package com.gov.landcheck.file.task.processor.command;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.file.service.ParseJobUpdateService;
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
                    taskData
            );

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
                                fileRecord.getId(), taskData.getParseJob() != null ? taskData.getParseJob().getId() : null,
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

    /**
     * 预处理阶段回滚：
     * - 删除本次预处理产生的预处理文件（如仍然存在）
     * - 清空 FileRecord 与 TaskData 中的 preprocessGridfsId，避免残留无效引用
     */
    @Override
    public void rollback(TaskData taskData) throws TaskException {
        if (taskData == null || taskData.getFileRecord() == null) {
            return;
        }

        FileRecord fileRecord = taskData.getFileRecord();
        String preprocessGridfsId = taskData.getPreprocessGridfsId();

        // 如果 TaskData 中没有记录，则退回到 FileRecord 中的值
        if (!StringUtils.hasText(preprocessGridfsId)) {
            preprocessGridfsId = fileRecord.getPreprocessGridfsId();
        }

        if (!StringUtils.hasText(preprocessGridfsId)) {
            log.debug("预处理阶段回滚：未发现需要删除的预处理文件, fileId={}", fileRecord.getId());
            return;
        }


        // 复用 PdfPreReceiver 的删除逻辑，确保与预处理实现保持一致
        pdfPreprocessor.deleteExistingPreprocessResult(preprocessGridfsId);
        log.debug("预处理阶段回滚：已删除预处理文件, fileId={}, gridfsId={}",
                fileRecord.getId(), preprocessGridfsId);


        // 清理内存与数据库中的引用，避免后续逻辑依赖无效的 GridFS ID
        fileRecord.setPreprocessGridfsId(null);
        taskData.setPreprocessGridfsId(null);
        try {
            mongoTemplate.save(fileRecord);
        } catch (Exception ex) {
            log.warn("预处理阶段回滚：更新文件记录失败, fileId={}, error={}",
                    fileRecord.getId(), ex.getMessage());
        }
    }

    /**
     * 根据异常类型确定预处理错误码
     */
    private TaskException.ErrorCode determinePreprocessErrorCode(Exception exception) {
        String message = exception.getMessage();
        if (message == null) message = "";

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