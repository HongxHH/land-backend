package com.gov.landcheck.file.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.concurrent.ScheduledExecutorService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.gov.landcheck.core.bo.entity.ParseJob;
import com.gov.landcheck.core.config.global.RetryConfig;
import com.gov.landcheck.file.task.base.TaskException;

class RetryDecisionServiceTest {

    private RetryDecisionService retryDecisionService;

    @BeforeEach
    void setUp() {
        retryDecisionService = new RetryDecisionService(
                new RetryConfig(),
                mock(ITaskExecuteService.class),
                mock(MongoTemplate.class),
                mock(ScheduledExecutorService.class));
    }

    @Test
    void parseDataInvalid_shouldNotRetry() {
        ParseJob parseJob = new ParseJob();
        parseJob.setAttemptCount(0);
        TaskException ex = new TaskException(
                TaskException.ErrorCode.PARSE_DATA_INVALID,
                "PARSE",
                764L,
                917L,
                "数据解析失败: 实测报告未解析到户室面积对照表明细",
                new IllegalStateException("实测报告未解析到户室面积对照表明细"));

        RetryDecisionService.RetryDecision decision = retryDecisionService.shouldRetry(parseJob, ex);

        assertFalse(decision.isShouldRetry());
    }

    @Test
    void parseFailed_shouldNotRetry() {
        ParseJob parseJob = new ParseJob();
        parseJob.setAttemptCount(0);
        TaskException ex = new TaskException(
                TaskException.ErrorCode.PARSE_FAILED,
                "PARSE",
                764L,
                917L,
                "数据解析失败: 未知错误",
                null);

        RetryDecisionService.RetryDecision decision = retryDecisionService.shouldRetry(parseJob, ex);

        assertFalse(decision.isShouldRetry());
    }

    @Test
    void parseTimeout_shouldRetry() {
        ParseJob parseJob = new ParseJob();
        parseJob.setAttemptCount(0);
        TaskException ex = new TaskException(
                TaskException.ErrorCode.PARSE_TIMEOUT,
                "PARSE",
                764L,
                917L,
                "数据解析超时",
                null);

        RetryDecisionService.RetryDecision decision = retryDecisionService.shouldRetry(parseJob, ex);

        assertTrue(decision.isShouldRetry());
    }

    @Test
    void exhaustedAttempts_shouldNotRetry() {
        ParseJob parseJob = new ParseJob();
        parseJob.setAttemptCount(2);
        TaskException ex = new TaskException(
                TaskException.ErrorCode.PARSE_TIMEOUT,
                "PARSE",
                764L,
                917L,
                "数据解析超时",
                null);

        RetryDecisionService.RetryDecision decision = retryDecisionService.shouldRetry(parseJob, ex);

        assertFalse(decision.isShouldRetry());
    }
}
