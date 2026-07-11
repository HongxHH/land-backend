package com.gov.landcheck.file.task.base;

/**
 * 命令模式中角色：抽象命令基类
 * 提供通用的命令实现模板，包含错误处理、日志记录等通用逻辑
 *
 * @author system
 * @date 2026/02/28
 */
public abstract class AbstractCommand implements Command {

    protected final String name;
    protected final String stage;

    protected AbstractCommand(String name, String stage) {
        this.name = name;
        this.stage = stage;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getStage() {
        return stage;
    }

    /**
     * 执行前的准备工作
     * 子类可以重写此方法进行特定的准备逻辑
     */
    protected void preExecute(TaskData taskData) throws TaskException {
        // 检查任务取消状态
        if (taskData.getTask() != null) {
            taskData.getTask().checkCancellation();
        }
    }

    /**
     * 执行后的清理工作
     * 子类可以重写此方法进行特定的清理逻辑
     */
    protected void postExecute(TaskData taskData) throws TaskException {
        // 默认空实现
    }

    /**
     * 执行命令的核心逻辑
     * 由子类实现具体的业务逻辑
     */
    protected abstract void doExecute(TaskData taskData) throws TaskException;

    @Override
    public final void execute(TaskData taskData) throws TaskException {
        try {
            preExecute(taskData);
            doExecute(taskData);
            postExecute(taskData);
        } catch (TaskException e) {
            throw e;
        } catch (Exception e) {
            // 将其他异常包装为TaskException，保持异常链
            throw new TaskException(
                TaskException.ErrorCode.SYSTEM_ERROR,
                stage,
                taskData.getFileRecord() != null ? taskData.getFileRecord().getId() : null,
                taskData.getParseJob() != null ? taskData.getParseJob().getId() : null,
                String.format("%s 执行失败: %s", name, e.getMessage()),
                e
            );
        }
    }
}