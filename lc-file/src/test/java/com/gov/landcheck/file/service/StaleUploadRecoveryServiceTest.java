package com.gov.landcheck.file.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.util.ReflectionTestUtils;

import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.file.config.FileProcessingProperties;
import com.gov.landcheck.file.task.thread.UploadThreadPool;
import com.mongodb.client.result.UpdateResult;

@ExtendWith(MockitoExtension.class)
class StaleUploadRecoveryServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private UpdateResult updateResult;

    @Mock
    private UploadThreadPool uploadThreadPool;

    @Mock
    private ActiveUploadTracker activeUploadTracker;

    @InjectMocks
    private StaleUploadRecoveryService recoveryService;

    private FileProcessingProperties properties;

    @BeforeEach
    void setUp() {
        properties = new FileProcessingProperties();
        ReflectionTestUtils.setField(recoveryService, "fileProcessingProperties", properties);
        lenient().when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(FileRecord.class)))
                .thenReturn(updateResult);
        lenient().when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(FileRecord.class)))
                .thenReturn(updateResult);
        lenient().when(updateResult.getModifiedCount()).thenReturn(0L);
        lenient().when(mongoTemplate.find(any(Query.class), eq(FileRecord.class))).thenReturn(List.of());
    }

    @Test
    void recoverOnStartupUsesGracePeriodCutoff() {
        when(updateResult.getModifiedCount()).thenReturn(2L, 3L);

        StaleUploadRecoveryService.RecoveryStats stats = recoveryService.recoverOnStartup();

        assertEquals(2L, stats.uploadingFailed());
        assertEquals(3L, stats.postProcessFailed());

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate, times(2))
                .updateMulti(queryCaptor.capture(), any(Update.class), eq(FileRecord.class));

        String firstQuery = queryCaptor.getAllValues().get(0).toString();
        String secondQuery = queryCaptor.getAllValues().get(1).toString();
        assertTrue(firstQuery.contains("UPLOADING"));
        assertTrue(firstQuery.contains("update_time"));
        assertTrue(secondQuery.contains("WAITING_POST_PROCESS"));
        assertTrue(secondQuery.contains("update_time"));
    }

    @Test
    void recoverTimedOutSkipsActivePostProcessFiles() {
        when(updateResult.getModifiedCount()).thenReturn(1L, 1L);

        FileRecord active = new FileRecord();
        active.setId(100L);
        FileRecord idle = new FileRecord();
        idle.setId(101L);
        when(mongoTemplate.find(any(Query.class), eq(FileRecord.class)))
                .thenReturn(List.of())
                .thenReturn(List.of(active, idle));
        when(uploadThreadPool.isPostProcessActive("100")).thenReturn(true);
        when(uploadThreadPool.isPostProcessActive("101")).thenReturn(false);

        StaleUploadRecoveryService.RecoveryStats stats = recoveryService.recoverTimedOut();
        assertEquals(0L, stats.uploadingFailed());
        assertEquals(1L, stats.postProcessFailed());

        verify(mongoTemplate, never()).updateMulti(any(Query.class), any(Update.class), eq(FileRecord.class));
        verify(mongoTemplate, times(1)).updateFirst(any(Query.class), any(Update.class), eq(FileRecord.class));
        verify(uploadThreadPool).isPostProcessActive("100");
        verify(uploadThreadPool).isPostProcessActive("101");
    }

    @Test
    void recoverTimedOutMarksNoPostProcessWhenAllActive() {
        FileRecord active = new FileRecord();
        active.setId(200L);
        when(mongoTemplate.find(any(Query.class), eq(FileRecord.class)))
                .thenReturn(List.of())
                .thenReturn(List.of(active));
        when(uploadThreadPool.isPostProcessActive("200")).thenReturn(true);

        StaleUploadRecoveryService.RecoveryStats stats = recoveryService.recoverTimedOut();
        assertEquals(0L, stats.postProcessFailed());
        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(FileRecord.class));
    }

    @Test
    void recoverTimedOutSkipsUploadsStillReceivingBytes() {
        FileRecord receiving = new FileRecord();
        receiving.setId(10L);
        FileRecord stalled = new FileRecord();
        stalled.setId(11L);
        when(mongoTemplate.find(any(Query.class), eq(FileRecord.class)))
                .thenReturn(List.of(receiving, stalled))
                .thenReturn(List.of());
        when(activeUploadTracker.isReceiving(10L, properties.getStaleUpload().getUploadingTimeoutMs()))
                .thenReturn(true);
        when(updateResult.getModifiedCount()).thenReturn(1L);

        StaleUploadRecoveryService.RecoveryStats stats = recoveryService.recoverTimedOut();

        assertEquals(1L, stats.uploadingFailed());
        assertEquals(0L, stats.postProcessFailed());
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate, times(1)).updateFirst(queryCaptor.capture(), any(Update.class), eq(FileRecord.class));
        String query = queryCaptor.getValue().toString();
        assertTrue(query.contains("11"));
        assertTrue(query.contains("UPLOADING"));
        verify(mongoTemplate, never()).updateMulti(any(Query.class), any(Update.class), eq(FileRecord.class));
    }

    @Test
    void disabledSkipsUpdates() {
        properties.getStaleUpload().setEnabled(false);
        StaleUploadRecoveryService.RecoveryStats stats = recoveryService.recoverOnStartup();
        assertEquals(0L, stats.uploadingFailed());
        assertEquals(0L, stats.postProcessFailed());
        verifyNoInteractions(mongoTemplate);
    }
}
