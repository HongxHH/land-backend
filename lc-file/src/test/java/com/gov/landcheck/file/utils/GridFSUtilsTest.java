package com.gov.landcheck.file.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Date;

import org.bson.BsonObjectId;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSFindIterable;
import com.mongodb.client.gridfs.model.GridFSFile;

@ExtendWith(MockitoExtension.class)
class GridFSUtilsTest {

    @Mock
    private GridFSBucket gridFsBucket;
    @Mock
    private GridFSFindIterable gridFSFindIterable;

    private GridFSUtils gridFSUtils;

    @BeforeEach
    void setUp() {
        gridFSUtils = new GridFSUtils();
        ReflectionTestUtils.setField(gridFSUtils, "gridFsBucket", gridFsBucket);
    }

    @Test
    void existsReturnsTrueWhenGridFsFileIsFound() {
        String gridfsId = new ObjectId().toHexString();
        GridFSFile file = new GridFSFile(
                new BsonObjectId(new ObjectId(gridfsId)),
                "survey.pdf",
                1024L,
                255,
                new Date(),
                new Document());
        when(gridFsBucket.find(any(Document.class))).thenReturn(gridFSFindIterable);
        when(gridFSFindIterable.first()).thenReturn(file);

        assertThat(gridFSUtils.exists(gridfsId)).isTrue();
    }

    @Test
    void existsReturnsFalseOnlyWhenQueryConfirmsFileIsMissing() {
        String gridfsId = new ObjectId().toHexString();
        when(gridFsBucket.find(any(Document.class))).thenReturn(gridFSFindIterable);
        when(gridFSFindIterable.first()).thenReturn(null);

        assertThat(gridFSUtils.exists(gridfsId)).isFalse();
    }

    @Test
    void existsThrowsWhenGridFsQueryFails() {
        String gridfsId = new ObjectId().toHexString();
        when(gridFsBucket.find(any(Document.class))).thenThrow(new RuntimeException("temporary mongo outage"));

        assertThatThrownBy(() -> gridFSUtils.exists(gridfsId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GridFS 文件存在性检查失败");
    }

    @Test
    void existsReturnsFalseForMalformedGridFsIdWithoutQueryingGridFs() {
        assertThat(gridFSUtils.exists("not-an-object-id")).isFalse();

        verifyNoInteractions(gridFsBucket);
    }
}
