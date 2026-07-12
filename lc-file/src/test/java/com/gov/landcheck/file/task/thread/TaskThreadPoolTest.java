package com.gov.landcheck.file.task.thread;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.gov.landcheck.file.config.FileProcessingProperties;
import com.gov.landcheck.file.task.base.Task;
import com.gov.landcheck.file.task.base.TaskPriority;

class TaskThreadPoolTest {

    private TaskThreadPool pool;

    @AfterEach
    void tearDown() {
        if (pool != null) {
            pool.shutdown();
        }
    }

    @Test
    void cancelRunningTaskKeepsTaskActiveUntilRunActuallyReturns() throws Exception {
        pool = createPool(1, 8);
        BlockingTask task = new BlockingTask("running-task");

        pool.submit(task, task.getTaskId(), TaskPriority.NORMAL);
        assertTrue(task.awaitStarted(Duration.ofSeconds(2)));

        assertTrue(pool.cancelTask(task.getTaskId(), "delete file"));

        assertTrue(task.isCancelled());
        assertTrue(pool.isTaskRunning(task.getTaskId()));

        task.release();
        waitUntilNotRunning(task.getTaskId(), Duration.ofSeconds(2));

        assertFalse(pool.isTaskRunning(task.getTaskId()));
    }

    @Test
    void cancelQueuedTaskRemovesItFromActiveTasksImmediately() throws Exception {
        pool = createPool(1, 8);
        BlockingTask runningTask = new BlockingTask("running-task");
        BlockingTask queuedTask = new BlockingTask("queued-task");

        pool.submit(runningTask, runningTask.getTaskId(), TaskPriority.NORMAL);
        assertTrue(runningTask.awaitStarted(Duration.ofSeconds(2)));
        pool.submit(queuedTask, queuedTask.getTaskId(), TaskPriority.NORMAL);

        assertTrue(pool.cancelTask(queuedTask.getTaskId(), "delete file"));

        assertTrue(queuedTask.isCancelled());
        assertFalse(pool.isTaskRunning(queuedTask.getTaskId()));
        assertFalse(queuedTask.awaitStarted(Duration.ofMillis(100)));

        runningTask.release();
        waitUntilNotRunning(runningTask.getTaskId(), Duration.ofSeconds(2));
    }

    private TaskThreadPool createPool(int concurrency, int queueCapacity) {
        FileProcessingProperties properties = new FileProcessingProperties();
        properties.setParsePool(new FileProcessingProperties.Pool(concurrency, concurrency, queueCapacity));
        return new TaskThreadPool(properties);
    }

    private void waitUntilNotRunning(String taskId, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (!pool.isTaskRunning(taskId)) {
                return;
            }
            Thread.sleep(10L);
        }
        assertFalse(pool.isTaskRunning(taskId), "task should stop running before timeout");
    }

    private static final class BlockingTask implements Task {

        private final String taskId;
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        private BlockingTask(String taskId) {
            this.taskId = taskId;
        }

        @Override
        public String getTaskId() {
            return taskId;
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }

        @Override
        public void cancel(String reason) {
            cancelled.set(true);
        }

        @Override
        public void run() {
            started.countDown();
            while (release.getCount() > 0) {
                try {
                    release.await(10L, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ignored) {
                    // Simulate parser code that does not immediately stop on interrupt.
                }
            }
        }

        private boolean awaitStarted(Duration timeout) throws InterruptedException {
            return started.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        private void release() {
            release.countDown();
        }
    }
}
