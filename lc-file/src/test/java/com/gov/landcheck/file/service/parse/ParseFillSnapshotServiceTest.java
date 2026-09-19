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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.ParseFillSnapshot;
import com.gov.landcheck.core.bo.entity.ParseJob;
import com.gov.landcheck.core.bo.entity.RoomInfo;
import com.gov.landcheck.core.bo.entity.SurveyReportInfo;
import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.file.task.base.TaskData;
import com.mongodb.client.result.DeleteResult;

@ExtendWith(MockitoExtension.class)
class ParseFillSnapshotServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private PlatformTransactionManager transactionManager;

    @InjectMocks
    private ParseFillSnapshotService snapshotService;

    @BeforeEach
    void stubTransaction() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    @Test
    void captureIfAbsentDoesNotOverwriteExistingSnapshot() {
        TaskData taskData = surveyTask();
        ParseFillSnapshot existing = new ParseFillSnapshot();
        existing.setParseJobId(3L);
        existing.setFileRecordId(9L);
        existing.setFileContextType(FileContextType.SURVEY_REPORT);
        existing.setHadExisting(true);
        SurveyReportInfo survey = new SurveyReportInfo();
        survey.setId(77L);
        survey.setBuildingName("旧楼");
        existing.setSurveyReportSnapshot(survey);
        when(mongoTemplate.findOne(any(Query.class), eq(ParseFillSnapshot.class))).thenReturn(existing);

        ParseFillSnapshot result = snapshotService.captureIfAbsent(taskData);

        assertEquals(existing, result);
        verify(mongoTemplate, never()).insert(any(ParseFillSnapshot.class));
        assertEquals("旧楼", taskData.getPreFillSurveySnapshot().getBuildingName());
        assertFalse(taskData.isFillCreatedNewSurvey());
    }

    @Test
    void restoreIfPresentReturnsFalseWhenMissing() {
        TaskData taskData = surveyTask();
        when(mongoTemplate.findOne(any(Query.class), eq(ParseFillSnapshot.class))).thenReturn(null);

        assertFalse(snapshotService.restoreIfPresent(taskData));
        verify(mongoTemplate, never()).save(any(SurveyReportInfo.class));
        verify(mongoTemplate, never()).remove(any(Query.class), eq(RoomInfo.class));
    }

    @Test
    void restoreIfPresentRebuildsRoomsThenDeletesSnapshot() {
        TaskData taskData = surveyTask();
        ParseFillSnapshot snapshot = new ParseFillSnapshot();
        snapshot.setParseJobId(3L);
        snapshot.setFileRecordId(9L);
        snapshot.setFileContextType(FileContextType.SURVEY_REPORT);
        snapshot.setHadExisting(true);
        SurveyReportInfo survey = new SurveyReportInfo();
        survey.setId(77L);
        survey.setFileRecordId(9L);
        snapshot.setSurveyReportSnapshot(survey);
        RoomInfo room = new RoomInfo();
        room.setId(501L);
        room.setRoomNumber("101");
        snapshot.setRoomsSnapshot(List.of(room));
        when(mongoTemplate.findOne(any(Query.class), eq(ParseFillSnapshot.class))).thenReturn(snapshot);
        when(mongoTemplate.remove(any(Query.class), eq(RoomInfo.class))).thenReturn(DeleteResult.acknowledged(2));
        when(mongoTemplate.remove(any(Query.class), eq(ParseFillSnapshot.class)))
                .thenReturn(DeleteResult.acknowledged(1));

        assertTrue(snapshotService.restoreIfPresent(taskData));

        verify(mongoTemplate).save(any(SurveyReportInfo.class));
        verify(mongoTemplate).remove(any(Query.class), eq(RoomInfo.class));
        verify(mongoTemplate).insertAll(any());
        verify(mongoTemplate).remove(any(Query.class), eq(ParseFillSnapshot.class));
    }

    @Test
    void captureIfAbsentPersistsSurveyAndRooms() {
        TaskData taskData = surveyTask();
        when(mongoTemplate.findOne(any(Query.class), eq(ParseFillSnapshot.class))).thenReturn(null);
        when(mongoTemplate.remove(any(Query.class), eq(ParseFillSnapshot.class)))
                .thenReturn(DeleteResult.acknowledged(0));
        SurveyReportInfo current = new SurveyReportInfo();
        current.setId(77L);
        current.setFileRecordId(9L);
        current.setBuildingName("旧楼");
        when(mongoTemplate.findOne(any(Query.class), eq(SurveyReportInfo.class))).thenReturn(current);
        RoomInfo room = new RoomInfo();
        room.setId(501L);
        room.setRoomNumber("101");
        when(mongoTemplate.find(any(Query.class), eq(RoomInfo.class))).thenReturn(List.of(room));

        try (org.mockito.MockedStatic<com.gov.landcheck.core.config.mongo.MongoIdGenerator> ids = org.mockito.Mockito
                .mockStatic(com.gov.landcheck.core.config.mongo.MongoIdGenerator.class)) {
            ids.when(() -> com.gov.landcheck.core.config.mongo.MongoIdGenerator
                    .getNextId(org.mockito.ArgumentMatchers.anyString()))
                    .thenReturn(99L);
            snapshotService.captureIfAbsent(taskData);
        }

        ArgumentCaptor<ParseFillSnapshot> captor = ArgumentCaptor.forClass(ParseFillSnapshot.class);
        verify(mongoTemplate).insert(captor.capture());
        ParseFillSnapshot saved = captor.getValue();
        assertEquals(3L, saved.getParseJobId());
        assertTrue(saved.getHadExisting());
        assertEquals("旧楼", saved.getSurveyReportSnapshot().getBuildingName());
        assertEquals(1, saved.getRoomsSnapshot().size());
        assertEquals(501L, saved.getRoomsSnapshot().get(0).getId());
        assertEquals(1, taskData.getPreFillRoomsSnapshot().size());
    }

    private static TaskData surveyTask() {
        FileRecord fileRecord = new FileRecord();
        fileRecord.setId(9L);
        fileRecord.setProjectId(1L);
        fileRecord.setFileContextType(FileContextType.SURVEY_REPORT);
        ParseJob parseJob = new ParseJob();
        parseJob.setId(3L);
        parseJob.setFileContextType(FileContextType.SURVEY_REPORT);
        TaskData taskData = new TaskData(fileRecord);
        taskData.setParseJob(parseJob);
        return taskData;
    }
}
