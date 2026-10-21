package com.gov.landcheck.file.service.parse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.ParseJob;
import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.file.task.base.TaskData;
import com.gov.landcheck.file.task.context.FileParseModulesFixture;

@ExtendWith(MockitoExtension.class)
class ParseArtifactCleanupRollbackOrderTest {

    private ParseArtifactCleanupService cleanupService;

    @BeforeEach
    void setUp() {
        cleanupService = spy(new ParseArtifactCleanupService());
        ReflectionTestUtils.setField(cleanupService, "fileParseModuleRegistry", FileParseModulesFixture.registry());
        ParseRollbackSummary empty = new ParseRollbackSummary();
        lenient().doReturn(empty).when(cleanupService).rollbackValidate(any());
        lenient().doReturn(empty).when(cleanupService).rollbackFill(any());
        lenient().doReturn(empty).when(cleanupService).rollbackParse(any());
        lenient().doReturn(empty).when(cleanupService).rollbackOcr(any());
        lenient().doReturn(empty).when(cleanupService).rollbackPreprocess(any());
        lenient().doNothing().when(cleanupService).clearIntermediateReferences(any(), any(), any(), any());
    }

    @Test
    void surveyRollsBackItsStagesInReverse() {
        TaskData taskData = task(FileContextType.SURVEY_REPORT, null);
        cleanupService.rollbackAllStages(taskData.getParseJob(), taskData.getFileRecord(), taskData);

        InOrder order = inOrder(cleanupService);
        order.verify(cleanupService).rollbackValidate(taskData);
        order.verify(cleanupService).rollbackFill(taskData);
        order.verify(cleanupService).rollbackParse(taskData);
        order.verify(cleanupService).rollbackOcr(taskData);
        order.verify(cleanupService).rollbackPreprocess(taskData);
    }

    @Test
    void projectPartySkipsOcrAndPreprocess() {
        TaskData taskData = task(FileContextType.PROJECT_PARTY_SURVEY_SUMMARY, null);
        cleanupService.rollbackAllStages(taskData.getParseJob(), taskData.getFileRecord(), taskData);

        InOrder order = inOrder(cleanupService);
        order.verify(cleanupService).rollbackFill(taskData);
        order.verify(cleanupService).rollbackParse(taskData);
        order.verify(cleanupService, never()).rollbackValidate(any());
        order.verify(cleanupService, never()).rollbackOcr(any());
        order.verify(cleanupService, never()).rollbackPreprocess(any());
    }

    @Test
    void capacitySkipsPreprocessAndValidate() {
        TaskData taskData = task(FileContextType.CAPACITY_INDICATOR, null);
        cleanupService.rollbackAllStages(taskData.getParseJob(), taskData.getFileRecord(), taskData);

        InOrder order = inOrder(cleanupService);
        order.verify(cleanupService).rollbackFill(taskData);
        order.verify(cleanupService).rollbackParse(taskData);
        order.verify(cleanupService).rollbackOcr(taskData);
        order.verify(cleanupService, never()).rollbackPreprocess(any());
        order.verify(cleanupService, never()).rollbackValidate(any());
    }

    @Test
    void missingFileTypeUsesJobType() {
        TaskData taskData = task(null, FileContextType.SURVEY_REPORT);
        cleanupService.rollbackAllStages(taskData.getParseJob(), taskData.getFileRecord(), taskData);

        InOrder order = inOrder(cleanupService);
        order.verify(cleanupService).rollbackValidate(taskData);
        order.verify(cleanupService).rollbackFill(taskData);
    }

    @Test
    void unregisteredTypeUsesDefaultStages() {
        TaskData taskData = task(FileContextType.OTHER, null);
        cleanupService.rollbackAllStages(taskData.getParseJob(), taskData.getFileRecord(), taskData);

        InOrder order = inOrder(cleanupService);
        order.verify(cleanupService).rollbackFill(taskData);
        order.verify(cleanupService).rollbackParse(taskData);
        order.verify(cleanupService).rollbackOcr(taskData);
        order.verify(cleanupService).rollbackPreprocess(taskData);
        order.verify(cleanupService, never()).rollbackValidate(any());
    }

    private static TaskData task(FileContextType fileType, FileContextType jobType) {
        FileRecord fileRecord = new FileRecord();
        fileRecord.setId(9L);
        fileRecord.setFileContextType(fileType);
        ParseJob parseJob = new ParseJob();
        parseJob.setId(3L);
        parseJob.setFileContextType(jobType);
        TaskData taskData = new TaskData(fileRecord);
        taskData.setParseJob(parseJob);
        return taskData;
    }
}
