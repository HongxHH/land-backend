package com.gov.landcheck.file.task.thread;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.gov.landcheck.file.config.FileProcessingProperties;
import com.gov.landcheck.file.dto.TaskStatusDTO;
import com.gov.landcheck.file.task.base.PrioritizedFutureTask;
import com.gov.landcheck.file.task.base.Task;
import com.gov.landcheck.file.task.base.TaskData;
import com.gov.landcheck.file.task.base.TaskPriority;
import com.gov.landcheck.file.task.base.TaskStageTrace;
import com.gov.landcheck.file.task.executor.PriorityThreadPoolExecutor;
import com.gov.landcheck.file.task.publisher.TaskResultPublisher;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * 任务线程池
 * 负责异步执行文件解析任务，使用 CompletableFuture 处理回调和优先级队列
 *
 * @author HongHong
 * @date 2023/3/2 23:29
 */
@Slf4j
@Component
public class TaskThreadPool {

    private static final int TASK_QUEUE_CAPACITY_FALLBACK = 512;

    private final PriorityThreadPoolExecutor priorityExecutor;
    private final int queueCapacity;
    private final Set<Task> taskSet;
    private final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

    /** 任务完成回调与 MQ 发布，避免使用 {@link java.util.concurrent.ForkJoinPool#commonPool()} */
    private final ExecutorService taskCallbackExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "task-callback");
        t.setDaemon(true);
        return t;
    });

    private final Map<String, Future<?>> taskFutures = new ConcurrentHashMap<>();
    private final Map<String, PrioritizedFutureTask> activeTasks = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private TaskResultPublisher taskResultPublisher;

    public TaskThreadPool(FileProcessingProperties fileProcessingProperties) {
        FileProcessingProperties.Pool pool = fileProcessingProperties != null
                ? fileProcessingProperties.getParsePool()
                : null;
        int corePoolSize = pool != null ? pool.getCoreSize() : 2;
        int maximumPoolSize = pool != null ? pool.getMaxSize() : 2;
        this.queueCapacity = pool != null ? pool.getQueueCapacity() : TASK_QUEUE_CAPACITY_FALLBACK;

        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("Task_Thread_%d")
                .build();

        priorityExecutor = new PriorityThreadPoolExecutor(corePoolSize, maximumPoolSize, 60L, TimeUnit.SECONDS,
                queueCapacity, threadFactory);
        taskSet = ConcurrentHashMap.newKeySet();
        if (threadMXBean.isThreadCpuTimeSupported() && !threadMXBean.isThreadCpuTimeEnabled()) {
            threadMXBean.setThreadCpuTimeEnabled(true);
        }
        log.info("TaskThreadPool 已初始化: core={}, max={}, queue={}", corePoolSize, maximumPoolSize, queueCapacity);
    }

    /**
     * 提交任务，支持任务取消和优先级设置
     */
    public void submit(Task task, String taskId, TaskPriority priority) {
        taskSet.add(task);

        if (taskId == null) {
            throw new IllegalArgumentException("taskId不能为空");
        }
        PrioritizedFutureTask prioritizedTask = new PrioritizedFutureTask(task, priority, taskId);
        activeTasks.put(taskId, prioritizedTask);
        try {
            priorityExecutor.execute(prioritizedTask);
            taskFutures.put(taskId, prioritizedTask);
        } catch (RejectedExecutionException e) {
            activeTasks.remove(taskId);
            taskSet.remove(task);
            log.warn("任务提交被拒绝，线程池可能已满或关闭: taskId={}, reason={}", taskId, e.getMessage());
            throw e;
        }

        CompletableFuture.runAsync(() -> {
            Throwable executionError = null;
            try {
                prioritizedTask.get();
                if (!task.isCancelled()) {
                    task.success();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executionError = e;
                task.failed(e);
                task.fallback();
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                executionError = cause != null ? cause : e;
                task.failed(executionError);
                task.fallback();
            } catch (Exception e) {
                executionError = e;
                task.failed(e);
                task.fallback();
            } finally {
                try {
                    if (taskResultPublisher != null) {
                        taskResultPublisher.publish(task, taskId, executionError);
                    }
                } catch (Exception e) {
                    log.warn("任务结果通知发送失败: taskId={}, error={}", taskId, e.getMessage());
                } finally {
                    cleanupTask(task, taskId);
                }
            }
        }, taskCallbackExecutor);

    }

    @PreDestroy
    public void shutdown() {
        log.info("正在关闭TaskThreadPool...");

        if (priorityExecutor != null && !priorityExecutor.isShutdown()) {
            priorityExecutor.shutdown();
            try {
                if (!priorityExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.warn("任务线程池未能在30秒内完成，强制关闭");
                    priorityExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.warn("等待任务线程池关闭时被中断，强制关闭");
                priorityExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        taskCallbackExecutor.shutdown();
        try {
            if (!taskCallbackExecutor.awaitTermination(15, TimeUnit.SECONDS)) {
                log.warn("任务回调线程池未能在15秒内完成，强制关闭");
                taskCallbackExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            taskCallbackExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("TaskThreadPool关闭完成");
    }

    /**
     * 取消任务
     */
    public boolean cancelTask(String taskId, String reason) {
        if (taskId == null) {
            log.warn("任务ID为空，无法取消任务");
            return false;
        }

        Future<?> future = taskFutures.get(taskId);
        PrioritizedFutureTask prioritizedTask = activeTasks.get(taskId);

        if (future == null || prioritizedTask == null) {
            log.warn("任务不存在或已完成: taskId={}", taskId);
            return false;
        }

        boolean cancelled = future.cancel(true);

        if (cancelled) {
            prioritizedTask.getTask().cancel(reason);
            cleanupTask(prioritizedTask.getTask(), taskId);
        } else {
            log.warn("任务取消失败，可能任务已完成: taskId={}", taskId);
        }
        log.info("任务已取消: taskId={}, reason={}", taskId, reason);
        return cancelled;
    }

    /**
     * 检查任务是否正在运行
     */
    public boolean isTaskRunning(String taskId) {
        if (taskId == null) {
            return false;
        }
        Future<?> future = taskFutures.get(taskId);
        return future != null && !future.isDone();
    }

    /**
     * 获取活跃任务ID列表
     */
    public List<String> getActiveTaskIds() {
        return new ArrayList<>(taskFutures.keySet());
    }

    private void cleanupTask(Task task, String taskId) {
        taskSet.remove(task);
        if (taskId != null) {
            taskFutures.remove(taskId);
            activeTasks.remove(taskId);
        }
    }

    /**
     * 解析线程池是否还能接受新任务（队列未满且未关闭）。
     */
    public boolean hasSubmissionCapacity() {
        if (priorityExecutor == null || priorityExecutor.isShutdown()) {
            return false;
        }
        return priorityExecutor.getQueue().remainingCapacity() > 0;
    }

    /**
     * 动态调整解析并行度（core 与 max 设为同一值）。
     */
    public synchronized void updateConcurrency(int concurrency) {
        updatePoolSize(concurrency, concurrency);
    }

    /**
     * 动态调整线程池参数。
     */
    public synchronized void updatePoolSize(int corePoolSize, int maximumPoolSize) {
        if (corePoolSize <= 0 || maximumPoolSize <= 0) {
            throw new IllegalArgumentException("corePoolSize和maximumPoolSize必须大于0");
        }
        if (corePoolSize > maximumPoolSize) {
            throw new IllegalArgumentException("corePoolSize不能大于maximumPoolSize");
        }

        int previousCorePoolSize = priorityExecutor.getCorePoolSize();
        int previousMaximumPoolSize = priorityExecutor.getMaximumPoolSize();

        if (maximumPoolSize > previousMaximumPoolSize) {
            priorityExecutor.setMaximumPoolSize(maximumPoolSize);
        }
        priorityExecutor.setCorePoolSize(corePoolSize);
        if (maximumPoolSize < previousMaximumPoolSize) {
            priorityExecutor.setMaximumPoolSize(maximumPoolSize);
        }

        if (corePoolSize > previousCorePoolSize) {
            priorityExecutor.prestartAllCoreThreads();
        }

        log.debug("线程池参数已更新: corePoolSize {} -> {}, maximumPoolSize {} -> {}",
                previousCorePoolSize, corePoolSize, previousMaximumPoolSize, maximumPoolSize);
    }

    public TaskStatusDTO getTaskStatus() {
        TaskStatusDTO.ThreadPoolStatus threadPoolStatus = TaskStatusDTO.ThreadPoolStatus.builder()
                .corePoolSize(priorityExecutor.getCorePoolSize())
                .maximumPoolSize(priorityExecutor.getMaximumPoolSize())
                .activeThreadCount(priorityExecutor.getActiveCount())
                .poolSize(priorityExecutor.getPoolSize())
                .queueCapacity(queueCapacity)
                .queueSize(priorityExecutor.getQueue().size())
                .completedTaskCount(priorityExecutor.getCompletedTaskCount())
                .isShutdown(priorityExecutor.isShutdown())
                .isTerminated(priorityExecutor.isTerminated())
                .build();

        List<TaskStatusDTO.RunningTaskInfo> runningTasks = getActiveTaskInfos();
        TaskStatusDTO.QueueTaskInfo queueTasks = getQueueTaskInfo();

        return TaskStatusDTO.builder()
                .threadPoolStatus(threadPoolStatus)
                .runningTasks(runningTasks)
                .queueTasks(queueTasks)
                .build();
    }

    public TaskStatusDTO.RunningTaskInfo getTaskDetail(String taskId) {
        if (taskId == null) {
            return null;
        }
        PrioritizedFutureTask prioritizedTask = activeTasks.get(taskId);
        if (prioritizedTask == null) {
            return null;
        }
        Set<String> queuedTaskIds = new HashSet<>();
        for (Runnable runnable : priorityExecutor.getQueue()) {
            if (runnable instanceof PrioritizedFutureTask prioritizedFutureTask) {
                queuedTaskIds.add(prioritizedFutureTask.getTaskId());
            }
        }
        return buildRunningTaskInfo(taskId, prioritizedTask, queuedTaskIds, System.currentTimeMillis());
    }

    private List<TaskStatusDTO.RunningTaskInfo> getActiveTaskInfos() {
        List<TaskStatusDTO.RunningTaskInfo> result = new ArrayList<>();
        Set<String> queuedTaskIds = new HashSet<>();
        long now = System.currentTimeMillis();
        for (Runnable runnable : priorityExecutor.getQueue()) {
            if (runnable instanceof PrioritizedFutureTask prioritizedFutureTask) {
                queuedTaskIds.add(prioritizedFutureTask.getTaskId());
            }
        }

        for (String taskId : activeTasks.keySet()) {
            PrioritizedFutureTask prioritizedTask = activeTasks.get(taskId);
            if (prioritizedTask != null) {
                result.add(buildRunningTaskInfo(taskId, prioritizedTask, queuedTaskIds, now));
            }
        }

        return result;
    }

    private TaskStatusDTO.RunningTaskInfo buildRunningTaskInfo(
            String taskId,
            PrioritizedFutureTask prioritizedTask,
            Set<String> queuedTaskIds,
            long now) {
        Task task = prioritizedTask.getTask();
        String taskName;
        String taskType;
        String priority;
        String status = queuedTaskIds.contains(taskId) ? "QUEUED" : "RUNNING";
        Long projectId = null;
        Long fileId = null;
        String fileName = null;
        Long parseJobId = null;
        boolean cancellable = true;
        Integer progress = null;
        String currentStageCode = null;
        String currentStageName = null;
        String currentStageStatus = null;
        String errorMessage = null;
        List<TaskStatusDTO.StageTraceInfo> stageTraces = null;
        String fileContextType = null;

        if (task instanceof ParseFileTask parseFileTask) {
            taskName = "文件解析任务";
            taskType = "FILE_PARSE";
            TaskData taskData = parseFileTask.getTaskData();
            if (taskData != null && taskData.getFileRecord() != null) {
                projectId = taskData.getFileRecord().getProjectId();
                fileId = taskData.getFileRecord().getId();
                fileName = taskData.getFileRecord().getOriginalName();
                if (taskData.getFileRecord().getFileContextType() != null) {
                    fileContextType = taskData.getFileRecord().getFileContextType().name();
                }
            }
            if (taskData != null && taskData.getParseJob() != null) {
                parseJobId = taskData.getParseJob().getId();
            }
            if (taskData != null) {
                progress = taskData.getProgress();
                currentStageCode = taskData.getCurrentStageCode();
                currentStageName = taskData.getCurrentStageName();
                currentStageStatus = taskData.getCurrentStageStatus();
                errorMessage = taskData.getErrorMessage();
                stageTraces = mapStageTraces(taskData.getStageTracesSnapshot());
            }
        } else {
            taskName = "未知任务";
            taskType = "UNKNOWN";
        }
        priority = prioritizedTask.getPriority().name();
        Long submittedAt = prioritizedTask.getSubmitEpochMillis();
        Long startedAt = task.getStartedAtEpochMs();
        Long waitingDurationMs = "QUEUED".equals(status)
                ? Math.max(0L, now - (submittedAt != null ? submittedAt : now))
                : 0L;
        Long runningDurationMs = "RUNNING".equals(status)
                ? Math.max(0L, now - (startedAt != null ? startedAt : (submittedAt != null ? submittedAt : now)))
                : 0L;
        Long threadCpuTimeMs = getThreadCpuTimeMs(task.getExecutingThreadId());

        return TaskStatusDTO.RunningTaskInfo.builder()
                .taskId(taskId)
                .taskName(taskName)
                .taskType(taskType)
                .status(status)
                .priority(priority)
                .projectId(projectId)
                .fileId(fileId)
                .fileName(fileName)
                .parseJobId(parseJobId)
                .submittedAt(submittedAt)
                .startedAt(startedAt)
                .waitingDurationMs(waitingDurationMs)
                .runningDurationMs(runningDurationMs)
                .threadCpuTimeMs(threadCpuTimeMs)
                .cancellable(cancellable)
                .progress(progress)
                .currentStageCode(currentStageCode)
                .currentStageName(currentStageName)
                .currentStageStatus(currentStageStatus)
                .errorMessage(errorMessage)
                .fileContextType(fileContextType)
                .stageTraces(stageTraces)
                .build();
    }

    private List<TaskStatusDTO.StageTraceInfo> mapStageTraces(List<TaskStageTrace> traces) {
        if (traces == null || traces.isEmpty()) {
            return new ArrayList<>();
        }
        List<TaskStatusDTO.StageTraceInfo> mapped = new ArrayList<>(traces.size());
        for (TaskStageTrace trace : traces) {
            mapped.add(TaskStatusDTO.StageTraceInfo.builder()
                    .stageCode(trace.getStageCode())
                    .stageName(trace.getStageName())
                    .status(trace.getStatus())
                    .startedAt(trace.getStartedAt())
                    .endedAt(trace.getEndedAt())
                    .durationMs(trace.getDurationMs())
                    .message(trace.getMessage())
                    .build());
        }
        return mapped;
    }

    private TaskStatusDTO.QueueTaskInfo getQueueTaskInfo() {
        int highPriorityCount = 0;
        int normalPriorityCount = 0;

        for (Object item : priorityExecutor.getQueue()) {
            if (item instanceof PrioritizedFutureTask prioritizedTask) {
                TaskPriority priority = prioritizedTask.getPriority();
                switch (priority) {
                    case HIGH -> highPriorityCount++;
                    case NORMAL -> normalPriorityCount++;
                }
            }
        }

        return TaskStatusDTO.QueueTaskInfo.builder()
                .queueSize(priorityExecutor.getQueue().size())
                .highPriorityCount(highPriorityCount)
                .normalPriorityCount(normalPriorityCount)
                .build();
    }

    private Long getThreadCpuTimeMs(Long threadId) {
        if (threadId == null || threadId <= 0) {
            return null;
        }
        if (!threadMXBean.isThreadCpuTimeSupported() || !threadMXBean.isThreadCpuTimeEnabled()) {
            return null;
        }
        long nanos = threadMXBean.getThreadCpuTime(threadId);
        if (nanos < 0) {
            return null;
        }
        return nanos / 1_000_000L;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public int getMaximumPoolSize() {
        return priorityExecutor.getMaximumPoolSize();
    }
}
