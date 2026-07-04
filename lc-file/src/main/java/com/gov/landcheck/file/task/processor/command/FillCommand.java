package com.gov.landcheck.file.task.processor.command;

import org.springframework.stereotype.Component;

import com.gov.landcheck.file.service.ParseJobUpdateService;
import com.gov.landcheck.file.service.parse.ParseArtifactCleanupService;
import com.gov.landcheck.file.service.parse.ParseRollbackSummary;
import com.gov.landcheck.file.task.base.AbstractCommand;
import com.gov.landcheck.file.task.base.TaskData;
import com.gov.landcheck.file.task.base.TaskException;
import com.gov.landcheck.file.task.processor.receiver.DataFillReceiver;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * 数据回填命令 - 执行数据回填操作
 */
@Slf4j
@Component
public class FillCommand extends AbstractCommand {

    @Resource
    private DataFillReceiver dataFillReceiver;

    @Resource
    private ParseJobUpdateService parseJobUpdateService;

    @Resource
    private ParseArtifactCleanupService parseArtifactCleanupService;

    public FillCommand() {
        super("数据回填", "FILL");
    }

    @Override
    protected void doExecute(TaskData taskData) throws TaskException {
        log.debug("开始数据回填: fileId={}", taskData.getFileRecord().getId());

        taskData.setFillCommandEntered(true);

        parseJobUpdateService.updateFillStarted(taskData.getParseJob());

        try {
            dataFillReceiver.fillData(taskData);
            parseJobUpdateService.updateFillCompleted(taskData.getParseJob(), null);
            log.debug("数据回填完成: fileId={}", taskData.getFileRecord().getId());

        } catch (Exception e) {
            TaskException.ErrorCode errorCode = determineFillErrorCode(e);
            parseJobUpdateService.updateFillFailed(taskData.getParseJob(), e.getMessage());

            throw new TaskException(
                    errorCode,
                    getStage(),
                    taskData.getFileRecord().getId(),
                    taskData.getParseJob() != null ? taskData.getParseJob().getId() : null,
                    "数据回填失败: " + e.getMessage(),
                    e);
        }
    }

    @Override
    public ParseRollbackSummary rollback(TaskData taskData) throws TaskException {
        var summary = parseArtifactCleanupService.rollbackFill(taskData);
        if (summary.hasFailures()) {
            log.warn("回填阶段回滚存在失败项: fileRecordId={}, summary={}",
                    taskData != null && taskData.getFileRecord() != null ? taskData.getFileRecord().getId() : null,
                    summary);
        }
        return summary;
    }

    private TaskException.ErrorCode determineFillErrorCode(Exception exception) {
        String message = exception.getMessage();
        if (message == null) {
            message = "";
        }
        String lowerMessage = message.toLowerCase();
        if (exception instanceof TaskException taskException) {
            return taskException.getErrorCode();
        }
        if (lowerMessage.contains("timeout") || lowerMessage.contains("time out")) {
            return TaskException.ErrorCode.FILL_TIMEOUT;
        }
        return TaskException.ErrorCode.FILL_FAILED;
    }
}
