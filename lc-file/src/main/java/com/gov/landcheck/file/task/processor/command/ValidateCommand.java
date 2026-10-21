package com.gov.landcheck.file.task.processor.command;

import org.springframework.stereotype.Component;

import com.gov.landcheck.file.service.ParseJobUpdateService;
import com.gov.landcheck.file.service.parse.ParseArtifactCleanupService;
import com.gov.landcheck.file.service.parse.ParseRollbackSummary;
import com.gov.landcheck.file.task.base.AbstractCommand;
import com.gov.landcheck.file.task.base.TaskData;
import com.gov.landcheck.file.task.base.TaskException;
import com.gov.landcheck.file.task.context.FileParseModuleRegistry;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * 数据校验命令 - 执行数据校验操作
 */
@Slf4j
@Component
public class ValidateCommand extends AbstractCommand {

    @Resource
    private FileParseModuleRegistry fileParseModuleRegistry;

    @Resource
    private ParseJobUpdateService parseJobUpdateService;

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
            fileParseModuleRegistry.require(taskData.getFileRecord().getFileContextType()).validate(taskData);
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
