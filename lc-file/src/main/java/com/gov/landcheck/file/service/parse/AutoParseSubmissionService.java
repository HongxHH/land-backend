package com.gov.landcheck.file.service.parse;

import org.springframework.context.annotation.Lazy;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.core.enums.FileStateEnum;
import com.gov.landcheck.file.config.FileProcessingProperties;
import com.gov.landcheck.file.dto.SubmitParseResult;

import lombok.extern.slf4j.Slf4j;

/**
 * 上传完成后自动解析：直接提交到解析线程池，不再经延迟 drainer 队列。
 */
@Slf4j
@Service
public class AutoParseSubmissionService {

    private final FileProcessingProperties properties;
    private final FileParseSubmissionService fileParseSubmissionService;
    private final MongoTemplate mongoTemplate;

    public AutoParseSubmissionService(
            FileProcessingProperties properties,
            @Lazy FileParseSubmissionService fileParseSubmissionService,
            MongoTemplate mongoTemplate) {
        this.properties = properties;
        this.fileParseSubmissionService = fileParseSubmissionService;
        this.mongoTemplate = mongoTemplate;
    }

    public void submitAfterUpload(Long fileRecordId) {
        if (fileRecordId == null || !properties.getAutoParse().isEnabled()) {
            return;
        }
        FileRecord fileRecord = mongoTemplate.findById(fileRecordId, FileRecord.class);
        submitWaitingFile(fileRecord);
    }

    /**
     * @return 是否已成功提交到解析线程池
     */
    public boolean submitWaitingFile(FileRecord fileRecord) {
        if (!properties.getAutoParse().isEnabled()) {
            return false;
        }
        if (fileRecord == null || fileRecord.getId() == null) {
            return false;
        }
        if (!FileContextType.isAutoParseContext(fileRecord.getFileContextType())) {
            return false;
        }
        if (fileRecord.getFileState() != FileStateEnum.WAITING_PARSE) {
            log.debug("自动解析跳过：状态非 WAITING_PARSE fileId={}, state={}",
                    fileRecord.getId(), fileRecord.getFileState());
            return false;
        }

        SubmitParseResult result = fileParseSubmissionService.submitParseIfEligible(fileRecord);
        if (result.isSubmitted()) {
            log.debug("自动解析已提交: fileId={}, taskId={}", fileRecord.getId(), result.getTaskId());
            return true;
        }
        log.debug("自动解析未提交: fileId={}, reason={}", fileRecord.getId(), result.getErrorMessage());
        return false;
    }
}
