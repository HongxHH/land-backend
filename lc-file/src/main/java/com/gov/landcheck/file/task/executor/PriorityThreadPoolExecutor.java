package com.gov.landcheck.file.task.executor;

import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.gov.landcheck.file.task.base.BoundedPriorityBlockingQueue;
import com.gov.landcheck.file.task.base.PrioritizedFutureTask;

import lombok.extern.slf4j.Slf4j;

/**
 * 支持优先级的线程池执行器
 * 使用带 Comparator 的优先级队列，由 PrioritizedFutureTask 提供排序
 *
 * @author System
 * @date 2026/01/26
 */
@Slf4j
public class PriorityThreadPoolExecutor extends ThreadPoolExecutor {

    private static final Comparator<Runnable> PRIORITY_COMPARATOR = (a, b) -> {
        boolean pa = a instanceof PrioritizedFutureTask;
        boolean pb = b instanceof PrioritizedFutureTask;
        if (pa && pb) {
            return ((PrioritizedFutureTask) a).compareTo((PrioritizedFutureTask) b);
        }
        if (pa != pb) {
            return pa ? -1 : 1;
        }
        int c = System.identityHashCode(a) - System.identityHashCode(b);
        if (c != 0) {
            return c;
        }
        return Integer.compare(Objects.hashCode(a), Objects.hashCode(b));
    };

    public PriorityThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime,
            TimeUnit unit, int queueCapacity) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit,
                new BoundedPriorityBlockingQueue<>(queueCapacity, PRIORITY_COMPARATOR));
        this.allowCoreThreadTimeOut(true);
    }

    public PriorityThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime,
            TimeUnit unit, BlockingQueue<Runnable> workQueue,
            ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
    }

    public PriorityThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime,
            TimeUnit unit, int queueCapacity, ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit,
                new BoundedPriorityBlockingQueue<>(queueCapacity, PRIORITY_COMPARATOR), threadFactory);
        this.allowCoreThreadTimeOut(true);
    }
}
