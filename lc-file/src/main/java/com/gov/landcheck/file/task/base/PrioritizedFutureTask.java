package com.gov.landcheck.file.task.base;

import java.util.concurrent.FutureTask;

import lombok.Getter;

/**
 * 带优先级元数据的 FutureTask。
 * 用于让线程池队列能感知任务优先级与提交顺序。
 */
@Getter
public class PrioritizedFutureTask extends FutureTask<Void> implements Comparable<PrioritizedFutureTask> {

    private final Task task;
    private final TaskPriority priority;
    private final long submitTime;
    private final long submitEpochMillis;
    private final String taskId;

    public PrioritizedFutureTask(Task task, TaskPriority priority, String taskId) {
        super(task::run, null);
        this.task = task;
        this.priority = priority;
        this.taskId = taskId;
        this.submitTime = System.nanoTime();
        this.submitEpochMillis = System.currentTimeMillis();
    }

    @Override
    public int compareTo(PrioritizedFutureTask other) {
        int priorityCompare = Integer.compare(this.priority.getValue(), other.priority.getValue());
        if (priorityCompare != 0) {
            return priorityCompare;
        }
        return Long.compare(this.submitTime, other.submitTime);
    }
}
