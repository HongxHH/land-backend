package com.gov.landcheck.file.task.processor.command;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import com.gov.landcheck.core.bo.entity.SurveyReportInfo;
import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.core.service.UnknownUsageRecordService;
import com.gov.landcheck.file.service.ParseJobUpdateService;
import com.gov.landcheck.file.task.base.AbstractCommand;
import com.gov.landcheck.file.task.base.TaskData;
import com.gov.landcheck.file.task.base.TaskException;
import com.gov.landcheck.file.task.processor.receiver.DataValidReceiver;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * 命令模式中角色：具体命令 - 数据校验命令
 * 数据校验命令 - 执行数据校验操作
 *
 * @author system
 * @date 2026/01/27
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

    public ValidateCommand() {
        super("数据校验", "VALIDATE");
    }

    @Override
    protected void doExecute(TaskData taskData) throws TaskException {
        log.info("开始数据校验: fileId={}", taskData.getFileRecord().getId());

        parseJobUpdateService.updateValidateStarted(taskData.getParseJob());

        try {
            // 动态链路仅在实测报告下组装 ValidateCommand，这里直接执行校验流程。
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
            // 根据异常类型确定具体的错误码
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
        // 动态组装后默认不会触发该分支，保留兜底以应对错误装配。
        if (taskData == null || taskData.getFileRecord() == null) {
            return true;
        }
        return taskData.getFileRecord().getFileContextType() != FileContextType.SURVEY_REPORT;
    }

    /**
     * 数据校验阶段回滚：
     * 删除本文件在校验阶段产生的 UnknownUsageRecord（校验时遇到未知用途会写入）。
     * SurveyReportInfo / RoomInfo 的校验更新若在同一事务内，已由 @Transactional 回滚。
     */
    @Override
    public void rollback(TaskData taskData) throws TaskException {
        if (taskData == null || taskData.getFileRecord() == null) {
            return;
        }
        clearUnknownUsageRecordsForFile(taskData.getFileRecord().getId());
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

    /**
     * 根据异常类型确定校验错误码
     */
    private TaskException.ErrorCode determineValidateErrorCode(Exception exception) {
        String message = exception.getMessage();
        if (message == null)
            message = "";

        String lowerMessage = message.toLowerCase();

        // 检查是否是TaskException，如果是则保持原有错误码
        if (exception instanceof TaskException taskException) {
            return taskException.getErrorCode();
        }

        // 校验操作涉及数据库查询，超时通常是数据库相关
        if (lowerMessage.contains("timeout") || lowerMessage.contains("time out")) {
            return TaskException.ErrorCode.VALIDATE_TIMEOUT;
        }

        // 默认使用通用校验失败错误码
        return TaskException.ErrorCode.VALIDATE_FAILED;
    }
}