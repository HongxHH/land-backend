package com.gov.landcheck.file.task.thread;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.ParseJob;
import com.gov.landcheck.core.config.global.ApplicationContextProvider;
import com.gov.landcheck.file.service.RetryDecisionService;
import com.gov.landcheck.file.task.base.TaskData;
import com.gov.landcheck.file.task.executor.ParseFileExecutor;

class ParseFileTaskTest {

    @Test
    void fallbackSkipsRollbackAfterRetryWasTriggered() {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBean(RetryDecisionService.class))
                .thenReturn(mock(RetryDecisionService.class));
        new ApplicationContextProvider().setApplicationContext(applicationContext);

        FileRecord fileRecord = new FileRecord();
        fileRecord.setId(10L);
        ParseJob parseJob = new ParseJob();
        parseJob.setId(20L);

        TaskData taskData = new TaskData();
        taskData.setFileRecord(fileRecord);
        taskData.setParseJob(parseJob);

        ParseFileExecutor executor = mock(ParseFileExecutor.class);
        ParseFileTask task = new ParseFileTask("task-1", taskData, executor);
        ReflectionTestUtils.setField(task, "retryTriggered", true);

        task.fallback();

        verify(executor, never()).rollback(taskData);
    }
}
