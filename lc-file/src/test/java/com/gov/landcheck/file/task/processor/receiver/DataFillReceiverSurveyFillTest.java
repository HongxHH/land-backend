package com.gov.landcheck.file.task.processor.receiver;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.ParsedDataItem;
import com.gov.landcheck.core.bo.entity.RoomInfo;
import com.gov.landcheck.core.bo.entity.SurveyReportInfo;
import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.core.service.SurveyReportContractApprovalSyncService;
import com.gov.landcheck.core.service.UnknownUsageRecordService;
import com.gov.landcheck.file.task.base.TaskData;

@ExtendWith(MockitoExtension.class)
class DataFillReceiverSurveyFillTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private UnknownUsageRecordService unknownUsageRecordService;

    @Mock
    private SurveyReportContractApprovalSyncService surveyReportContractApprovalSyncService;

    @InjectMocks
    private DataFillReceiver dataFillReceiver;

    @BeforeEach
    void initHandlers() {
        dataFillReceiver.initHandlers();
    }

    @Test
    void existingSurveyFillClearsVerificationKeepsOcrAndDeletesUnknownUsage() throws Exception {
        FileRecord fileRecord = surveyFile(9L, 1L);
        TaskData taskData = new TaskData(fileRecord);
        taskData.setParsedDataItems(List.of(ocrItem(new BigDecimal("12.5"))));

        SurveyReportInfo existing = new SurveyReportInfo();
        existing.setId(77L);
        existing.setProjectId(1L);
        existing.setFileRecordId(9L);
        existing.setIsParsed(1);
        existing.setIsVerified(1);
        existing.setActualTotalBuildingArea(new BigDecimal("99"));
        existing.setRoomInfoBuildingAreaSumFromOcr(new BigDecimal("10"));
        when(mongoTemplate.findOne(any(Query.class), eq(SurveyReportInfo.class))).thenReturn(existing);
        when(mongoTemplate.find(any(Query.class), eq(RoomInfo.class))).thenReturn(List.of());

        dataFillReceiver.fillData(taskData);

        ArgumentCaptor<SurveyReportInfo> captor = ArgumentCaptor.forClass(SurveyReportInfo.class);
        verify(mongoTemplate).save(captor.capture());
        SurveyReportInfo saved = captor.getValue();
        assertNull(saved.getIsVerified());
        assertEquals(0, saved.getHasUnknownUsage());
        assertNull(saved.getActualTotalBuildingArea());
        assertEquals(1, saved.getIsParsed());
        assertEquals(new BigDecimal("12.5"), saved.getRoomInfoBuildingAreaSumFromOcr());
        verify(unknownUsageRecordService).deleteByFileRecordId(9L);
    }

    @Test
    void unknownUsageDeleteFailurePropagates() {
        FileRecord fileRecord = surveyFile(9L, 1L);
        TaskData taskData = new TaskData(fileRecord);
        SurveyReportInfo existing = new SurveyReportInfo();
        existing.setId(77L);
        existing.setProjectId(1L);
        existing.setFileRecordId(9L);
        existing.setIsParsed(1);
        existing.setIsVerified(1);
        when(mongoTemplate.findOne(any(Query.class), eq(SurveyReportInfo.class))).thenReturn(existing);
        when(mongoTemplate.find(any(Query.class), eq(RoomInfo.class))).thenReturn(List.of());
        when(unknownUsageRecordService.deleteByFileRecordId(9L)).thenThrow(new RuntimeException("mongo down"));

        assertThrows(RuntimeException.class, () -> dataFillReceiver.fillData(taskData));
    }

    private static FileRecord surveyFile(Long fileId, Long projectId) {
        FileRecord fileRecord = new FileRecord();
        fileRecord.setId(fileId);
        fileRecord.setProjectId(projectId);
        fileRecord.setFileContextType(FileContextType.SURVEY_REPORT);
        return fileRecord;
    }

    private static ParsedDataItem ocrItem(BigDecimal value) {
        ParsedDataItem item = new ParsedDataItem();
        item.setNormalizedKey("room_info_building_area_sum_from_ocr");
        item.setValueNumber(value);
        return item;
    }
}
