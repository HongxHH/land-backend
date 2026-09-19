package com.gov.landcheck.file.service.parse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.util.ReflectionTestUtils;

import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.ParseJob;
import com.gov.landcheck.core.bo.entity.RoomInfo;
import com.gov.landcheck.core.bo.entity.SurveyReportInfo;
import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.file.task.base.TaskData;
import com.mongodb.client.result.DeleteResult;

@ExtendWith(MockitoExtension.class)
class ParseArtifactCleanupServiceRevertTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private ParseFillSnapshotService parseFillSnapshotService;

    @InjectMocks
    private ParseArtifactCleanupService cleanupService;

    @BeforeEach
    void injectSnapshotService() {
        ReflectionTestUtils.setField(cleanupService, "parseFillSnapshotService", parseFillSnapshotService);
    }

    @Test
    void revertFillWithoutSnapshotDoesNotDeleteRooms() {
        FileRecord fileRecord = surveyFile(9L, 1L);
        TaskData taskData = new TaskData(fileRecord);

        cleanupService.revertFillBusinessState(fileRecord, taskData);

        verify(mongoTemplate, never()).remove(any(Query.class), eq(RoomInfo.class));
        verify(mongoTemplate, never()).remove(any(Query.class), eq(SurveyReportInfo.class));
        verify(mongoTemplate, never()).save(any(SurveyReportInfo.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void revertFillRestoresSurveyAndRoomsFromMemorySnapshot() {
        FileRecord fileRecord = surveyFile(9L, 1L);
        TaskData taskData = new TaskData(fileRecord);
        SurveyReportInfo snapshot = new SurveyReportInfo();
        snapshot.setId(77L);
        snapshot.setProjectId(1L);
        snapshot.setFileRecordId(9L);
        snapshot.setBuildingName("旧楼");
        taskData.setPreFillSurveySnapshot(snapshot);

        RoomInfo room = new RoomInfo();
        room.setId(501L);
        room.setFileRecordId(9L);
        room.setRoomNumber("101");
        taskData.setPreFillRoomsSnapshot(List.of(room));

        when(mongoTemplate.remove(any(Query.class), eq(RoomInfo.class))).thenReturn(DeleteResult.acknowledged(1));

        cleanupService.revertFillBusinessState(fileRecord, taskData);

        verify(mongoTemplate).save(snapshot);
        verify(mongoTemplate).remove(any(Query.class), eq(RoomInfo.class));
        ArgumentCaptor<List<RoomInfo>> roomsCaptor = ArgumentCaptor.forClass(List.class);
        verify(mongoTemplate).insertAll(roomsCaptor.capture());
        assertEquals(1, roomsCaptor.getValue().size());
        RoomInfo inserted = roomsCaptor.getValue().get(0);
        assertEquals(501L, inserted.getId());
        assertEquals("101", inserted.getRoomNumber());
    }

    @Test
    void revertFillDeletesOnlyWhenThisAttemptCreatedSurvey() {
        FileRecord fileRecord = surveyFile(9L, 1L);
        TaskData taskData = new TaskData(fileRecord);
        taskData.setFillCreatedNewSurvey(true);
        when(mongoTemplate.find(any(Query.class), eq(SurveyReportInfo.class))).thenReturn(List.of());
        when(mongoTemplate.remove(any(Query.class), eq(RoomInfo.class))).thenReturn(DeleteResult.acknowledged(0));
        when(mongoTemplate.remove(any(Query.class), eq(SurveyReportInfo.class)))
                .thenReturn(DeleteResult.acknowledged(0));

        cleanupService.revertFillBusinessState(fileRecord, taskData);

        verify(mongoTemplate).remove(any(Query.class), eq(RoomInfo.class));
        verify(mongoTemplate).remove(any(Query.class), eq(SurveyReportInfo.class));
    }

    @Test
    void rollbackFillSkipsWhenFillStageNotStarted() {
        FileRecord fileRecord = surveyFile(9L, 1L);
        ParseJob parseJob = new ParseJob();
        parseJob.setId(3L);
        parseJob.setFillStatus("PENDING");
        TaskData taskData = new TaskData(fileRecord);
        taskData.setParseJob(parseJob);

        ParseRollbackSummary summary = cleanupService.rollbackFill(taskData);

        assertFalse(summary.hasFailures());
        verify(parseFillSnapshotService, never()).restoreIfPresent(any());
        verify(mongoTemplate, never()).remove(any(Query.class), eq(RoomInfo.class));
    }

    @Test
    void rollbackFillPrefersPersistedSnapshot() {
        FileRecord fileRecord = surveyFile(9L, 1L);
        ParseJob parseJob = new ParseJob();
        parseJob.setId(3L);
        parseJob.setFillStatus("RUNNING");
        TaskData taskData = new TaskData(fileRecord);
        taskData.setParseJob(parseJob);
        when(parseFillSnapshotService.restoreIfPresent(taskData)).thenReturn(true);

        ParseRollbackSummary summary = cleanupService.rollbackFill(taskData);

        assertFalse(summary.hasFailures());
        assertTrue(summary.getSuccessesView().stream().anyMatch(s -> s.contains("落盘快照")));
        verify(parseFillSnapshotService).restoreIfPresent(taskData);
        verify(mongoTemplate, never()).remove(any(Query.class), eq(RoomInfo.class));
    }

    private static FileRecord surveyFile(Long fileId, Long projectId) {
        FileRecord fileRecord = new FileRecord();
        fileRecord.setId(fileId);
        fileRecord.setProjectId(projectId);
        fileRecord.setFileContextType(FileContextType.SURVEY_REPORT);
        return fileRecord;
    }
}
