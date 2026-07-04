package com.gov.landcheck.file.service.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import com.gov.landcheck.core.bo.entity.ContractInfo;
import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.ParseJob;
import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.file.task.base.TaskData;

@ExtendWith(MockitoExtension.class)
class ParseArtifactCleanupServiceFillRollbackTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private ParseArtifactCleanupService cleanupService;

    private FileRecord fileRecord;
    private ParseJob parseJob;
    private TaskData taskData;

    @BeforeEach
    void setUp() {
        fileRecord = new FileRecord();
        fileRecord.setId(100L);
        fileRecord.setProjectId(10L);
        fileRecord.setFileContextType(FileContextType.CONTRACT);

        parseJob = new ParseJob();
        parseJob.setId(200L);
        parseJob.setFillStatus("SUCCESS");

        taskData = new TaskData();
        taskData.setFileRecord(fileRecord);
        taskData.setParseJob(parseJob);
        taskData.setFillCommandEntered(true);
    }

    @Test
    void offlineContractRollback_withoutSnapshot_shouldNotDeleteExistingContract() {
        taskData.setPreFillContractExisted(false);
        taskData.setFillCreatedNewContract(false);

        cleanupService.rollbackFill(taskData);

        verify(mongoTemplate, never()).remove(any(Query.class), eq(ContractInfo.class));
    }

    @Test
    void contractRollback_withSnapshot_shouldRestoreSnapshot() {
        ContractInfo snapshot = new ContractInfo();
        snapshot.setId(1L);
        snapshot.setProjectId(10L);
        snapshot.setFileRecordId(100L);
        snapshot.setContractNumber("OLD-001");
        taskData.setPreFillContractSnapshot(snapshot);

        cleanupService.rollbackFill(taskData);

        verify(mongoTemplate).save(snapshot);
        verify(mongoTemplate, never()).remove(any(Query.class), eq(ContractInfo.class));
        assertEquals("OLD-001", snapshot.getContractNumber());
    }

    @Test
    void contractRollback_fillCreatedNew_shouldDeleteContract() {
        taskData.setFillCreatedNewContract(true);
        ContractInfo created = new ContractInfo();
        created.setId(2L);
        created.setProjectId(10L);
        when(mongoTemplate.find(any(Query.class), eq(ContractInfo.class))).thenReturn(List.of(created));

        cleanupService.rollbackFill(taskData);

        verify(mongoTemplate).remove(any(Query.class), eq(ContractInfo.class));
    }

    @Test
    void copyContractSnapshot_preservesBusinessFields() {
        ContractInfo source = new ContractInfo();
        source.setId(5L);
        source.setContractNumber("CN-1");
        source.setTotalArea(new BigDecimal("100.5"));

        ContractInfo copy = ParseArtifactCleanupService.copyContractSnapshot(source);

        assertEquals(source.getId(), copy.getId());
        assertEquals(source.getContractNumber(), copy.getContractNumber());
        assertEquals(source.getTotalArea(), copy.getTotalArea());
    }

    @Test
    void copyPlanningRowSnapshots_emptyInput_returnsEmptyList() {
        assertEquals(Collections.emptyList(), ParseArtifactCleanupService.copyPlanningRowSnapshots(null));
        assertEquals(Collections.emptyList(), ParseArtifactCleanupService.copyPlanningRowSnapshots(List.of()));
    }
}
