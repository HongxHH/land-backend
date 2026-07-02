package com.gov.landcheck.file.task.thread;

import java.time.LocalDateTime;

import org.springframework.data.mongodb.core.MongoTemplate;

import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.UploadRecord;
import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.core.enums.FileStateEnum;
import com.gov.landcheck.core.enums.FileType;
import com.gov.landcheck.core.enums.UploadStatusEnum;
import com.gov.landcheck.file.service.UploadRecordService;
import com.gov.landcheck.file.service.parse.DeferredParseSubmissionService;
import com.gov.landcheck.file.utils.GridFSUtils;
import com.gov.landcheck.file.utils.PdfProcessor;

import lombok.extern.slf4j.Slf4j;

/**
 * 单文件上传后处理：缩略图、状态迁移、上传记录、延迟自动提交解析。
 */
@Slf4j
public class FileUploadPostProcessTask implements Runnable {

    private final String taskId;
    private final String fileId;
    private final Long operatorId;
    private final String operatorName;

    private final MongoTemplate mongoTemplate;
    private final UploadRecordService uploadRecordService;
    private final PdfProcessor pdfProcessor;
    private final GridFSUtils gridFSUtils;
    private final DeferredParseSubmissionService deferredParseSubmissionService;

    private volatile boolean failureHandled;

    public FileUploadPostProcessTask(String taskId, String fileId, Long operatorId, String operatorName,
            MongoTemplate mongoTemplate, UploadRecordService uploadRecordService,
            PdfProcessor pdfProcessor, GridFSUtils gridFSUtils,
            DeferredParseSubmissionService deferredParseSubmissionService) {
        this.taskId = taskId;
        this.fileId = fileId;
        this.operatorId = operatorId;
        this.operatorName = operatorName;
        this.mongoTemplate = mongoTemplate;
        this.uploadRecordService = uploadRecordService;
        this.pdfProcessor = pdfProcessor;
        this.gridFSUtils = gridFSUtils;
        this.deferredParseSubmissionService = deferredParseSubmissionService;
    }

    public String getTaskId() {
        return taskId;
    }

    @Override
    public void run() {
        log.debug("开始文件上传后处理: fileId={}", fileId);
        try {
            processPostUpload();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void onSuccess() {
        if (failureHandled) {
            return;
        }
        log.debug("文件上传后处理成功: fileId={}", fileId);
        try {
            FileRecord fileRecord = loadFileRecord();
            if (fileRecord == null || !isAutoParseContext(fileRecord.getFileContextType())) {
                return;
            }
            deferredParseSubmissionService.enqueueAfterUpload(fileRecord.getId());
        } catch (Exception e) {
            log.warn("上传后延迟自动解析入队失败: fileId={}, error={}", fileId, e.getMessage(), e);
        }
    }

    public void onFailure(Throwable throwable) {
        String reason = throwable != null && throwable.getMessage() != null
                ? throwable.getMessage()
                : "unknown";
        handleFailure(reason);
        log.warn("文件上传后处理失败: fileId={}, error={}", fileId, reason);
    }

    private void processPostUpload() throws Exception {
        FileRecord fileRecord = loadFileRecord();
        if (fileRecord == null) {
            throw new RuntimeException("文件记录不存在: fileId=" + fileId);
        }

        String originalFilename = fileRecord.getOriginalName();
        if (fileRecord.getGridfsId() == null || fileRecord.getGridfsId().isBlank()) {
            throw new RuntimeException("GridFS文件不存在: fileId=" + fileId);
        }

        if (fileRecord.getFileType() == FileType.PDF) {
            byte[] fileBytes = gridFSUtils.getFileBytes(fileRecord.getGridfsId());
            try {
                String thumbGridfsId = pdfProcessor.generateThumbnail(fileBytes);
                fileRecord.setThumbGridfsId(thumbGridfsId);
            } catch (Exception e) {
                log.warn("生成缩略图失败: fileName={}, fileId={}, error={}",
                        originalFilename, fileId, e.getMessage());
            } finally {
                fileBytes = null;
            }
        }

        if (isAutoParseContext(fileRecord.getFileContextType())) {
            fileRecord.setFileState(FileStateEnum.WAITING_PARSE);
        } else {
            fileRecord.setFileState(FileStateEnum.UNPARSEABLE);
        }

        mongoTemplate.save(fileRecord);

        UploadRecord uploadRecord = new UploadRecord();
        uploadRecord.setFileId(fileRecord.getId());
        uploadRecord.setFileName(fileRecord.getOriginalName());
        uploadRecord.setUploadUserId(operatorId != null ? operatorId : 0L);
        uploadRecord.setUploadUserName(resolveOperatorName());
        uploadRecord.setUploadTime(LocalDateTime.now());
        uploadRecord.setUploadStatus(UploadStatusEnum.SUCCESS);
        uploadRecordService.insertUploadRecord(uploadRecord);
    }

    private void handleFailure(String reason) {
        if (failureHandled) {
            return;
        }
        failureHandled = true;
        try {
            FileRecord fileRecord = loadFileRecord();
            if (fileRecord == null) {
                return;
            }
            if (fileRecord.getFileState() == FileStateEnum.UPLOAD_FAIL) {
                return;
            }
            fileRecord.setFileState(FileStateEnum.UPLOAD_FAIL);
            fileRecord.setParseMessage("后处理失败: " + reason);
            mongoTemplate.save(fileRecord);

            UploadRecord uploadRecord = new UploadRecord();
            uploadRecord.setFileId(fileRecord.getId());
            uploadRecord.setFileName(fileRecord.getOriginalName());
            uploadRecord.setUploadUserId(operatorId != null ? operatorId : 0L);
            uploadRecord.setUploadUserName(resolveOperatorName());
            uploadRecord.setUploadTime(LocalDateTime.now());
            uploadRecord.setUploadStatus(UploadStatusEnum.FAILED);
            uploadRecord.setFailureReason(reason);
            uploadRecordService.insertUploadRecord(uploadRecord);
        } catch (Exception e) {
            log.error("标记上传失败状态时出错: fileId={}, error={}", fileId, e.getMessage());
        }
    }

    private FileRecord loadFileRecord() {
        try {
            return mongoTemplate.findById(Long.parseLong(fileId), FileRecord.class);
        } catch (NumberFormatException ex) {
            return mongoTemplate.findById(fileId, FileRecord.class);
        }
    }

    private boolean isAutoParseContext(FileContextType contextType) {
        return contextType == FileContextType.CONTRACT
                || contextType == FileContextType.SURVEY_REPORT
                || contextType == FileContextType.PLANNING_REVIEW
                || contextType == FileContextType.CAPACITY_INDICATOR
                || contextType == FileContextType.PROJECT_PARTY_SURVEY_SUMMARY;
    }

    private String resolveOperatorName() {
        if (operatorName != null && !operatorName.isBlank()) {
            return operatorName;
        }
        return "system";
    }
}
