package com.gov.landcheck.file.service;

import java.time.LocalDateTime;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.ParseJob;
import com.gov.landcheck.core.config.global.RetryConfig;
import com.gov.landcheck.core.config.logging.ContextPropagating;
import com.gov.landcheck.file.config.FileRetrySchedulerConfig;
import com.gov.landcheck.file.task.base.TaskFailureClassifier;

import lombok.extern.slf4j.Slf4j;

/**
 * 重试决策服务
 */
@Slf4j
@Service
public class RetryDecisionService {

    private static final String RETRY_SUBMIT_FAILED = "SUBMIT_FAILED";

    private final RetryConfig retryConfig;
    private final ITaskExecuteService taskExecuteService;
    private final MongoTemplate mongoTemplate;
    private final ScheduledExecutorService parseRetryScheduler;

    public RetryDecisionService(
            RetryConfig retryConfig,
            ITaskExecuteService taskExecuteService,
            MongoTemplate mongoTemplate,
            @Qualifier(FileRetrySchedulerConfig.PARSE_RETRY_SCHEDULER) ScheduledExecutorService parseRetryScheduler) {
        this.retryConfig = retryConfig;
        this.taskExecuteService = taskExecuteService;
        this.mongoTemplate = mongoTemplate;
        this.parseRetryScheduler = parseRetryScheduler;
    }

    /**
     * 判断是否应该重试
     */
    public RetryDecision shouldRetry(ParseJob parseJob, Exception exception) {
        if (parseJob == null) {
            logNoRetryDecision(null, "parseJob为空");
            return RetryDecision.noRetry("parseJob为空");
        }
        if (parseJob.getId() != null) {
            ParseJob latest = mongoTemplate.findById(parseJob.getId(), ParseJob.class);
            if (latest != null) {
                parseJob = latest;
            }
        }
        if (parseJob.isCancelRequested()) {
            logNoRetryDecision(parseJob, "任务已取消");
            return RetryDecision.noRetry("任务已取消");
        }
        if (exception == null) {
            logNoRetryDecision(parseJob, "exception为空");
            return RetryDecision.noRetry("exception为空");
        }
        int currentAttempts = parseJob.getAttemptCount() != null ? parseJob.getAttemptCount() : 0;

        if (currentAttempts >= retryConfig.getMaxAttempts()) {
            logNoRetryDecision(parseJob, "超过最大重试次数: " + currentAttempts);
            return RetryDecision.noRetry("超过最大重试次数: " + currentAttempts);
        }

        String errorType = TaskFailureClassifier.toRetryErrorType(exception);
        if (!retryConfig.isRetryable(errorType)) {
            logNoRetryDecision(parseJob, "错误类型不支持重试: " + errorType);
            return RetryDecision.noRetry("错误类型不支持重试: " + errorType);
        }

        long delayMs = Math.max(1000L, retryConfig.calculateDelay(currentAttempts + 1));
        RetryDecision decision = RetryDecision.retry(delayMs, "错误类型: " + errorType + ", 延迟: " + delayMs + "ms");
        log.info("重试决策: decision=retry fileId={} parseJobId={} attempt={}/{} errorType={} delayMs={} reason={}",
                parseJob.getFileRecordId(), parseJob.getId(), currentAttempts, retryConfig.getMaxAttempts(),
                errorType, delayMs, decision.getReason());
        return decision;
    }

    private void logNoRetryDecision(ParseJob parseJob, String reason) {
        int currentAttempts = parseJob != null && parseJob.getAttemptCount() != null ? parseJob.getAttemptCount() : 0;
        log.info("重试决策: decision=noRetry fileId={} parseJobId={} attempt={}/{} reason={}",
                parseJob != null ? parseJob.getFileRecordId() : null,
                parseJob != null ? parseJob.getId() : null,
                currentAttempts, retryConfig.getMaxAttempts(), reason);
    }

    /**
     * 执行重试：更新重试计数并重新提交任务
     */
    public void executeRetry(ParseJob parseJob, FileRecord fileRecord, long delayMs, int newAttemptCount,
            String reason) {
        // 更新重试计数
        parseJob.setAttemptCount(newAttemptCount);
        parseJob.setRetryReason(reason);
        parseJob.setRetryStatus(delayMs > 0 ? "SCHEDULED" : "SUBMITTED");
        parseJob.setNextRetryAt(delayMs > 0 ? LocalDateTime.now().plusNanos(delayMs * 1_000_000L) : null);
        mongoTemplate.save(parseJob);

        log.info("准备执行重试: fileId={}, attempt={}, delay={}ms, reason={}",
                fileRecord.getId(), newAttemptCount, delayMs, reason);

        if (delayMs <= 0) {
            // 立即重试
            executeRetryImmediately(parseJob, fileRecord, newAttemptCount);
        } else {
            // 延迟重试
            executeRetryWithDelay(parseJob, fileRecord, delayMs, newAttemptCount);
        }
    }

