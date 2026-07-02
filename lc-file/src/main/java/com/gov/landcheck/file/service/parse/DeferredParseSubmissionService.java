package com.gov.landcheck.file.service.parse;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.core.enums.FileStateEnum;
import com.gov.landcheck.file.config.FileProcessingConfig;
import com.gov.landcheck.file.config.FileProcessingProperties;
import com.gov.landcheck.file.dto.SubmitParseResult;
import com.gov.landcheck.file.processing.ProcessingConcurrencyGate;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * 上传后自动解析：延迟入队 + 单线程 drainer，避免与上传后处理/解析管道瞬时叠加。
 */
@Slf4j
@Service
public class DeferredParseSubmissionService {

    private final FileProcessingProperties properties;
    private final FileParseSubmissionService fileParseSubmissionService;
    private final MongoTemplate mongoTemplate;
    private final ProcessingConcurrencyGate parsePipelineGate;

    private BlockingQueue<Long> pendingQueue;
    private Thread drainerThread;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public DeferredParseSubmissionService(
            FileProcessingProperties properties,
            @Lazy FileParseSubmissionService fileParseSubmissionService,
            MongoTemplate mongoTemplate,
            @Qualifier(FileProcessingConfig.PARSE_PIPELINE_GATE) ProcessingConcurrencyGate parsePipelineGate) {
        this.properties = properties;
        this.fileParseSubmissionService = fileParseSubmissionService;
        this.mongoTemplate = mongoTemplate;
        this.parsePipelineGate = parsePipelineGate;
    }

    @PostConstruct
    void startDrainer() {
        int capacity = properties.getAutoParse().getMaxPending();
        pendingQueue = new LinkedBlockingQueue<>(capacity);
        drainerThread = new Thread(this::drainLoop, "deferred-parse-drainer");
        drainerThread.setDaemon(true);
        drainerThread.start();
    }

    @PreDestroy
    void stopDrainer() {
        running.set(false);
        if (drainerThread != null) {
            drainerThread.interrupt();
            try {
                drainerThread.join(5_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void enqueueAfterUpload(Long fileRecordId) {
        if (fileRecordId == null) {
            return;
        }
        FileProcessingProperties.AutoParse autoParse = properties.getAutoParse();
        if (!autoParse.isEnabled()) {
            return;
        }
        if (!pendingQueue.offer(fileRecordId)) {
            log.warn("延迟自动解析队列已满，保持 WAITING_PARSE: fileId={}, capacity={}",
                    fileRecordId, autoParse.getMaxPending());
        } else {
            log.debug("已加入延迟自动解析队列: fileId={}", fileRecordId);
        }
    }

    private void drainLoop() {
        FileProcessingProperties.AutoParse autoParse = properties.getAutoParse();
        while (running.get()) {
            try {
                Long fileRecordId = pendingQueue.poll(1, TimeUnit.SECONDS);
                if (fileRecordId == null) {
                    continue;
                }
                long deferMs = Math.max(0L, autoParse.getDeferMs());
                if (deferMs > 0) {
                    Thread.sleep(deferMs);
                }
                if (!running.get()) {
                    pendingQueue.offer(fileRecordId);
                    break;
                }
                if (!parsePipelineGate.hasAvailablePermit()) {
                    requeue(fileRecordId, autoParse.getRetryDelayMs());
                    continue;
                }
                FileRecord fileRecord = mongoTemplate.findById(fileRecordId, FileRecord.class);
                if (fileRecord == null) {
                    log.warn("延迟自动解析跳过：文件不存在 fileId={}", fileRecordId);
                    continue;
                }
                if (!isAutoParseContext(fileRecord.getFileContextType())) {
                    continue;
                }
                if (fileRecord.getFileState() != FileStateEnum.WAITING_PARSE) {
                    log.debug("延迟自动解析跳过：状态非 WAITING_PARSE fileId={}, state={}",
                            fileRecordId, fileRecord.getFileState());
                    continue;
                }
                SubmitParseResult result = fileParseSubmissionService.submitParseIfEligible(fileRecord);
                if (result.isSubmitted()) {
                    log.debug("延迟自动解析已提交: fileId={}, taskId={}", fileRecordId, result.getTaskId());
                } else if (shouldRetrySubmission(result)) {
                    log.debug("延迟自动解析暂缓重试: fileId={}, reason={}", fileRecordId, result.getErrorMessage());
                    requeue(fileRecordId, autoParse.getRetryDelayMs());
                } else {
                    log.warn("延迟自动解析跳过: fileId={}, reason={}", fileRecordId, result.getErrorMessage());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (!running.get()) {
                    break;
                }
            } catch (Exception e) {
                log.warn("延迟自动解析 drainer 异常: {}", e.getMessage(), e);
            }
        }
    }

    private void requeue(Long fileRecordId, long retryDelayMs) {
        try {
            if (retryDelayMs > 0) {
                Thread.sleep(retryDelayMs);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        if (!pendingQueue.offer(fileRecordId)) {
            log.warn("延迟自动解析重入队失败，保持 WAITING_PARSE: fileId={}", fileRecordId);
        }
    }

    private static boolean shouldRetrySubmission(SubmitParseResult result) {
        if (result == null || result.getErrorMessage() == null) {
            return false;
        }
        String message = result.getErrorMessage().toLowerCase();
        return message.contains("资源") || message.contains("稍后再试");
    }

    private static boolean isAutoParseContext(FileContextType contextType) {
        return contextType == FileContextType.CONTRACT
                || contextType == FileContextType.SURVEY_REPORT
                || contextType == FileContextType.PLANNING_REVIEW
                || contextType == FileContextType.CAPACITY_INDICATOR
                || contextType == FileContextType.PROJECT_PARTY_SURVEY_SUMMARY;
    }
}
