package com.gov.landcheck.file.service.impl;

import java.time.LocalDateTime;
import java.util.Objects;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import com.gov.landcheck.core.bo.entity.UploadRecord;
import com.gov.landcheck.file.service.UploadRecordService;

import jakarta.annotation.Resource;

/**
 * 上传记录服务实现类
 *
 * @author system
 * @date 2025/12/20
 */
@Service
public class UploadRecordServiceImpl implements UploadRecordService {

    @Resource
    private MongoTemplate mongoTemplate;

    @Override
    public void insertUploadRecord(UploadRecord uploadRecord) {
        Objects.requireNonNull(uploadRecord, "uploadRecord");
        if (uploadRecord.getUploadTime() == null) {
            uploadRecord.setUploadTime(LocalDateTime.now());
        }
        uploadRecord.preSave(); // 统一的预保存处理
        mongoTemplate.save(uploadRecord);
    }

    @Override
    public void deleteByFileId(Long fileId) {
        Query query = new Query(Criteria.where("file_id").is(fileId));
        mongoTemplate.remove(query, UploadRecord.class);
    }
}
