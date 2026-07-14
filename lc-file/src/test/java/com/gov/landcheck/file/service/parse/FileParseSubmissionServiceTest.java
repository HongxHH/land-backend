package com.gov.landcheck.file.service.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.util.ReflectionTestUtils;

import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.core.enums.FileStateEnum;
import com.gov.landcheck.core.enums.FileType;
import com.gov.landcheck.file.dto.SubmitParseResult;
import com.gov.landcheck.file.service.ITaskExecuteService;
import com.gov.landcheck.file.utils.GridFSUtils;
import com.mongodb.client.result.UpdateResult;

@ExtendWith(MockitoExtension.class)
class FileParseSubmissionServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private GridFSUtils gridFSUtils;
    @Mock
    private ITaskExecuteService taskExecuteService;
    @Mock
    private ParseArtifactCleanupService parseArtifactCleanupService;

    private FileParseSubmissionService service;

    @BeforeEach
    void setUp() {
        service = new FileParseSubmissionService();
        ReflectionTestUtils.setField(service, "mongoTemplate", mongoTemplate);
        ReflectionTestUtils.setField(service, "gridFSUtils", gridFSUtils);
        ReflectionTestUtils.setField(service, "taskExecuteService", taskExecuteService);
        ReflectionTestUtils.setField(service, "parseArtifactCleanupService", parseArtifactCleanupService);
    }

    @Test
    void submitParseIfEligibleDoesNotResetBusinessStateWhenReservationIsLost() {
        FileRecord fileRecord = surveyReport(FileStateEnum.PARSE_COMPLETE);
        when(gridFSUtils.exists("gridfs-1")).thenReturn(true);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(FileRecord.class)))
                .thenReturn(UpdateResult.acknowledged(1L, 0L, null));

        SubmitParseResult result = service.submitParseIfEligible(fileRecord);

        assertFalse(result.isSubmitted());
        assertEquals(FileStateEnum.PARSE_COMPLETE, fileRecord.getFileState());
        verify(parseArtifactCleanupService, never()).resetBusinessStateBeforeParse(any(FileRecord.class));
        verify(taskExecuteService, never()).executeParseTask(any(FileRecord.class), any(FileStateEnum.class));
    }

    @Test
    void submitParseIfEligibleResetsBusinessStateOnlyAfterReservationSucceeds() {
        FileRecord fileRecord = surveyReport(FileStateEnum.PARSE_COMPLETE);
        when(gridFSUtils.exists("gridfs-1")).thenReturn(true);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(FileRecord.class)))
                .thenReturn(UpdateResult.acknowledged(1L, 1L, null));
        when(taskExecuteService.executeParseTask(fileRecord, FileStateEnum.PARSE_COMPLETE)).thenReturn("task-1");

        SubmitParseResult result = service.submitParseIfEligible(fileRecord);

        assertTrue(result.isSubmitted());
        assertEquals("task-1", result.getTaskId());
        InOrder inOrder = inOrder(mongoTemplate, parseArtifactCleanupService, taskExecuteService);
        inOrder.verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class), eq(FileRecord.class));
        inOrder.verify(parseArtifactCleanupService).resetBusinessStateBeforeParse(fileRecord);
        inOrder.verify(taskExecuteService).executeParseTask(fileRecord, FileStateEnum.PARSE_COMPLETE);
    }

    private static FileRecord surveyReport(FileStateEnum fileState) {
        FileRecord fileRecord = new FileRecord();
        fileRecord.setId(100L);
        fileRecord.setFileContextType(FileContextType.SURVEY_REPORT);
        fileRecord.setFileType(FileType.PDF);
        fileRecord.setFileState(fileState);
        fileRecord.setGridfsId("gridfs-1");
        return fileRecord;
    }
}
