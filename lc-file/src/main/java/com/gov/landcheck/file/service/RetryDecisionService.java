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
import com.gov.landcheck.file.config.FileRetrySchedulerConfig;
import com.gov.landcheck.file.task.base.TaskException;

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
            return RetryDecision.noRetry("parseJob为空");
        }
        if (parseJob.isCancelRequested()) {
            return RetryDecision.noRetry("任务已取消");
        }
        if (exception == null) {
            return RetryDecision.noRetry("exception为空");
        }
        int currentAttempts = parseJob.getAttemptCount() != null ? parseJob.getAttemptCount() : 0;

        // 检查重试次数限制
        if (currentAttempts >= retryConfig.getMaxAttempts()) {
            return RetryDecision.noRetry("超过最大重试次数: " + currentAttempts);
        }

        // 分析异常类型
        String errorType = analyzeErrorType(exception);
        if (!retryConfig.isRetryable(errorType)) {
            return RetryDecision.noRetry("错误类型不支持重试: " + errorType);
        }

        // 计算下次重试延迟
        long delayMs = retryConfig.calculateDelay(currentAttempts + 1);

        return RetryDecision.retry(delayMs, "错误类型: " + errorType + ", 延迟: " + delayMs + "ms");
    }

    /**
     * 分析异常类型
     */
    private String analyzeErrorType(Exception exception) {
        String message = exception.getMessage();
        if (message == null)
            message = exception.getClass().getSimpleName();

        String lowerMessage = message.toLowerCase();

        // 超时相关
        if (lowerMessage.contains("timeout") || lowerMessage.contains("time out")) {
            return "timeout";
        }

        // 连接相关
        if (lowerMessage.contains("connection") || lowerMessage.contains("connect")) {
            return "connection";
        }

        // 网络相关
        if (lowerMessage.contains("network") || lowerMessage.contains("socket")) {
            return "network";
        }

        // 数据库相关
        if (lowerMessage.contains("database") || lowerMessage.contains("mongo")) {
            return "database";
        }

        // 任务异常
        if (exception instanceof TaskException taskException) {
            TaskException.ErrorCode errorCode = taskException.getErrorCode();
            switch (errorCode) {
                // 预处理阶段错误
                case PREPROCESS_FAILED:
                case PREPROCESS_TIMEOUT:
                case PREPROCESS_RESOURCE_ERROR:
                    return "timeout";

                // OCR阶段错误
                case OCR_FAILED:
                case OCR_TIMEOUT:
                case OCR_API_ERROR:
                    return "network";
                case OCR_INVALID_INPUT:
                    return "parse"; // 输入数据问题，不可重试

                // 数据解析阶段错误
                case PARSE_FAILED:
                case PARSE_TIMEOUT:
                    return "timeout";
                case PARSE_DATA_INVALID:
                    return "parse"; // 数据格式问题，不可重试

                // 数据回填阶段错误
                case FILL_FAILED:
                case FILL_TIMEOUT:
                    return "database"; // 数据库操作相关

                // 数据校验阶段错误
                case VALIDATE_FAILED:
                case VALIDATE_TIMEOUT:
                    return "database"; // 涉及数据库查询

                // 系统级别错误
                case DATABASE_ERROR:
                    return "database";
                case NETWORK_ERROR:
                    return "network";
                case IO_ERROR:
                    return "io"; // IO错误有时是临时性的

                // 任务管理错误
                case TASK_TIMEOUT:
                    return "timeout";
                case TASK_RESOURCE_ERROR:
                    return "timeout"; // 资源不足通常是临时性的

                // 不支持重试的错误
                case TASK_CANCELLED:
                case TASK_STATE_INVALID:
                case SYSTEM_ERROR:
                default:
                    return "unknown";
            }
        }

        // 默认不可重试
        return "unknown";
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
            parseRetryScheduler.schedule(() -> {
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
            }, delayMs, TimeUnit.MILLISECONDS);
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