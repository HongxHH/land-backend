package com.gov.landcheck.file.task.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import com.gov.landcheck.core.audit.TargetType;
import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.ProjectPartySurveySummaryForm;
import com.gov.landcheck.core.bo.entity.SurveyReportInfo;
import com.gov.landcheck.core.service.SurveyReportContractApprovalSyncService;
import com.gov.landcheck.core.service.UnknownUsageRecordService;
import com.gov.landcheck.file.task.processor.receiver.DataValidReceiver;
import com.gov.landcheck.file.task.processor.receiver.parse.ContractDataParser;
import com.gov.landcheck.file.task.processor.receiver.parse.ProjectPartySummaryExcelParser;
import com.gov.landcheck.file.task.processor.receiver.parse.RoomTableParser;
import com.gov.landcheck.file.task.processor.receiver.parse.SurveyDataParser;

@ExtendWith(MockitoExtension.class)
class PlaceholderCreationTest {

    @Mock
    private MongoTemplate mongoTemplate;

    private SurveyReportParseModule surveyModule;
    private ProjectPartySummaryParseModule partyModule;

    @BeforeEach
    void setUp() {
        FillChangePublisher publisher = new FillChangePublisher(null);
        surveyModule = new SurveyReportParseModule(
                mock(SurveyDataParser.class),
                mock(RoomTableParser.class),
                mongoTemplate,
                publisher,
                mock(SurveyReportContractApprovalSyncService.class),
                mock(UnknownUsageRecordService.class),
                mock(DataValidReceiver.class));
        partyModule = new ProjectPartySummaryParseModule(
                mock(ProjectPartySummaryExcelParser.class),
                mongoTemplate,
                publisher);
    }

    @Test
    void surveyPlaceholderKeepsPhaseAndRequestsApprovalSync() {
        FileRecord fileRecord = file(9L, 1L);
        when(mongoTemplate.findOne(any(Query.class), eq(SurveyReportInfo.class))).thenReturn(null);

        PlaceholderCreated created;
        try (org.mockito.MockedStatic<com.gov.landcheck.core.config.mongo.MongoIdGenerator> ids = org.mockito.Mockito
                .mockStatic(com.gov.landcheck.core.config.mongo.MongoIdGenerator.class)) {
            ids.when(() -> com.gov.landcheck.core.config.mongo.MongoIdGenerator
                    .getNextId(org.mockito.ArgumentMatchers.anyString()))
                    .thenReturn(99L);
            created = surveyModule.createPlaceholder(fileRecord, 2);
        }

        ArgumentCaptor<SurveyReportInfo> captor = ArgumentCaptor.forClass(SurveyReportInfo.class);
        verify(mongoTemplate).save(captor.capture());
        SurveyReportInfo saved = captor.getValue();
        assertEquals(2, saved.getPhase());
        assertEquals(0, saved.getIsParsed());
        assertEquals(9L, saved.getFileRecordId());
        assertEquals(TargetType.SURVEY_REPORT, created.auditTarget());
        assertTrue(created.syncSurveyContractApproval());
    }

    @Test
    void existingSurveySkipsCreate() {
        FileRecord fileRecord = file(9L, 1L);
        when(mongoTemplate.findOne(any(Query.class), eq(SurveyReportInfo.class))).thenReturn(new SurveyReportInfo());

        assertNull(surveyModule.createPlaceholder(fileRecord, 2));
        verify(mongoTemplate, never()).save(any(SurveyReportInfo.class));
    }

    @Test
    void partyPlaceholderStartsPendingWithoutApprovalSync() {
        FileRecord fileRecord = file(4L, 8L);
        when(mongoTemplate.findOne(any(Query.class), eq(ProjectPartySurveySummaryForm.class))).thenReturn(null);

        PlaceholderCreated created;
        try (org.mockito.MockedStatic<com.gov.landcheck.core.config.mongo.MongoIdGenerator> ids = org.mockito.Mockito
                .mockStatic(com.gov.landcheck.core.config.mongo.MongoIdGenerator.class)) {
            ids.when(() -> com.gov.landcheck.core.config.mongo.MongoIdGenerator
                    .getNextId(org.mockito.ArgumentMatchers.anyString()))
                    .thenReturn(99L);
            created = partyModule.createPlaceholder(fileRecord, null);
        }

        ArgumentCaptor<ProjectPartySurveySummaryForm> captor = ArgumentCaptor.forClass(
                ProjectPartySurveySummaryForm.class);
        verify(mongoTemplate).save(captor.capture());
        assertEquals("PENDING", captor.getValue().getParseStatus());
        assertEquals(0, captor.getValue().getIsParsed());
        assertEquals(TargetType.PROJECT_PARTY_SUMMARY_FORM, created.auditTarget());
        assertFalse(created.syncSurveyContractApproval());
    }

    private static FileRecord file(Long fileId, Long projectId) {
        FileRecord fileRecord = new FileRecord();
        fileRecord.setId(fileId);
        fileRecord.setProjectId(projectId);
        return fileRecord;
    }
}
