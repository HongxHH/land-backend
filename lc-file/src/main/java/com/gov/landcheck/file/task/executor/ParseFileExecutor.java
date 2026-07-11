package com.gov.landcheck.file.task.executor;

import java.util.ArrayList;
import java.util.List;

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
import com.gov.landcheck.file.task.processor.command.FillCommand;
import com.gov.landcheck.file.task.processor.command.OCRCommand;
import com.gov.landcheck.file.task.processor.command.ParseCommand;
import com.gov.landcheck.file.task.processor.command.PreprocessCommand;
import com.gov.landcheck.file.task.processor.command.ValidateCommand;
import com.gov.landcheck.file.task.plan.ParsePipelinePlan;

import lombok.extern.slf4j.Slf4j;

/**
 * 任务执行器
 * 执行完整的文件解析流程：预处理 -> OCR -> 数据解析 -> 数据回填 -> 数据校验
 */
@Slf4j
@Service
public class ParseFileExecutor {

    @Autowired
    private PreprocessCommand preprocessCommand;

    @Autowired
    private OCRCommand ocrCommand;

    @Autowired
    private ParseCommand parseCommand;

    @Autowired
    private FillCommand fillCommand;

    @Autowired
    private ValidateCommand validateCommand;

    @Autowired
    private ParseArtifactCleanupService parseArtifactCleanupService;

    private final ProcessingConcurrencyGate parsePipelineGate;

    public ParseFileExecutor(
            @Qualifier(FileProcessingConfig.PARSE_PIPELINE_GATE) ProcessingConcurrencyGate parsePipelineGate) {
        this.parsePipelineGate = parsePipelineGate;
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
        List<Command> commands = resolveCommands(contextType);
        PipelineBuilder builder = Pipeline.of("FileProcessingPipeline");
        for (Command command : commands) {
            builder.add(command);
        }
        return builder.build();
    }

    /**
     * 按文件内容类型动态拼装解析链路：
     * - 实测：全链路（含校验）
     * - 合同/规划复核：不做校验
     * - 项目方汇总：直达解析回填（跳过预处理/OCR）
     * <p>
     * 阶段顺序需与 {@link ParsePipelinePlan#stagesFor(FileContextType)} 保持一致。
     * </p>
     */
    private List<Command> resolveCommands(FileContextType contextType) {
        List<Command> commands = new ArrayList<>();
        switch (contextType) {
            case PROJECT_PARTY_SURVEY_SUMMARY -> {
                commands.add(parseCommand);
                commands.add(fillCommand);
            }
            case SURVEY_REPORT -> {
                commands.add(preprocessCommand);
                commands.add(ocrCommand);
                commands.add(parseCommand);
                commands.add(fillCommand);
                commands.add(validateCommand);
            }
            default -> {
                commands.add(preprocessCommand);
                commands.add(ocrCommand);
                commands.add(parseCommand);
                commands.add(fillCommand);
            }
        }
        return commands;
    }
}
