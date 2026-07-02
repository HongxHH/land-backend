package com.gov.landcheck.file.task.base;

import com.gov.landcheck.file.task.result.TaskResultSummary;

/**
 * 任务接口 - 简化的统一抽象
 * 合并了Runnable、Cancellable和回调功能，支持优先级
 *
 * @author system
 * @date 2026/01/27
 */
public interface Task extends Runnable {

    /**
     * 获取任务唯一标识符
     */
    String getTaskId();

    /**
     * 检查任务是否已被取消
     */
    boolean isCancelled();

    /**
     * 取消任务
     */
    void cancel(String reason);

    /**
     * 检查任务是否应该停止执行
     */
    default boolean shouldStop() {
        return isCancelled();
    }

    /**
     * 检查任务取消状态，如果被取消则抛出异常
     */
    default void checkCancellation() {
        if (isCancelled()) {
            throw new TaskException(TaskException.ErrorCode.TASK_CANCELLED,"任务已被取消: " + getTaskId());
        }
    }

    /**
     * 任务执行成功的回调方法
     */
    default void success() {
        // 默认空实现
    }

    /**
     * 任务执行失败的回调方法
     */
    default void failed(Throwable throwable) {
        // 默认空实现
    }

    /**
     * 任务fallback的回调方法
     */
    default void fallback() {
        // 默认空实现
    }

    /**
     * 构建任务结果摘要（用于统一发布 MQ 通知）。
     */
    default TaskResultSummary buildResultSummary(Throwable throwable) {
        return TaskResultSummary.none();
    }

    /**
     * 获取任务开始执行时间（毫秒时间戳）
     */
    default Long getStartedAtEpochMs() {
        return null;
    }

    /**
     * 获取当前执行线程ID，若无则返回 null
     */
    default Long getExecutingThreadId() {
        return null;
    }

}
