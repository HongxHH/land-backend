package com.gov.landcheck.file.task.processor.command;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.file.service.ParseJobUpdateService;
import com.gov.landcheck.file.service.parse.ParseArtifactCleanupService;
import com.gov.landcheck.file.service.parse.ParseFillSnapshotService;
import com.gov.landcheck.file.service.parse.ParseRollbackSummary;
import com.gov.landcheck.file.task.base.AbstractCommand;
import com.gov.landcheck.file.task.base.TaskData;
import com.gov.landcheck.file.task.base.TaskException;
import com.gov.landcheck.file.task.base.TaskFailureClassifier;
import com.gov.landcheck.file.task.context.FileParseModule;
import com.gov.landcheck.file.task.context.FileParseModuleRegistry;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * 数据回填命令 - 执行数据回填操作
 */
@Slf4j
@Component
public class FillCommand extends AbstractCommand {

    /** 事务只罩写库，不罩 OCR；30 秒超时。 */
    private static final int FILL_TX_TIMEOUT_SECONDS = 30;

    @Resource
    private FileParseModuleRegistry fileParseModuleRegistry;

    @Resource
    private ParseJobUpdateService parseJobUpdateService;

    @Resource
    private ParseArtifactCleanupService parseArtifactCleanupService;

    @Resource
    private ParseFillSnapshotService parseFillSnapshotService;

    @Resource
    private PlatformTransactionManager transactionManager;

    public FillCommand() {
        super("数据回填", "FILL");
    }

    @Override
    public boolean canSkip(TaskData taskData) {
        if (fileParseModuleRegistry == null || taskData == null || taskData.getFileRecord() == null) {
            return false;
        }
        FileParseModule module = fileParseModuleRegistry.find(taskData.getFileRecord().getFileContextType());
        return module != null && module.skipFill(taskData);
    }

    @Override
    protected void doExecute(TaskData taskData) throws TaskException {
        log.debug("开始数据回填: fileId={}", taskData.getFileRecord().getId());

        taskData.setFillCommandEntered(true);

        parseJobUpdateService.updateFillStarted(taskData.getParseJob());
        parseFillSnapshotService.captureIfAbsent(taskData);

        try {
            TransactionTemplate tpl = new TransactionTemplate(transactionManager);
            tpl.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
            tpl.setTimeout(FILL_TX_TIMEOUT_SECONDS);
            tpl.executeWithoutResult(status -> {
                try {
                    fillByModule(taskData);
                } catch (RuntimeException ex) {
                    throw ex;
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
            });
            parseJobUpdateService.updateFillCompleted(taskData.getParseJob(), null);
            log.debug("数据回填完成: fileId={}", taskData.getFileRecord().getId());

        } catch (Exception e) {
            Exception classified = unwrapFillException(e);
            TaskException.ErrorCode errorCode = TaskFailureClassifier.classifyFillFailure(classified);
            parseJobUpdateService.updateFillFailed(taskData.getParseJob(), classified.getMessage());

            throw new TaskException(
                    errorCode,
                    getStage(),
                    taskData.getFileRecord().getId(),
                    taskData.getParseJob() != null ? taskData.getParseJob().getId() : null,
                    "数据回填失败: " + classified.getMessage(),
                    classified);
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

    private void fillByModule(TaskData taskData) throws Exception {
        FileContextType type = taskData.getFileRecord().getFileContextType();
        if (type == null) {
            return;
        }
        FileParseModule module = fileParseModuleRegistry.find(type);
        if (module == null) {
            log.warn("数据回填跳过：未注册的文件类型 handler, fileRecordId={}, fileContextType={}",
                    taskData.getFileRecord().getId(), type);
            return;
        }
        module.fill(taskData);
    }

    private static Exception unwrapFillException(Exception e) {
        Throwable current = e;
        if (current instanceof IllegalStateException && current.getCause() instanceof Exception cause) {
            current = cause;
        }
        if (current instanceof Exception ex) {
            return ex;
        }
        return e;
    }
}
