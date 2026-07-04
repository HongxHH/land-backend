package com.gov.landcheck.file.service.parse;

import java.time.LocalDateTime;

import java.util.Set;

import java.util.concurrent.BlockingQueue;

import java.util.concurrent.ConcurrentHashMap;

import java.util.concurrent.LinkedBlockingQueue;

import java.util.concurrent.TimeUnit;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Qualifier;

import org.springframework.context.annotation.Lazy;

import org.springframework.data.mongodb.core.MongoTemplate;

import org.springframework.data.mongodb.core.query.Criteria;

import org.springframework.data.mongodb.core.query.Query;

import org.springframework.data.mongodb.core.query.Update;

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
 * 
 * 上传后自动解析：延迟入队 + 单线程 drainer，避免与上传后处理/解析管道瞬时叠加。
 * 
 */

@Slf4j

@Service

public class DeferredParseSubmissionService {

    private final FileProcessingProperties properties;

    private final FileParseSubmissionService fileParseSubmissionService;

    private final MongoTemplate mongoTemplate;

    private final ProcessingConcurrencyGate parsePipelineGate;

    private BlockingQueue<Long> pendingQueue;

    private final Set<Long> queuedInMemory = ConcurrentHashMap.newKeySet();

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

        if (!queuedInMemory.add(fileRecordId)) {

            log.debug("已在延迟自动解析队列中，跳过重复入队: fileId={}", fileRecordId);

            return;

        }

        if (!pendingQueue.offer(fileRecordId)) {

            queuedInMemory.remove(fileRecordId);

            log.warn("延迟自动解析队列已满，保持 WAITING_PARSE: fileId={}, capacity={}",

                    fileRecordId, autoParse.getMaxPending());

        } else {

            log.debug("已加入延迟自动解析队列: fileId={}", fileRecordId);

            markAutoParseQueued(fileRecordId);

        }

    }

    private void markAutoParseQueued(Long fileRecordId) {

        try {

            Query query = new Query(Criteria.where("_id").is(fileRecordId)

                    .and("file_state").is(FileStateEnum.WAITING_PARSE));

            Update update = new Update()

                    .set("auto_parse_queued_at", LocalDateTime.now())

                    .set("update_time", LocalDateTime.now());

            mongoTemplate.updateFirst(query, update, FileRecord.class);

        } catch (Exception e) {

            log.warn("标记自动解析入队时间失败: fileId={}, error={}", fileRecordId, e.getMessage());

        }

    }

    private void drainLoop() {

        FileProcessingProperties.AutoParse autoParse = properties.getAutoParse();

        while (running.get()) {

            Long fileRecordId = null;

            try {

                fileRecordId = pendingQueue.poll(1, TimeUnit.SECONDS);

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

                    queuedInMemory.remove(fileRecordId);

                    continue;

                }

                if (!FileContextType.isAutoParseContext(fileRecord.getFileContextType())) {

                    queuedInMemory.remove(fileRecordId);

                    continue;

                }

                if (fileRecord.getFileState() != FileStateEnum.WAITING_PARSE) {

                    log.debug("延迟自动解析跳过：状态非 WAITING_PARSE fileId={}, state={}",

                            fileRecordId, fileRecord.getFileState());

                    queuedInMemory.remove(fileRecordId);

                    continue;

                }

                SubmitParseResult result = fileParseSubmissionService.submitParseIfEligible(fileRecord);

                if (result.isSubmitted()) {

                    log.debug("延迟自动解析已提交: fileId={}, taskId={}", fileRecordId, result.getTaskId());

                    queuedInMemory.remove(fileRecordId);

                } else if (shouldRetrySubmission(result)) {

                    log.debug("延迟自动解析暂缓重试: fileId={}, reason={}", fileRecordId, result.getErrorMessage());

                    requeue(fileRecordId, autoParse.getRetryDelayMs());

                } else {

                    log.warn("延迟自动解析跳过: fileId={}, reason={}", fileRecordId, result.getErrorMessage());

                    queuedInMemory.remove(fileRecordId);

                }

            } catch (InterruptedException e) {

                Thread.currentThread().interrupt();

                if (!running.get()) {

                    break;

                }

            } catch (Exception e) {

                log.warn("延迟自动解析 drainer 异常: {}", e.getMessage(), e);

                if (fileRecordId != null) {

                    queuedInMemory.remove(fileRecordId);

                }

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

            queuedInMemory.remove(fileRecordId);

            return;

        }

        if (!pendingQueue.offer(fileRecordId)) {

            log.warn("延迟自动解析重入队失败，保持 WAITING_PARSE: fileId={}", fileRecordId);

            queuedInMemory.remove(fileRecordId);

        }

    }

    private static boolean shouldRetrySubmission(SubmitParseResult result) {

        if (result == null || result.getErrorMessage() == null) {

            return false;

        }

        String message = result.getErrorMessage().toLowerCase();

        return message.contains("资源") || message.contains("稍后再试");

    }

}
