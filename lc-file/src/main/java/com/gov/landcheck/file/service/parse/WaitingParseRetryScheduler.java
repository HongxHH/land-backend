package com.gov.landcheck.file.service.parse;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.core.enums.FileStateEnum;
import com.gov.landcheck.file.config.FileProcessingProperties;
import com.gov.landcheck.file.config.FileRetrySchedulerConfig;
import com.gov.landcheck.file.task.thread.TaskThreadPool;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * 线程池满导致自动解析提交失败后，周期性扫描 {@link FileStateEnum#WAITING_PARSE} 并重试提交。
 * <p>
 * 替代已移除的 drainer 内存队列，仅在线程池有空位时批量尝试，避免空转。
 */
@Slf4j
@Component
public class WaitingParseRetryScheduler {

    private final FileProcessingProperties properties;
    private final MongoTemplate mongoTemplate;
    private final AutoParseSubmissionService autoParseSubmissionService;
    private final TaskThreadPool taskThreadPool;
    private final ScheduledExecutorService parseRetryScheduler;
    private final AtomicBoolean tickRunning = new AtomicBoolean(false);

    public WaitingParseRetryScheduler(
            FileProcessingProperties properties,
            MongoTemplate mongoTemplate,
            AutoParseSubmissionService autoParseSubmissionService,
            TaskThreadPool taskThreadPool,
            @Qualifier(FileRetrySchedulerConfig.PARSE_RETRY_SCHEDULER) ScheduledExecutorService parseRetryScheduler) {
        this.properties = properties;
        this.mongoTemplate = mongoTemplate;
        this.autoParseSubmissionService = autoParseSubmissionService;
        this.taskThreadPool = taskThreadPool;
        this.parseRetryScheduler = parseRetryScheduler;
    }

    @PostConstruct
    void scheduleRetryLoop() {
        if (!properties.getAutoParse().isEnabled()) {
            return;
        }
        long intervalMs = properties.getAutoParse().getRetryIntervalMs();
        parseRetryScheduler.scheduleWithFixedDelay(
                this::retryWaitingParseFiles,
                intervalMs,
                intervalMs,
                TimeUnit.MILLISECONDS);
        log.info("WAITING_PARSE 自动重试调度已启动: intervalMs={}, batchSize={}",
                intervalMs, properties.getAutoParse().getRetryBatchSize());
    }

    @PreDestroy
    void logShutdown() {
        log.debug("WAITING_PARSE 自动重试调度随应用关闭");
    }

    void retryWaitingParseFiles() {
        if (!properties.getAutoParse().isEnabled()) {
            return;
        }
        if (!taskThreadPool.hasSubmissionCapacity()) {
            return;
        }
        if (!tickRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            int batchSize = properties.getAutoParse().getRetryBatchSize();
            Query query = new Query(Criteria.where("file_state").is(FileStateEnum.WAITING_PARSE)
                    .and("auto_parse_suppressed").ne(true))
                    .with(Sort.by(Sort.Direction.ASC, "update_time"))
                    .limit(batchSize);
            List<FileRecord> waitingFiles = mongoTemplate.find(query, FileRecord.class);
            if (waitingFiles.isEmpty()) {
                return;
            }
            int submitted = 0;
            for (FileRecord fileRecord : waitingFiles) {
                if (!taskThreadPool.hasSubmissionCapacity()) {
                    break;
                }
                if (fileRecord.getId() == null
                        || !FileContextType.isAutoParseContext(fileRecord.getFileContextType())) {
                    continue;
                }
                if (autoParseSubmissionService.submitWaitingFile(fileRecord)) {
                    submitted++;
                }
            }
            if (submitted > 0) {
                log.debug("WAITING_PARSE 重试提交: 成功 {} / 扫描 {}", submitted, waitingFiles.size());
            }
        } catch (Exception e) {
            log.warn("WAITING_PARSE 自动重试失败: {}", e.getMessage(), e);
        } finally {
            tickRunning.set(false);
        }
    }
}
