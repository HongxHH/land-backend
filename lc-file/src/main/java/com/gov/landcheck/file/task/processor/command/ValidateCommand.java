package com.gov.landcheck.file.task.processor.command;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import com.gov.landcheck.core.bo.entity.SurveyReportInfo;
import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.core.service.UnknownUsageRecordService;
import com.gov.landcheck.file.service.ParseJobUpdateService;
import com.gov.landcheck.file.service.parse.ParseArtifactCleanupService;
import com.gov.landcheck.file.service.parse.ParseRollbackSummary;
import com.gov.landcheck.file.task.base.AbstractCommand;
import com.gov.landcheck.file.task.base.TaskData;
import com.gov.landcheck.file.task.base.TaskException;
import com.gov.landcheck.file.task.processor.receiver.DataValidReceiver;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * 数据校验命令 - 执行数据校验操作
 */
@Slf4j
@Component
public class ValidateCommand extends AbstractCommand {

    @Resource
    private DataValidReceiver dataValidReceiver;

    @Resource
    private MongoTemplate mongoTemplate;

    @Resource
    private ParseJobUpdateService parseJobUpdateService;

    @Resource
    private UnknownUsageRecordService unknownUsageRecordService;

    @Resource
    private ParseArtifactCleanupService parseArtifactCleanupService;

    public ValidateCommand() {
        super("数据校验", "VALIDATE");
    }

    @Override
    protected void doExecute(TaskData taskData) throws TaskException {
        log.info("开始数据校验: fileId={}", taskData.getFileRecord().getId());

        parseJobUpdateService.updateValidateStarted(taskData.getParseJob());

        try {
            Long fileRecordId = taskData.getFileRecord().getId();
            clearUnknownUsageRecordsForFile(fileRecordId);

            Query query = new Query(Criteria.where("file_record_id").is(fileRecordId));
            SurveyReportInfo surveyReportInfo = mongoTemplate.findOne(query, SurveyReportInfo.class);

            if (surveyReportInfo != null) {
                dataValidReceiver.validateSurveyData(surveyReportInfo, taskData.getFileRecord());
                taskData.setSurveyReportInfo(surveyReportInfo);
            }
            parseJobUpdateService.updateValidateCompleted(taskData.getParseJob(), "校验完成");
            log.info("数据校验完成: fileId={}", taskData.getFileRecord().getId());

        } catch (Exception e) {
            TaskException.ErrorCode errorCode = determineValidateErrorCode(e);
            log.error("数据校验失败: fileId={}, error={}",
                    taskData.getFileRecord().getId(), e.getMessage());
            parseJobUpdateService.updateValidateFailed(taskData.getParseJob(), e.getMessage());

            throw new TaskException(
                    errorCode,
                    getStage(),
                    taskData.getFileRecord().getId(),
                    taskData.getParseJob() != null ? taskData.getParseJob().getId() : null,
                    "数据校验失败: " + e.getMessage(),
                    e);
        }
    }

    @Override
    public boolean canSkip(TaskData taskData) {
        if (taskData == null || taskData.getFileRecord() == null) {
            return true;
        }
        return taskData.getFileRecord().getFileContextType() != FileContextType.SURVEY_REPORT;
    }

    /**
     * 校验阶段回滚：恢复 SurveyReportInfo 校验字段、RoomInfo 校验更新，并清理 UnknownUsageRecord。
     */
    @Override
    public ParseRollbackSummary rollback(TaskData taskData) throws TaskException {
        var summary = parseArtifactCleanupService.rollbackValidate(taskData);
        if (summary.hasFailures()) {
            log.warn("校验阶段回滚存在失败项: fileRecordId={}, summary={}",
                    taskData != null && taskData.getFileRecord() != null ? taskData.getFileRecord().getId() : null,
                    summary);
        }
        return summary;
    }

    private void clearUnknownUsageRecordsForFile(Long fileRecordId) {
        if (fileRecordId == null) {
            return;
        }
        try {
            long deleted = unknownUsageRecordService.deleteByFileRecordId(fileRecordId);
            if (deleted > 0) {
                log.info("已清理本文件未知用途记录, fileRecordId={}, deleted={}", fileRecordId, deleted);
            }
        } catch (Exception ex) {
            log.warn("清理未知用途记录失败: fileRecordId={}, error={}", fileRecordId, ex.getMessage());
        }
    }

    private TaskException.ErrorCode determineValidateErrorCode(Exception exception) {
        String message = exception.getMessage();
        if (message == null) {
            message = "";
        }
        String lowerMessage = message.toLowerCase();
        if (exception instanceof TaskException taskException) {
            return taskException.getErrorCode();
        }
        if (lowerMessage.contains("timeout") || lowerMessage.contains("time out")) {
            return TaskException.ErrorCode.VALIDATE_TIMEOUT;
        }
        return TaskException.ErrorCode.VALIDATE_FAILED;
    }
}
