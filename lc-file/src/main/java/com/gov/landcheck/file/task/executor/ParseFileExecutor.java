package com.gov.landcheck.file.task.executor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.file.config.FileProcessingConfig;
import com.gov.landcheck.file.processing.ProcessingConcurrencyGate;
import com.gov.landcheck.file.service.parse.ParseArtifactCleanupService;
import com.gov.landcheck.file.service.parse.ParseRollbackSummary;
import com.gov.landcheck.file.task.base.Command;
import com.gov.landcheck.file.task.base.Pipeline;
import com.gov.landcheck.file.task.base.PipelineBuilder;
import com.gov.landcheck.file.task.base.TaskData;
import com.gov.landcheck.file.task.base.TaskException;
import com.gov.landcheck.file.task.context.FileParseModuleRegistry;
import com.gov.landcheck.file.task.plan.ParsePipelinePlan.StageDef;
import com.gov.landcheck.file.task.processor.command.FillCommand;
import com.gov.landcheck.file.task.processor.command.OCRCommand;
import com.gov.landcheck.file.task.processor.command.ParseCommand;
import com.gov.landcheck.file.task.processor.command.PreprocessCommand;
import com.gov.landcheck.file.task.processor.command.ValidateCommand;

import lombok.extern.slf4j.Slf4j;

/**
 * 文件解析任务执行器 ：预处理 -> OCR -> 数据解析 -> 数据回填 -> 数据校验
 */
@Slf4j
@Service
public class ParseFileExecutor {

    @Autowired
    private ParseArtifactCleanupService parseArtifactCleanupService;

    private final ProcessingConcurrencyGate parsePipelineGate;
    private final FileParseModuleRegistry fileParseModuleRegistry;
    private final Map<String, Command> commandsByStage;

    public ParseFileExecutor(
            @Qualifier(FileProcessingConfig.PARSE_PIPELINE_GATE) ProcessingConcurrencyGate parsePipelineGate,
            FileParseModuleRegistry fileParseModuleRegistry,
            PreprocessCommand preprocessCommand,
            OCRCommand ocrCommand,
            ParseCommand parseCommand,
            FillCommand fillCommand,
            ValidateCommand validateCommand) {
        this.parsePipelineGate = parsePipelineGate;
        this.fileParseModuleRegistry = fileParseModuleRegistry;
        this.commandsByStage = Map.of(
                preprocessCommand.getStage(), preprocessCommand,
                ocrCommand.getStage(), ocrCommand,
                parseCommand.getStage(), parseCommand,
                fillCommand.getStage(), fillCommand,
                validateCommand.getStage(), validateCommand);
    }

    public void execute(TaskData taskData) throws Exception {
        if (taskData == null || taskData.getParseJob() == null || taskData.getFileRecord() == null) {
            Long fileRecordId = taskData != null && taskData.getFileRecord() != null
                    ? taskData.getFileRecord().getId()
                    : null;
            Long parseJobId = taskData != null && taskData.getParseJob() != null
                    ? taskData.getParseJob().getId()
                    : null;
            log.warn(
                    "ParseFileExecutor.execute 拒绝执行：缺少 taskData/parseJob/fileRecord 之一, fileRecordId={}, parseJobId={}",
                    fileRecordId, parseJobId);
            throw new IllegalArgumentException("解析管道执行需要完整的 taskData、parseJob 与 fileRecord");
        }
        if (!parsePipelineGate.tryAcquire()) {
            Long fileId = taskData.getFileRecord().getId();
            throw new TaskException(TaskException.ErrorCode.TASK_RESOURCE_ERROR,
                    "PIPELINE", fileId, null,
                    "解析管道并发已满，系统资源不足，稍后重试");
        }
        try {
            taskData.getParseJob().setFileContextType(taskData.getFileRecord().getFileContextType());
            Pipeline pipeline = buildPipeline(taskData);
            pipeline.execute(taskData);
        } finally {
            parsePipelineGate.release();
        }
    }

    public void rollback(TaskData taskData) {
        if (taskData == null || taskData.getParseJob() == null || taskData.getFileRecord() == null) {
            return;
        }
        taskData.getParseJob().setFileContextType(taskData.getFileRecord().getFileContextType());
        Pipeline pipeline = buildPipeline(taskData);
        ParseRollbackSummary summary = pipeline.rollbackAll(taskData);
        parseArtifactCleanupService.clearIntermediateReferences(
                taskData.getParseJob(), taskData.getFileRecord(), taskData, summary);
        if (summary.hasFailures()) {
            log.warn("解析管道回滚存在失败项: fileRecordId={}, parseJobId={}, summary={}",
                    taskData.getFileRecord().getId(), taskData.getParseJob().getId(), summary);
        } else {
            log.info("解析管道回滚完成: fileRecordId={}, parseJobId={}, summary={}",
                    taskData.getFileRecord().getId(), taskData.getParseJob().getId(), summary);
        }
    }

    private Pipeline buildPipeline(TaskData taskData) {
        FileContextType contextType = taskData != null && taskData.getFileRecord() != null
                ? taskData.getFileRecord().getFileContextType()
                : null;
        List<Command> commands = commandsFor(contextType, commandsByStage, fileParseModuleRegistry);
        PipelineBuilder builder = Pipeline.of("FileProcessingPipeline");
        for (Command command : commands) {
            builder.add(command);
        }
        return builder.build();
    }

    /**
     * 按 {@link FileParseModuleRegistry#stagesFor(FileContextType)} 的顺序取命令。
     * 空文件类型在这里失败，不走注册表对未注册类型的默认链路。
     */
    static List<Command> commandsFor(FileContextType contextType, Map<String, Command> commandsByStage,
            FileParseModuleRegistry registry) {
        if (contextType == null) {
            throw new IllegalStateException("解析管道缺少文件内容类型，无法确定执行阶段");
        }
        List<StageDef> stages = registry.stagesFor(contextType);
        List<Command> commands = new ArrayList<>(stages.size());
        for (StageDef stage : stages) {
            Command command = commandsByStage.get(stage.code());
            if (command == null) {
                throw new IllegalStateException(
                        "解析管道无法执行阶段 " + stage.code() + "，文件类型 " + contextType);
            }
            commands.add(command);
        }
        return commands;
    }
}
