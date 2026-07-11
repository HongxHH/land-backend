package com.gov.landcheck.file.task.base;

import com.gov.landcheck.file.service.parse.ParseRollbackSummary;

/**
 * 命令接口 - 命令模式核心抽象
 * 每个命令代表任务处理管道中的一个处理阶段
 *
 * @author system
 * @date 2026/01/27
 */
public interface Command {

    /**
     * 执行命令
     *
     * @param taskData 任务数据上下文
     * @throws TaskException 当命令执行失败时抛出
     */
    void execute(TaskData taskData) throws TaskException;

    /**
     * 获取命令名称，用于日志记录和监控
     */
    String getName();

    /**
     * 获取命令执行的阶段标识
     */
    String getStage();

    /**
     * 回滚当前命令在本次任务执行中产生的所有副作用。
     * 
     * @param taskData 任务数据上下文
     * @throws TaskException 回滚过程中发生的业务异常
     */
    default ParseRollbackSummary rollback(TaskData taskData) throws TaskException {
        return new ParseRollbackSummary();
    }

    /**
     * 检查命令是否可以跳过执行
     * 用于条件性执行某些命令
     *
     * @param taskData 任务数据上下文
     * @return true表示可以跳过，false表示必须执行
     */
    default boolean canSkip(TaskData taskData) {
        return false;
    }
}