package com.gov.landcheck.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.ParseJob;
import com.gov.landcheck.core.config.global.RetryConfig;
import com.gov.landcheck.core.enums.FileStateEnum;
import com.gov.landcheck.core.enums.ParseJobStateEnum;

class RetryDecisionServiceTest {

    @Test
    void executeRetryMarksFileFailedWhenImmediateRetrySubmitFails() {
        RetryConfig retryConfig = new RetryConfig();
        ITaskExecuteService taskExecuteService = mock(ITaskExecuteService.class);
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        RetryDecisionService service = new RetryDecisionService(
                retryConfig, taskExecuteService, mongoTemplate, scheduler);

        ParseJob parseJob = new ParseJob();
        parseJob.setId(20L);
        parseJob.setFileRecordId(10L);
        parseJob.setJobStatus(ParseJobStateEnum.RUNNING);

        FileRecord fileRecord = new FileRecord();
        fileRecord.setId(10L);
        fileRecord.setFileState(FileStateEnum.PARSING);

        when(taskExecuteService.retryParseTask(parseJob, fileRecord))
                .thenThrow(new RejectedExecutionException("pool full"));

        service.executeRetry(parseJob, fileRecord, 0, 1, "resource retry");

        assertThat(parseJob.getRetryStatus()).isEqualTo("SUBMIT_FAILED");
        assertThat(parseJob.getJobStatus()).isEqualTo(ParseJobStateEnum.FAILED);
        assertThat(parseJob.getRetryReason()).contains("retry_immediate_failed");
        assertThat(parseJob.getErrorMessage()).isEqualTo(parseJob.getRetryReason());
        assertThat(parseJob.getNextRetryAt()).isNull();
        assertThat(fileRecord.getFileState()).isEqualTo(FileStateEnum.PARSE_FAIL);
        assertThat(fileRecord.getParseMessage()).isEqualTo(parseJob.getRetryReason());
        assertThat(fileRecord.getParseJobId()).isEqualTo(20L);

        verify(mongoTemplate, atLeastOnce()).save(parseJob);
        verify(mongoTemplate).save(fileRecord);
    }

    @Test
    void executeRetryMarksFileFailedWhenDelayedRetryCannotBeScheduled() {
        RetryConfig retryConfig = new RetryConfig();
        ITaskExecuteService taskExecuteService = mock(ITaskExecuteService.class);
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        RetryDecisionService service = new RetryDecisionService(
                retryConfig, taskExecuteService, mongoTemplate, scheduler);

        ParseJob parseJob = new ParseJob();
        parseJob.setId(21L);
        parseJob.setFileRecordId(11L);
        parseJob.setJobStatus(ParseJobStateEnum.RUNNING);

        FileRecord fileRecord = new FileRecord();
        fileRecord.setId(11L);
        fileRecord.setFileState(FileStateEnum.PARSING);

        when(scheduler.schedule(any(Runnable.class), anyLong(), any()))
                .thenThrow(new RejectedExecutionException("scheduler closed"));

        service.executeRetry(parseJob, fileRecord, 1000L, 1, "resource retry");

        assertThat(parseJob.getRetryStatus()).isEqualTo("SUBMIT_FAILED");
        assertThat(parseJob.getJobStatus()).isEqualTo(ParseJobStateEnum.FAILED);
        assertThat(parseJob.getRetryReason()).contains("retry_schedule_failed");
        assertThat(fileRecord.getFileState()).isEqualTo(FileStateEnum.PARSE_FAIL);
        assertThat(fileRecord.getParseMessage()).isEqualTo(parseJob.getRetryReason());
        assertThat(fileRecord.getParseJobId()).isEqualTo(21L);
    }
}