    /**
     * 立即执行重试
     */
    private void executeRetryImmediately(ParseJob existingParseJob, FileRecord fileRecord, int attemptCount) {
        try {
            String newTaskId = taskExecuteService.retryParseTask(existingParseJob, fileRecord);
            existingParseJob.setRetryStatus("SUBMITTED");
            existingParseJob.setNextRetryAt(null);
            mongoTemplate.save(existingParseJob);
            log.info("立即重试任务已提交: fileId={}, parseJobId={}, newTaskId={}, attempt={}",
                    fileRecord.getId(), existingParseJob.getId(), newTaskId, attemptCount);
        } catch (Exception e) {
            log.error("立即重试任务提交失败: fileId={}, parseJobId={}, attempt={}, error={}",
                    fileRecord.getId(), existingParseJob.getId(), attemptCount, e.getMessage(), e);
            persistRetrySubmitFailure(existingParseJob, fileRecord, attemptCount, "immediate", e);
        }
    }

    private void persistRetrySubmitFailure(ParseJob parseJob, FileRecord fileRecord, int attemptCount, String stage,
            Exception e) {
        parseJob.setRetryStatus(RETRY_SUBMIT_FAILED);
        String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        String reason = "retry_" + stage + "_failed: " + truncate(detail, 800);
        parseJob.setRetryReason(reason);
        parseJob.setNextRetryAt(null);
        try {
            mongoTemplate.save(parseJob);
        } catch (Exception ex) {
            log.error("回写重试失败状态落库异常: parseJobId={}, fileId={}, error={}",
                    parseJob.getId(), fileRecord != null ? fileRecord.getId() : null, ex.getMessage(), ex);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    /**
     * 延迟执行重试
     */
    private void executeRetryWithDelay(ParseJob existingParseJob, FileRecord fileRecord, long delayMs,
            int attemptCount) {
        try {
            parseRetryScheduler
                    .schedule(() -> ContextPropagating.runWithTraceId(existingParseJob.getRequestId(), () -> {
                        try {
                            ParseJob latest = mongoTemplate.findById(existingParseJob.getId(), ParseJob.class);
                            if (latest == null || latest.isCancelRequested()) {
                                log.info("延迟重试已跳过（任务已取消）: fileId={}, parseJobId={}",
                                        fileRecord.getId(), existingParseJob.getId());
                                return;
                            }
                            log.debug("延迟重试执行: fileId={}, parseJobId={}, delay={}ms",
                                    fileRecord.getId(), existingParseJob.getId(), delayMs);
                            String newTaskId = taskExecuteService.retryParseTask(existingParseJob, fileRecord);
                            existingParseJob.setRetryStatus("SUBMITTED");
                            existingParseJob.setNextRetryAt(null);
                            mongoTemplate.save(existingParseJob);
                            log.info("延迟重试任务已提交: fileId={}, parseJobId={}, newTaskId={}, attempt={}",
                                    fileRecord.getId(), existingParseJob.getId(), newTaskId, attemptCount);
                        } catch (Exception e) {
                            log.error("延迟重试任务提交失败: fileId={}, parseJobId={}, attempt={}, error={}",
                                    fileRecord.getId(), existingParseJob.getId(), attemptCount, e.getMessage(), e);
                            persistRetrySubmitFailure(existingParseJob, fileRecord, attemptCount, "delayed", e);
                        }
                    }), delayMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("延迟重试未能入调度队列: fileId={}, parseJobId={}, error={}",
                    fileRecord.getId(), existingParseJob.getId(), e.getMessage(), e);
            persistRetrySubmitFailure(existingParseJob, fileRecord, attemptCount, "schedule", e);
        }
    }

    /**
     * 重试决策结果
     */
    public static class RetryDecision {
        private final boolean shouldRetry;
        private final long delayMs;
        private final String reason;

        private RetryDecision(boolean shouldRetry, long delayMs, String reason) {
            this.shouldRetry = shouldRetry;
            this.delayMs = delayMs;
            this.reason = reason;
        }

        public static RetryDecision retry(long delayMs, String reason) {
            return new RetryDecision(true, delayMs, reason);
        }

        public static RetryDecision noRetry(String reason) {
            return new RetryDecision(false, 0, reason);
        }

        public boolean isShouldRetry() {
            return shouldRetry;
        }

        public long getDelayMs() {
            return delayMs;
        }

        public String getReason() {
            return reason;
        }
    }
}