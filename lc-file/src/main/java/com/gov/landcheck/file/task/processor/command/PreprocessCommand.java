package com.gov.landcheck.file.task.processor.command;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.file.service.ParseJobUpdateService;
import com.gov.landcheck.file.service.parse.ParseArtifactCleanupService;
import com.gov.landcheck.file.service.parse.ParseRollbackSummary;
import com.gov.landcheck.file.task.base.AbstractCommand;
import com.gov.landcheck.file.task.base.TaskData;
import com.gov.landcheck.file.task.base.TaskException;
import com.gov.landcheck.file.task.processor.receiver.PdfPreReceiver;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * 命令模式中角色：具体命令 - 预处理命令
 * 预处理命令 - 执行PDF预处理操作
 *
 * @author system
 * @date 2026/01/27
 */
@Slf4j
@Component
public class PreprocessCommand extends AbstractCommand {

    @Resource
    private PdfPreReceiver pdfPreprocessor;

    @Resource
    private MongoTemplate mongoTemplate;

    @Resource
    private ParseJobUpdateService parseJobUpdateService;

    @Resource
    private ParseArtifactCleanupService parseArtifactCleanupService;

    public PreprocessCommand() {
        super("PDF预处理", "PREPROCESS");
    }

    @Override
    protected void doExecute(TaskData taskData) throws TaskException {
        log.debug("开始PDF预处理: fileId={}", taskData.getFileRecord().getId());

        parseJobUpdateService.updatePreprocessStarted(taskData.getParseJob());

        FileRecord fileRecord = taskData.getFileRecord();

        try {
            // 删除已存在的预处理结果
            String originalGridfsId = fileRecord.getPreprocessGridfsId();
            if (StringUtils.hasText(originalGridfsId)) {
                pdfPreprocessor.deleteExistingPreprocessResult(originalGridfsId);
            }

            // 执行预处理（传入 taskData 以支持取消时强杀子进程并保证上层清理中间文件）
            String preprocessGridfsId = pdfPreprocessor.preprocess(
                    fileRecord.getGridfsId(),
                    fileRecord.getOriginalName(),
                    taskData);

            // 更新任务数据
            taskData.setPreprocessGridfsId(preprocessGridfsId);
            fileRecord.setPreprocessGridfsId(preprocessGridfsId);

            // 保存到数据库
            mongoTemplate.save(fileRecord);

            // 更新进度
            taskData.updateProgress(25);

            parseJobUpdateService.updatePreprocessCompleted(taskData.getParseJob(), preprocessGridfsId);

            log.debug("PDF预处理完成: fileId={}, preprocessGridfsId={}",
                    fileRecord.getId(), preprocessGridfsId);

        } catch (Exception e) {
            boolean cancelled = (e.getMessage() != null && e.getMessage().contains("任务已取消"))
                    || (e.getCause() instanceof InterruptedException);
            if (cancelled) {
                log.debug("任务已取消，预处理已终止: fileId={}", fileRecord.getId());
                throw e instanceof TaskException ? (TaskException) e
                        : new TaskException(TaskException.ErrorCode.PREPROCESS_FAILED, getStage(),
                                fileRecord.getId(),
                                taskData.getParseJob() != null ? taskData.getParseJob().getId() : null,
                                "任务已取消，已终止Python预处理进程", e);
            }
            // 根据异常类型确定具体的错误码
            TaskException.ErrorCode errorCode = determinePreprocessErrorCode(e);
            log.error("PDF预处理失败: fileId={}, error={}", fileRecord.getId(), e.getMessage());
            parseJobUpdateService.updatePreprocessFailed(taskData.getParseJob(), e.getMessage());

            throw new TaskException(errorCode, getStage(),
                    fileRecord.getId(), taskData.getParseJob() != null ? taskData.getParseJob().getId() : null,
                    "PDF预处理失败: " + e.getMessage(), e);
        }
    }

    @Override
    public ParseRollbackSummary rollback(TaskData taskData) throws TaskException {
        var summary = parseArtifactCleanupService.rollbackPreprocess(taskData);
        if (summary.hasFailures()) {
            log.warn("预处理阶段回滚存在失败项: fileId={}, summary={}",
                    taskData != null && taskData.getFileRecord() != null ? taskData.getFileRecord().getId() : null,
                    summary);
        }
        return summary;
    }

    /**
     * 根据异常类型确定预处理错误码
     */
    private TaskException.ErrorCode determinePreprocessErrorCode(Exception exception) {
        String message = exception.getMessage();
        if (message == null)
            message = "";

        String lowerMessage = message.toLowerCase();

        // 检查是否是TaskException，如果是则保持原有错误码
        if (exception instanceof TaskException) {
            return ((TaskException) exception).getErrorCode();
        }

        // 根据异常信息判断错误类型
        if (lowerMessage.contains("timeout") || lowerMessage.contains("time out")) {
            return TaskException.ErrorCode.PREPROCESS_TIMEOUT;
        }

        if (lowerMessage.contains("resource") || lowerMessage.contains("memory") ||
                lowerMessage.contains("disk") || lowerMessage.contains("space")) {
            return TaskException.ErrorCode.PREPROCESS_RESOURCE_ERROR;
        }

        // 默认使用通用预处理失败错误码
        return TaskException.ErrorCode.PREPROCESS_FAILED;
    }
}