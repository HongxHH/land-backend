package com.gov.landcheck.file.service;

import com.gov.landcheck.core.bo.entity.FileRecord;

/**
 * GridFS 缺失等场景下，按与 {@link com.gov.landcheck.file.service.FileService}
 * 相同的顺序级联清理文件相关数据。
 */
public interface InvalidGridFsFileCleanup {

    void cleanupFileRecordWhenGridFsMissing(FileRecord fileRecord);
}
