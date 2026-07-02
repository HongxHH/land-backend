package com.gov.landcheck.file.service;

import com.gov.landcheck.core.bo.entity.UploadRecord;

/**
 * 上传记录服务接口
 *
 * @author system
 * @date 2025/12/20
 */
public interface UploadRecordService {

    /**
     * 插入上传记录
     *
     * @param uploadRecord 上传记录
     */
    void insertUploadRecord(UploadRecord uploadRecord);

    /**
     * 根据文件ID删除上传记录
     *
     * @param fileId 文件ID
     */
    void deleteByFileId(Long fileId);
}
