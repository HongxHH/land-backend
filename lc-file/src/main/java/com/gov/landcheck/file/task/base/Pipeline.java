package com.gov.landcheck.file.task.base;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.gov.landcheck.file.service.parse.ParseRollbackSummary;

import lombok.extern.slf4j.Slf4j;

/**
 * 命令管道
 * 按顺序编排并执行 Command 序列，支持 Builder 模式构建
 *
 * @author system
 * @date 2026/02/28
 */
@Slf4j
public class Pipeline {

    private final List<Command> commands;
    private final String pipelineName;

    Pipeline(String pipelineName, List<Command> commands) {
        this.pipelineName = pipelineName;
        this.commands = new ArrayList<>(commands);
    }

    /**
     * 创建管道构建器
     */
    public static PipelineBuilder of(String name) {
        return new PipelineBuilder(name);
    }

    /**
     * 创建包含指定命令的管道
     */
    public static Pipeline of(Command... commands) {
        return new Pipeline("DefaultPipeline", Arrays.asList(commands));
    }

    /**
     * 执行管道中的所有命令
     *
     * @param taskData 任务数据上下文
     * @throws TaskException 当任意命令执行失败时抛出
     */
    public void execute(TaskData taskData) throws TaskException {
        Long fileId = taskData.getFileRecord() != null ? taskData.getFileRecord().getId() : null;
        String taskId = taskData.getParseJob() != null ? taskData.getParseJob().getTaskId() : null;
        log.info("解析管道开始: pipeline={} stages={} fileId={} taskId={}",
                pipelineName, commands.size(), fileId, taskId);
        long pipelineStartMs = System.currentTimeMillis();

        for (int i = 0; i < commands.size(); i++) {
            Command command = commands.get(i);
            String commandInfo = String.format("[%d/%d] %s", i + 1, commands.size(), command.getName());
            int startProgress = Math.round((i * 100.0f) / commands.size());
            int endProgress = Math.round(((i + 1) * 100.0f) / commands.size());

            try {
                if (command.canSkip(taskData)) {
                    taskData.skipStage(command.getStage(), command.getName(), "条件跳过", endProgress);
                    log.info("解析阶段跳过: stage={} fileId={} taskId={} reason=条件跳过",
                            command.getStage(), fileId, taskId);
                    continue;
                }

                taskData.startStage(command.getStage(), command.getName(), null, startProgress);
                log.info("解析阶段开始: stage={} fileId={} taskId={} progress={}",
                        command.getStage(), fileId, taskId, startProgress);
                long startTime = System.currentTimeMillis();

                command.execute(taskData);
                taskData.completeStage(command.getStage(), null, endProgress);

                long duration = System.currentTimeMillis() - startTime;
                log.info("解析阶段完成: stage={} fileId={} taskId={} durationMs={} progress={}",
                        command.getStage(), fileId, taskId, duration, endProgress);

            } catch (TaskException e) {
                taskData.failCurrentStage(e.getMessage());
                log.error("解析阶段失败: stage={} fileId={} taskId={} error={}",
                        command.getStage(), fileId, taskId, e.getMessage());

                throw e;
            } catch (Exception e) {
                taskData.failCurrentStage(e.getMessage());
                log.error("解析阶段异常: stage={} fileId={} taskId={} error={}",
                        command.getStage(), fileId, taskId, e.getMessage(), e);

                throw new TaskException(
                        TaskException.ErrorCode.SYSTEM_ERROR,
                        command.getStage(),
                        taskData.getFileRecord() != null ? taskData.getFileRecord().getId() : null,
                        taskData.getParseJob() != null ? taskData.getParseJob().getId() : null,
                        String.format("命令执行异常: %s", command.getName()),
                        e);
            }
        }

        log.info("解析管道完成: pipeline={} fileId={} taskId={} durationMs={}",
                pipelineName, fileId, taskId, System.currentTimeMillis() - pipelineStartMs);
    }

    /**
     * 对管道中的全部命令按「后加入先回滚」顺序执行回滚。
     * 由 Task 层 fallback / cancel 触发；失败时汇总各阶段清理结果便于观测。
     */
    public ParseRollbackSummary rollbackAll(TaskData taskData) {
        ParseRollbackSummary summary = new ParseRollbackSummary();
        if (taskData == null || commands == null || commands.isEmpty()) {
            return summary;
        }
        log.warn("开始执行管道全量回滚: pipeline={}, commandCount={}", pipelineName, commands.size());
        for (int i = commands.size() - 1; i >= 0; i--) {
            Command cmd = commands.get(i);
            try {
                log.debug("回滚命令: name={}, stage={}", cmd.getName(), cmd.getStage());
                ParseRollbackSummary stageSummary = cmd.rollback(taskData);
                if (stageSummary != null) {
                    summary.merge(stageSummary);
                } else {
                    summary.recordSuccess(cmd.getStage() + ": ok");
                }
            } catch (Exception ex) {
                summary.recordFailure(cmd.getStage(), ex.getMessage());
                log.warn("命令回滚失败: name={}, stage={}, error={}",
                        cmd.getName(), cmd.getStage(), ex.getMessage(), ex);
            }
        }
        return summary;
    }

    /**
     * 获取管道中的命令列表（只读）
     */
    public List<Command> getCommands() {
        return new ArrayList<>(commands);
    }

    /**
     * 获取管道名称
     */
    public String getPipelineName() {
        return pipelineName;
    }
}
