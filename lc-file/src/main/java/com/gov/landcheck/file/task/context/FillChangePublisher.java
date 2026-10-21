package com.gov.landcheck.file.task.context;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.gov.landcheck.core.config.cache.event.ProjectDataChangedEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * 回填完成后的提交后事件发布，供各文件类型模块共用。
 */
@Slf4j
@Component
public class FillChangePublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public FillChangePublisher(@Autowired(required = false) ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public void publish(ProjectDataChangedEvent event) {
        if (applicationEventPublisher == null || event == null || !event.hasAnyId()) {
            return;
        }
        runAfterCommit(() -> doPublish(event));
    }

    /**
     * 删除和回填回滚在事务内立即发布，不推迟到提交之后。
     */
    public void publishImmediately(ProjectDataChangedEvent event) {
        if (applicationEventPublisher == null || event == null || !event.hasAnyId()) {
            return;
        }
        doPublish(event);
    }

    public void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }

    private void doPublish(ProjectDataChangedEvent event) {
        try {
            applicationEventPublisher.publishEvent(event);
        } catch (Exception ex) {
            log.warn("Publish ProjectDataChangedEvent failed, event={}", event, ex);
        }
    }
}
