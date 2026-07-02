package com.gov.landcheck.file.task.publisher;

import com.gov.landcheck.file.task.base.Task;

/**
 * 统一任务结果发布器。
 */
public interface TaskResultPublisher {

    /**
     * 发布任务结果消息。
     */
    void publish(Task task, String taskId, Throwable throwable);
}

