package com.gov.landcheck.file.task.publisher.impl;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.gov.landcheck.core.config.mq.service.MessageProducerService;
import com.gov.landcheck.core.config.mq.MqRateLimiter;
import com.gov.landcheck.file.task.base.Task;
import com.gov.landcheck.file.task.publisher.TaskResultPublisher;
import com.gov.landcheck.file.task.result.TaskResultSummary;

import lombok.extern.slf4j.Slf4j;

/**
 * 基于 MQ 的统一任务结果发布器。
 */
@Slf4j
@Component
public class MqTaskResultPublisher implements TaskResultPublisher {

    @Autowired(required = false)
    private MessageProducerService messageProducerService;

    @Autowired(required = false)
    private MqRateLimiter mqRateLimiter;

    @Override
    public void publish(Task task, String taskId, Throwable throwable) {
        if (task == null || messageProducerService == null) {
            return;
        }
        Throwable root = unwrap(throwable);
        TaskResultSummary summary = task.buildResultSummary(root);
        if (summary == null || !summary.shouldPublish()) {
            return;
        }
        String rateLimitKey = summary.rateLimitKey();
        if (mqRateLimiter != null && rateLimitKey != null && !rateLimitKey.isBlank()
                && !mqRateLimiter.tryAcquire(rateLimitKey)) {
            log.debug("任务结果通知被限流丢弃: taskId={}, key={}", taskId, rateLimitKey);
            return;
        }
        messageProducerService.send(summary.topic(), summary.tag(), summary.key(), summary.payload());
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException || current instanceof ExecutionException) {
            if (current.getCause() == null) {
                break;
            }
            current = current.getCause();
        }
        return current;
    }
}
