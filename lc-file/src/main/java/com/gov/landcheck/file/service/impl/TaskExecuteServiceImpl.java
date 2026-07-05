package com.gov.landcheck.file.service.impl;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.gov.landcheck.core.audit.OperatorContext;
import com.gov.landcheck.core.bo.dto.SystemRuntimeStatusDTO;
import com.gov.landcheck.core.bo.dto.ThreadPoolResizeDTO;
import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.ParseJob;
import com.gov.landcheck.core.enums.FileStateEnum;
import com.gov.landcheck.core.enums.ParseJobStateEnum;
import com.gov.landcheck.file.dto.TaskStatusDTO;
import com.gov.landcheck.file.service.ITaskExecuteService;
import com.gov.landcheck.file.service.ParseConcurrencyService;
import com.gov.landcheck.file.service.ParseProgressAssembler;
import com.gov.landcheck.file.service.UploadRecordService;
import com.gov.landcheck.file.service.parse.AutoParseSubmissionService;
import com.gov.landcheck.file.service.parse.ParseArtifactCleanupService;
import com.gov.landcheck.file.service.parse.ParseJobStageHelper;
import com.gov.landcheck.file.service.parse.ParseRollbackSummary;
import com.gov.landcheck.file.task.base.TaskData;
import com.gov.landcheck.file.task.base.TaskPriority;
import com.gov.landcheck.file.task.executor.ParseFileExecutor;
import com.gov.landcheck.file.task.thread.FileUploadPostProcessTask;
import com.gov.landcheck.file.task.thread.ParseFileTask;
import com.gov.landcheck.file.task.thread.TaskThreadPool;
import com.gov.landcheck.file.task.thread.UploadThreadPool;
import com.gov.landcheck.file.utils.GridFSUtils;
import com.gov.landcheck.file.utils.PdfProcessor;
import com.sun.management.OperatingSystemMXBean;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class TaskExecuteServiceImpl implements ITaskExecuteService {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private TaskThreadPool taskThreadPool;

    @Autowired
    private ParseFileExecutor parseFileExecutor;

    @Autowired
    private UploadThreadPool uploadThreadPool;

    @Autowired
    private UploadRecordService uploadRecordService;

    @Autowired
    private PdfProcessor pdfProcessor;

    @Autowired
    private GridFSUtils gridFSUtils;

    @Autowired(required = false)
    private DataSource dataSource;

    @Autowired
    private AutoParseSubmissionService autoParseSubmissionService;

    @Autowired
    private ParseProgressAssembler parseProgressAssembler;

    @Autowired
    private ParseArtifactCleanupService parseArtifactCleanupService;

    @Autowired
    private ParseConcurrencyService parseConcurrencyService;

    @Override
    public String executeParseTask(FileRecord fileRecord, FileStateEnum rollbackFileStateIfSubmitFails) {
        ParseJob parseJob = null;
        try {
            parseJob = new ParseJob();
            parseJob.setFileRecordId(fileRecord.getId());
            parseJob.setJobStatus(ParseJobStateEnum.PENDING);
            parseJob.setAttemptCount(0);
            parseJob.setCreatedBy(OperatorContext.getOperatorIdOrDefault(0L));
            parseJob.setFileContextType(fileRecord.getFileContextType());
            String taskId = UUID.randomUUID().toString();
            parseJob.setTaskId(taskId);

            TaskData taskData = new TaskData();
            taskData.setFileRecord(fileRecord);
            taskData.setParseJob(parseJob);
            taskData.updateProgress(0);
            taskData.setStartTime(System.currentTimeMillis());

            parseJob.preSave();
            mongoTemplate.save(parseJob);
            fileRecord.setParseJobId(parseJob.getId());
            mongoTemplate.save(fileRecord);

            ParseFileTask parseFileTask = new ParseFileTask(taskId, taskData, parseFileExecutor);
            taskThreadPool.submit(parseFileTask, taskId, TaskPriority.NORMAL);

            log.debug("文件解析任务已提交: fileId={}, parseJobId={}, taskId={}", fileRecord.getId(), parseJob.getId(), taskId);

            return taskId;
        } catch (Exception e) {
            if (parseJob != null && parseJob.getId() != null) {
                try {
                    mongoTemplate.remove(parseJob);
                } catch (Exception ex) {
                    log.warn("移除未成功提交的ParseJob失败: parseJobId={}, error={}", parseJob.getId(), ex.getMessage());
                }
            }
            FileStateEnum rollback = rollbackFileStateIfSubmitFails != null
                    ? rollbackFileStateIfSubmitFails
                    : FileStateEnum.WAITING_PARSE;
            Query rollbackQuery = new Query(Criteria.where("_id").is(fileRecord.getId())
                    .and("file_state").is(FileStateEnum.PENDING));
            Update rollbackUpdate = new Update()
                    .set("file_state", rollback)
                    .unset("parse_job_id")
                    .set("update_time", LocalDateTime.now());
            mongoTemplate.updateFirst(rollbackQuery, rollbackUpdate, FileRecord.class);
            fileRecord.setFileState(rollback);
            fileRecord.setParseJobId(null);
            throw new RuntimeException("创建文件解析任务失败", e);
        }
    }

    @Override
    public String retryParseTask(ParseJob existingParseJob, FileRecord fileRecord) {
        try {
            ParseJob latest = mongoTemplate.findById(existingParseJob.getId(), ParseJob.class);
            if (latest != null && latest.isCancelRequested()) {
                log.info("跳过重试（任务已取消）: fileId={}, parseJobId={}",
                        fileRecord.getId(), existingParseJob.getId());
                return existingParseJob.getTaskId();
            }
            log.debug("开始重试文件解析任务: fileId={}, parseJobId={}, attempt={}",
                    fileRecord.getId(), existingParseJob.getId(), existingParseJob.getAttemptCount());

            // 使用现有的ParseJob
            String taskId = existingParseJob.getTaskId();
            existingParseJob.setFileContextType(fileRecord.getFileContextType());

            // 创建 TaskData
            TaskData taskData = new TaskData();
            taskData.setFileRecord(fileRecord);
            taskData.setParseJob(existingParseJob);
            taskData.updateProgress(0);
            taskData.setStartTime(System.currentTimeMillis());

            // 确保文件记录指向当前重试的 parse_job
            fileRecord.setParseJobId(existingParseJob.getId());
            if (fileRecord.getFileState() != FileStateEnum.PENDING) {
                fileRecord.setFileState(FileStateEnum.PENDING);
            }
            mongoTemplate.save(fileRecord);

            // 提交异步解析任务 - 使用高优先级（重试任务优先）
            ParseFileTask parseFileTask = new ParseFileTask(taskId, taskData, parseFileExecutor);
            taskThreadPool.submit(parseFileTask, taskId, TaskPriority.HIGH);

            log.debug("文件解析重试任务已提交: fileId={}, parseJobId={}, taskId={}, attempt={}",
                    fileRecord.getId(), existingParseJob.getId(), taskId, existingParseJob.getAttemptCount());

            return taskId;
        } catch (Exception e) {
            throw new RuntimeException("重试文件解析任务失败", e);
        }
    }

    @Override

    public String executeFileUploadPostProcess(String fileId, Long operatorId, String operatorName) {
        String taskId = UUID.randomUUID().toString();
        FileUploadPostProcessTask task = new FileUploadPostProcessTask(
                taskId, fileId, operatorId, operatorName,
                mongoTemplate, uploadRecordService, pdfProcessor, gridFSUtils,
                autoParseSubmissionService);
        try {
            uploadThreadPool.submit(task);
        } catch (RejectedExecutionException e) {
            throw new RuntimeException("上传后处理线程池已满，请稍后重试", e);
        }
        log.debug("文件上传后处理任务已提交: fileId={}, taskId={}", fileId, taskId);
        return taskId;
    }

    @Override
    public boolean cancelParseTask(String taskId, String reason) {
        return taskThreadPool.cancelTask(taskId, reason);
    }

    @Override
    public void rollbackParseJob(ParseJob parseJob, FileRecord fileRecord) {
        if (parseJob == null || fileRecord == null) {
            return;
        }
        ParseJob latest = mongoTemplate.findById(parseJob.getId(), ParseJob.class);
        if (latest != null) {
            parseJob = latest;
        }
        TaskData taskData = ParseJobStageHelper.taskDataForOfflineRollback(parseJob, fileRecord);
        ParseRollbackSummary summary = parseArtifactCleanupService.rollbackAllStages(parseJob, fileRecord, taskData);
        if (summary.hasFailures()) {
            log.warn("离线解析任务回滚存在失败项: fileRecordId={}, parseJobId={}, summary={}",
                    fileRecord.getId(), parseJob.getId(), summary);
        } else {
            log.info("已回滚未运行解析任务的中间数据: fileRecordId={}, parseJobId={}, summary={}",
                    fileRecord.getId(), parseJob.getId(), summary);
        }
    }

    @Override
    public boolean isTaskRunning(String taskId) {
        return taskThreadPool.isTaskRunning(taskId);
    }

    @Override
    public boolean awaitTaskIdle(String taskId, long timeoutMs) {
        if (taskId == null || taskId.isBlank()) {
            return true;
        }
        long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMs);
        while (System.currentTimeMillis() < deadline) {
            if (!isTaskRunning(taskId)) {
                return true;
            }
            try {
                Thread.sleep(200L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return !isTaskRunning(taskId);
            }
        }
        return !isTaskRunning(taskId);
    }

    /**
     * 获取线程池整体任务状态
     */
    @Override
    public TaskStatusDTO getTaskStatus() {
        TaskStatusDTO status = taskThreadPool.getTaskStatus();
        if (status != null) {
            parseConcurrencyService.enrichThreadPoolStatus(status.getThreadPoolStatus());
        }
        return status;
    }

    /**
     * 获取线程池单个任务详情
     */
    @Override
    public TaskStatusDTO.RunningTaskInfo getTaskDetail(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId不能为空");
        }
        TaskStatusDTO.RunningTaskInfo fromPool = taskThreadPool.getTaskDetail(taskId);
        ParseJob job = null;
        if (fromPool != null && fromPool.getParseJobId() != null) {
            job = mongoTemplate.findById(fromPool.getParseJobId(), ParseJob.class);
        }
        if (fromPool != null) {
            return parseProgressAssembler.enrichFromActiveTask(fromPool, job);
        }
        job = mongoTemplate.findOne(Query.query(Criteria.where("task_id").is(taskId)), ParseJob.class);
        if (job != null) {
            FileRecord fileRecord = mongoTemplate.findById(job.getFileRecordId(), FileRecord.class);
            return parseProgressAssembler.fromParseJob(job, fileRecord);
        }
        return null;
    }

    @Override
    public TaskStatusDTO.RunningTaskInfo getParseJobFlowDetail(Long parseJobId) {
        if (parseJobId == null) {
            throw new IllegalArgumentException("parseJobId不能为空");
        }
        ParseJob job = mongoTemplate.findById(parseJobId, ParseJob.class);
        if (job == null) {
            return null;
        }
        FileRecord fileRecord = mongoTemplate.findById(job.getFileRecordId(), FileRecord.class);
        return parseProgressAssembler.fromParseJob(job, fileRecord);
    }

    /**
     * 取消任务
     */
    @Override
    public boolean cancelTaskByTaskId(String taskId, String reason) {
        return cancelParseTask(taskId, reason);
    }

    /**
     * 获取系统运行状态
     */
    @Override
    public SystemRuntimeStatusDTO getSystemRuntimeStatus() {
        OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

        MemoryUsage heap = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeap = memoryMXBean.getNonHeapMemoryUsage();

        return SystemRuntimeStatusDTO.builder()
                .system(SystemRuntimeStatusDTO.SystemMetrics.builder()
                        .cpuLoadPercent(toPercent(osBean.getCpuLoad()))
                        .totalMemoryBytes(osBean.getTotalMemorySize())
                        .freeMemoryBytes(osBean.getFreeMemorySize())
                        .build())
                .memory(SystemRuntimeStatusDTO.RuntimeMemoryMetrics.builder()
                        .heapUsedBytes(heap.getUsed())
                        .heapMaxBytes(heap.getMax())
                        .nonHeapUsedBytes(nonHeap.getUsed())
                        .build())
                .thread(SystemRuntimeStatusDTO.ThreadMetrics.builder()
                        .liveThreadCount(threadMXBean.getThreadCount())
                        .daemonThreadCount(threadMXBean.getDaemonThreadCount())
                        .peakThreadCount(threadMXBean.getPeakThreadCount())
                        .build())
                .dataSource(extractDataSourceMetrics())
                .gpu(extractGpuMetrics())
                .build();
    }

    /**
     * 更新解析并行度 N（线程池 core/max 与 parse-pipeline gate 联动）。
     */
    @Override
    public void updateTaskPoolSize(ThreadPoolResizeDTO resizeDTO) {
        Objects.requireNonNull(resizeDTO, "resizeDTO不能为空");
        parseConcurrencyService.updateConcurrency(resizeDTO.getParseConcurrency());
    }

    private double toPercent(double ratio) {
        if (ratio < 0) {
            return 0D;
        }
        return Math.round(ratio * 10000D) / 100D;
    }

    private SystemRuntimeStatusDTO.DataSourceMetrics extractDataSourceMetrics() {
        if (dataSource == null) {
            return SystemRuntimeStatusDTO.DataSourceMetrics.builder().poolName("N/A").build();
        }
        try {
            Class<?> dsClass = dataSource.getClass();
            if (!dsClass.getName().contains("Hikari")) {
                return SystemRuntimeStatusDTO.DataSourceMetrics.builder()
                        .poolName(dsClass.getSimpleName())
                        .build();
            }
            Object poolBean = dsClass.getMethod("getHikariPoolMXBean").invoke(dataSource);
            Object poolName = dsClass.getMethod("getPoolName").invoke(dataSource);
            Object maxPoolSize = dsClass.getMethod("getMaximumPoolSize").invoke(dataSource);

            Integer active = (Integer) poolBean.getClass().getMethod("getActiveConnections").invoke(poolBean);
            Integer idle = (Integer) poolBean.getClass().getMethod("getIdleConnections").invoke(poolBean);
            Integer total = (Integer) poolBean.getClass().getMethod("getTotalConnections").invoke(poolBean);
            Integer waiting = (Integer) poolBean.getClass().getMethod("getThreadsAwaitingConnection").invoke(poolBean);

            return SystemRuntimeStatusDTO.DataSourceMetrics.builder()
                    .poolName(String.valueOf(poolName))
                    .activeConnections(active)
                    .idleConnections(idle)
                    .totalConnections(total)
                    .waitingThreads(waiting)
                    .maxPoolSize((Integer) maxPoolSize)
                    .build();
        } catch (Exception e) {
            return SystemRuntimeStatusDTO.DataSourceMetrics.builder()
                    .poolName(dataSource.getClass().getSimpleName())
                    .build();
        }
    }

    private SystemRuntimeStatusDTO.GpuMetrics extractGpuMetrics() {
        Process process = null;
        try {
            List<String> command = Arrays.asList(
                    "nvidia-smi",
                    "--query-gpu=utilization.gpu,memory.used,memory.total",
                    "--format=csv,noheader,nounits");
            process = new ProcessBuilder(command).start();
            boolean finished = process.waitFor(1200, TimeUnit.MILLISECONDS);
            if (!finished || process.exitValue() != 0) {
                return SystemRuntimeStatusDTO.GpuMetrics.builder()
                        .supported(false)
                        .message("nvidia-smi 不可用")
                        .build();
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line == null || line.isBlank()) {
                    return SystemRuntimeStatusDTO.GpuMetrics.builder()
                            .supported(false)
                            .message("未检测到GPU信息")
                            .build();
                }
                String[] parts = line.split(",");
                if (parts.length < 3) {
                    return SystemRuntimeStatusDTO.GpuMetrics.builder()
                            .supported(false)
                            .message("GPU数据格式异常")
                            .build();
                }
                Integer utilization = parseIntSafe(parts[0]);
                Integer used = parseIntSafe(parts[1]);
                Integer total = parseIntSafe(parts[2]);
                return SystemRuntimeStatusDTO.GpuMetrics.builder()
                        .supported(true)
                        .utilizationPercent(utilization)
                        .memoryUsedMiB(used)
                        .memoryTotalMiB(total)
                        .message("ok")
                        .build();
            }
        } catch (Exception e) {
            return SystemRuntimeStatusDTO.GpuMetrics.builder()
                    .supported(false)
                    .message(String.format(Locale.ROOT, "GPU指标不可用: %s", e.getMessage()))
                    .build();
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private Integer parseIntSafe(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
