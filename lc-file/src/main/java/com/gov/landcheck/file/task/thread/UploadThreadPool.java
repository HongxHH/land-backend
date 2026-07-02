package com.gov.landcheck.file.task.thread;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.gov.landcheck.file.config.FileProcessingProperties;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * 文件上传后处理专用线程池（缩略图、状态迁移、自动解析提交），与解析任务池隔离。
 */
@Slf4j
@Component
public class UploadThreadPool {

    private final ThreadPoolExecutor executor;
    private final ExecutorService callbackExecutor;
    private final ConcurrentHashMap<String, Future<?>> taskFutures = new ConcurrentHashMap<>();

    public UploadThreadPool(FileProcessingProperties fileProcessingProperties) {
        FileProcessingProperties.Pool pool = fileProcessingProperties.getUploadPool();
        int callbackThreads = Math.max(2, pool.getMaxSize());
        callbackExecutor = Executors.newFixedThreadPool(callbackThreads, r -> {
            Thread t = new Thread(r, "upload-callback");
            t.setDaemon(true);
            return t;
        });
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("Upload_Thread_%d")
                .build();
        BlockingQueue<Runnable> workQueue = pool.getQueueCapacity() == 0
                ? new LinkedBlockingQueue<>()
                : new LinkedBlockingQueue<>(pool.getQueueCapacity());
        executor = new ThreadPoolExecutor(
                pool.getCoreSize(),
                pool.getMaxSize(),
                60L,
                TimeUnit.SECONDS,
                workQueue,
                threadFactory);
        String queueLabel = pool.getQueueCapacity() == 0 ? "unbounded" : String.valueOf(pool.getQueueCapacity());
        log.info("UploadThreadPool 已初始化: core={}, max={}, queue={}",
                pool.getCoreSize(), pool.getMaxSize(), queueLabel);
    }

    public void submit(FileUploadPostProcessTask task) {
        String taskId = task.getTaskId();
        try {
            Future<?> future = executor.submit(task);
            taskFutures.put(taskId, future);
        } catch (RejectedExecutionException e) {
            log.warn("上传后处理任务提交被拒绝: taskId={}, reason={}", taskId, e.getMessage());
            throw e;
        }
        CompletableFuture.runAsync(() -> {
            Future<?> future = taskFutures.get(taskId);
            if (future == null) {
                return;
            }
            try {
                future.get();
                task.onSuccess();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                task.onFailure(e);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                task.onFailure(cause);
            } catch (Exception e) {
                task.onFailure(e);
            } finally {
                taskFutures.remove(taskId);
            }
        }, callbackExecutor);
    }

    @PreDestroy
    public void shutdown() {
        log.info("正在关闭 UploadThreadPool...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        callbackExecutor.shutdown();
        try {
            if (!callbackExecutor.awaitTermination(15, TimeUnit.SECONDS)) {
                callbackExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            callbackExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("UploadThreadPool 关闭完成");
    }
}
