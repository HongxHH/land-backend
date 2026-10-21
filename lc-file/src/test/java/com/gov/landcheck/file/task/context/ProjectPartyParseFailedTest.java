package com.gov.landcheck.file.task.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.ProjectPartySurveySummaryForm;
import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.core.service.SurveyReportContractApprovalSyncService;
import com.gov.landcheck.core.service.UnknownUsageRecordService;
import com.gov.landcheck.file.task.processor.receiver.DataValidReceiver;
import com.gov.landcheck.file.task.processor.receiver.parse.ProjectPartySummaryExcelParser;
import com.gov.landcheck.file.task.processor.receiver.parse.RoomTableParser;
import com.gov.landcheck.file.task.processor.receiver.parse.SurveyDataParser;

@ExtendWith(MockitoExtension.class)
class ProjectPartyParseFailedTest {

    @Mock
    private MongoTemplate mongoTemplate;

    private ProjectPartySummaryParseModule partyModule;
    private SurveyReportParseModule surveyModule;

    @BeforeEach
    void setUp() {
        FillChangePublisher publisher = new FillChangePublisher(null);
        partyModule = new ProjectPartySummaryParseModule(
                mock(ProjectPartySummaryExcelParser.class),
                mongoTemplate,
                publisher);
        surveyModule = new SurveyReportParseModule(
                mock(SurveyDataParser.class),
                mock(RoomTableParser.class),
                mongoTemplate,
                publisher,
                mock(SurveyReportContractApprovalSyncService.class),
                mock(UnknownUsageRecordService.class),
                mock(DataValidReceiver.class));
    }

    @Test
    void missingFormInsertsFailedStatus() {
        FileRecord fileRecord = partyFile(9L, 1L);
        when(mongoTemplate.findOne(any(Query.class), eq(ProjectPartySurveySummaryForm.class))).thenReturn(null);

        try (org.mockito.MockedStatic<com.gov.landcheck.core.config.mongo.MongoIdGenerator> ids = org.mockito.Mockito
                .mockStatic(com.gov.landcheck.core.config.mongo.MongoIdGenerator.class)) {
            ids.when(() -> com.gov.landcheck.core.config.mongo.MongoIdGenerator
                    .getNextId(org.mockito.ArgumentMatchers.anyString()))
                    .thenReturn(99L);
            partyModule.onParseFailed(fileRecord, null);
        }

        ArgumentCaptor<ProjectPartySurveySummaryForm> captor = ArgumentCaptor.forClass(
                ProjectPartySurveySummaryForm.class);
        verify(mongoTemplate).save(captor.capture());
        assertEquals("FAILED", captor.getValue().getParseStatus());
        assertEquals(0, captor.getValue().getIsParsed());
        assertEquals("解析失败: 未知错误", captor.getValue().getRemark());
    }

    @Test
    void existingFormUpdatesFailedStatus() {
        FileRecord fileRecord = partyFile(9L, 1L);
        ProjectPartySurveySummaryForm existing = new ProjectPartySurveySummaryForm();
        existing.setId(77L);
        existing.setParseStatus("PENDING");
        when(mongoTemplate.findOne(any(Query.class), eq(ProjectPartySurveySummaryForm.class))).thenReturn(existing);

        partyModule.onParseFailed(fileRecord, "字段缺失");

        ArgumentCaptor<Update> captor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateFirst(any(Query.class), captor.capture(), eq(ProjectPartySurveySummaryForm.class));
        verify(mongoTemplate, never()).save(any(ProjectPartySurveySummaryForm.class));
        Document set = (Document) captor.getValue().getUpdateObject().get("$set");
        assertEquals("FAILED", set.get("parse_status"));
        assertEquals("解析失败: 字段缺失", set.get("remark"));
    }

    @Test
    void longFailureRemarkIsTruncated() {
        FileRecord fileRecord = partyFile(9L, 1L);
        when(mongoTemplate.findOne(any(Query.class), eq(ProjectPartySurveySummaryForm.class))).thenReturn(null);
        String raw = "a".repeat(901);

        try (org.mockito.MockedStatic<com.gov.landcheck.core.config.mongo.MongoIdGenerator> ids = org.mockito.Mockito
                .mockStatic(com.gov.landcheck.core.config.mongo.MongoIdGenerator.class)) {
            ids.when(() -> com.gov.landcheck.core.config.mongo.MongoIdGenerator
                    .getNextId(org.mockito.ArgumentMatchers.anyString()))
                    .thenReturn(99L);
            partyModule.onParseFailed(fileRecord, raw);
        }

        ArgumentCaptor<ProjectPartySurveySummaryForm> captor = ArgumentCaptor.forClass(
                ProjectPartySurveySummaryForm.class);
        verify(mongoTemplate).save(captor.capture());
        String remark = captor.getValue().getRemark();
        assertTrue(remark.startsWith("解析失败: "));
        assertTrue(remark.endsWith("…"));
        assertEquals(900, remark.substring("解析失败: ".length(), remark.length() - 1).length());
    }

    @Test
    void otherTypeDoesNotWritePartyForm() {
        FileRecord fileRecord = new FileRecord();
        fileRecord.setId(3L);
        fileRecord.setProjectId(1L);
        fileRecord.setFileContextType(FileContextType.SURVEY_REPORT);

        surveyModule.onParseFailed(fileRecord, "失败");

        verify(mongoTemplate, never()).save(any());
        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class),
                eq(ProjectPartySurveySummaryForm.class));
    }

    private static FileRecord partyFile(Long fileId, Long projectId) {
        FileRecord fileRecord = new FileRecord();
        fileRecord.setId(fileId);
        fileRecord.setProjectId(projectId);
        fileRecord.setFileContextType(FileContextType.PROJECT_PARTY_SURVEY_SUMMARY);
        return fileRecord;
    }
}
