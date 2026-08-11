package com.gov.landcheck.file.service.parse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.core.enums.FileStateEnum;
import com.gov.landcheck.core.enums.FileType;
import com.gov.landcheck.file.dto.BulkParseEnqueueResultDTO;
import com.gov.landcheck.file.dto.SubmitParseResult;
import com.gov.landcheck.file.task.thread.TaskThreadPool;

@ExtendWith(MockitoExtension.class)
class GlobalParseEnqueueServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private FileParseSubmissionService fileParseSubmissionService;
    @Mock
    private TaskThreadPool taskThreadPool;

    @Test
    void enqueuePendingAndFailedSkipsAutoParseSuppressedFiles() {
        FileRecord fileRecord = parseableFile(101L);
        fileRecord.setAutoParseSuppressed(true);
        GlobalParseEnqueueService service = service();

        when(mongoTemplate.find(any(Query.class), eq(FileRecord.class))).thenReturn(List.of(fileRecord));
        when(mongoTemplate.count(any(Query.class), eq(FileRecord.class))).thenReturn(0L);
        when(taskThreadPool.hasSubmissionCapacity()).thenReturn(true);

        BulkParseEnqueueResultDTO result = service.enqueuePendingAndFailed();

        assertThat(result.getSubmitted()).isZero();
        assertThat(result.getSkipped()).isEqualTo(1);
        verify(fileParseSubmissionService, never()).findLatestParseJobByFileRecordId(101L);
        verify(fileParseSubmissionService, never()).submitParseIfEligible(fileRecord);
    }

    @Test
    void enqueuePendingAndFailedSubmitsEligibleUnsuppressedFiles() {
        FileRecord fileRecord = parseableFile(102L);
        GlobalParseEnqueueService service = service();

        when(mongoTemplate.find(any(Query.class), eq(FileRecord.class))).thenReturn(List.of(fileRecord));
        when(mongoTemplate.count(any(Query.class), eq(FileRecord.class))).thenReturn(0L);
        when(taskThreadPool.hasSubmissionCapacity()).thenReturn(true);
        when(fileParseSubmissionService.findLatestParseJobByFileRecordId(102L)).thenReturn(null);
        when(fileParseSubmissionService.submitParseIfEligible(fileRecord)).thenReturn(SubmitParseResult.success("task-1"));

        BulkParseEnqueueResultDTO result = service.enqueuePendingAndFailed();

        assertThat(result.getScanned()).isEqualTo(1);
        assertThat(result.getSubmitted()).isEqualTo(1);
        assertThat(result.getSkipped()).isZero();
    }

    private GlobalParseEnqueueService service() {
        return new GlobalParseEnqueueService(mongoTemplate, fileParseSubmissionService, taskThreadPool);
    }

    private static FileRecord parseableFile(Long id) {
        FileRecord fileRecord = new FileRecord();
        fileRecord.setId(id);
        fileRecord.setFileContextType(FileContextType.SURVEY_REPORT);
        fileRecord.setFileType(FileType.PDF);
        fileRecord.setFileState(FileStateEnum.WAITING_PARSE);
        fileRecord.setAutoParseSuppressed(false);
        return fileRecord;
    }
}
