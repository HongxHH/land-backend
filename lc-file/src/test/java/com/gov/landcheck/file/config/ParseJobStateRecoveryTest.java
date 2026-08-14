package com.gov.landcheck.file.config;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

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
import org.springframework.test.util.ReflectionTestUtils;

import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.ParseJob;
import com.gov.landcheck.core.enums.FileStateEnum;
import com.gov.landcheck.core.enums.ParseJobStateEnum;
import com.mongodb.client.result.UpdateResult;

@ExtendWith(MockitoExtension.class)
class ParseJobStateRecoveryTest {

    @Mock
    private MongoTemplate mongoTemplate;

    private ParseJobStateRecovery recovery;

    @BeforeEach
    void setUp() {
        recovery = new ParseJobStateRecovery();
        ReflectionTestUtils.setField(recovery, "mongoTemplate", mongoTemplate);
    }

    @Test
    void runRestoresParsingFileWhenParseJobAlreadySucceeded() {
        Long parseJobId = 1001L;

        FileRecord parsingRecord = new FileRecord();
        parsingRecord.setId(2001L);
        parsingRecord.setFileState(FileStateEnum.PARSING);
        parsingRecord.setParseJobId(parseJobId);

        ParseJob successJob = new ParseJob();
        successJob.setId(parseJobId);
        successJob.setJobStatus(ParseJobStateEnum.SUCCESS);

        when(mongoTemplate.find(any(Query.class), eq(ParseJob.class)))
                .thenReturn(List.of(), List.of(successJob));
        when(mongoTemplate.find(any(Query.class), eq(FileRecord.class))).thenReturn(List.of(parsingRecord));
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(ParseJob.class)))
                .thenReturn(UpdateResult.acknowledged(0L, 0L, null));
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(FileRecord.class)))
                .thenReturn(UpdateResult.acknowledged(0L, 0L, null))
                .thenReturn(UpdateResult.acknowledged(1L, 1L, null))
                .thenReturn(UpdateResult.acknowledged(0L, 0L, null));

        recovery.run(null);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, times(3)).updateMulti(queryCaptor.capture(), updateCaptor.capture(), eq(FileRecord.class));

        List<Query> fileQueries = queryCaptor.getAllValues();
        List<Update> fileUpdates = updateCaptor.getAllValues();

        int restoreIndex = findUpdateByFileState(fileUpdates, FileStateEnum.PARSE_COMPLETE);
        int failIndex = findUpdateByFileState(fileUpdates, FileStateEnum.PARSE_FAIL);

        assertTrue(restoreIndex >= 0, "successful parse job should restore FileRecord to PARSE_COMPLETE");
        assertTrue(failIndex >= 0, "remaining parsing files should still be failed");
        assertTrue(fileQueries.get(restoreIndex).getQueryObject().toJson().contains("\"$in\": [1001]"),
                "restore query should target the succeeded parse job");
        assertTrue(fileQueries.get(failIndex).getQueryObject().toJson().contains("\"$nin\": [1001]"),
                "failure query should exclude the succeeded parse job");
    }

    private int findUpdateByFileState(List<Update> updates, FileStateEnum fileState) {
        for (int i = 0; i < updates.size(); i++) {
            Document set = updates.get(i).getUpdateObject().get("$set", Document.class);
            if (set != null && fileState.getCode().equals(set.getString("file_state"))) {
                return i;
            }
        }
        return -1;
    }
}
