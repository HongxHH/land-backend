package com.gov.landcheck.file.task.thread;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.gov.landcheck.core.audit.AuditFileRecorder;
import com.gov.landcheck.core.audit.OperationAuditService;
import com.gov.landcheck.core.audit.OperationType;
import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.ParseJob;
import com.gov.landcheck.core.bo.entity.ProjectPartySurveySummaryForm;
import com.gov.landcheck.core.bo.entity.SurveyReportInfo;
import com.gov.landcheck.core.config.cache.event.ProjectDataChangedEvent;
import com.gov.landcheck.core.config.global.ApplicationContextProvider;
import com.gov.landcheck.core.config.mq.constant.TopicConstants;
import com.gov.landcheck.core.config.mq.dto.FileParseResultMessage;
import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.core.enums.FileStateEnum;
import com.gov.landcheck.core.enums.ParseJobStateEnum;
import com.gov.landcheck.file.service.ParseJobUpdateService;
import com.gov.landcheck.file.service.RetryDecisionService;
import com.gov.landcheck.file.service.parse.ParseFillSnapshotService;
import com.gov.landcheck.file.task.base.Task;
import com.gov.landcheck.file.task.base.TaskData;
import com.gov.landcheck.file.task.base.TaskException;
import com.gov.landcheck.file.task.executor.ParseFileExecutor;
import com.gov.landcheck.file.task.result.TaskResultSummary;

import lombok.extern.slf4j.Slf4j;

/**
 * 文件解析任务
 * 作为 TaskThreadPool 的适配器，委托 ParseFileExecutor 执行具体业务逻辑。
 */
@Slf4j
public class ParseFileTask implements Task {

    private static final int PARTY_SUMMARY_FAIL_REMARK_MAX = 900;

    private final TaskData taskData;
    private final ParseFileExecutor parseFileExecutor;
    private final RetryDecisionService retryDecisionService;
    private final String taskId;

    private volatile boolean cancelled = false;
    private volatile boolean retryTriggered = false;
    private String cancelReason;
    private volatile Thread executingThread;
    private volatile Long startedAtEpochMs;

    public ParseFileTask(String taskId, TaskData taskData, ParseFileExecutor parseFileExecutor) {
        this.taskData = taskData;
        this.parseFileExecutor = parseFileExecutor;
        this.retryDecisionService = ApplicationContextProvider.getBean(RetryDecisionService.class);
        this.taskId = taskId;
        taskData.setTask(this);
    }

    @Override
    public String getTaskId() {
        return taskId;
    }

    @Override
    public Long getStartedAtEpochMs() {
        return startedAtEpochMs;
    }

    @Override
    public Long getExecutingThreadId() {
        return executingThread != null ? executingThread.threadId() : null;
    }

    public TaskData getTaskData() {
        return taskData;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void run() {
        startedAtEpochMs = System.currentTimeMillis();
        executingThread = Thread.currentThread();
        try {
            log.debug("开始执行文件解析任务: fileId={}, taskId={}", taskData.getFileRecord().getId(), getTaskId());

            // 1. 检查任务是否已被取消（含排队期间 requestCancel）
            if (shouldStop()) {
                log.debug("任务在执行前已被取消: fileId={}, taskId={}", taskData.getFileRecord().getId(), getTaskId());
                cancel(cancelReason);
                return;
            }

            // 2. 更新任务状态为运行中
            updateTaskStatus(ParseJobStateEnum.RUNNING);

            // 2.1 落库前已被取消（竞态）
            if (shouldStop()) {
                cancel(cancelReason != null ? cancelReason : "任务已取消");
                return;
            }

            // 3. 交给Executor执行文件解析
            parseFileExecutor.execute(taskData);

        } catch (Exception e) {
            // 4. 处理异常情况
            // 4.1. 检查任务是否已被取消
            boolean isCancellation = cancelled
                    || e instanceof InterruptedException
                    || (e.getMessage() != null && e.getMessage().contains("任务已取消"));

            if (isCancellation) {
                log.debug("任务取消/中断，执行取消清理: fileId={}, taskId={}, reason={}",
                        taskData.getFileRecord().getId(), getTaskId(),
                        cancelReason != null ? cancelReason : e.getMessage());
                cancel(cancelReason != null ? cancelReason : "任务已取消");
                return;
            }
            log.error("文件解析任务执行失败: fileId={}, taskId={}, error={}", taskData.getFileRecord().getId(), getTaskId(),
                    e.getMessage(), e);
            // 4.2. 处理重试逻辑
            if (shouldStop()) {
                cancel(cancelReason != null ? cancelReason : "任务已取消");
                return;
            }
            ParseJob parseJob = taskData.getParseJob();
            RetryDecisionService.RetryDecision decision = retryDecisionService.shouldRetry(parseJob, e);
            if (decision.isShouldRetry()) {
                int newAttemptCount = (parseJob.getAttemptCount() != null ? parseJob.getAttemptCount() : 0) + 1;
                retryDecisionService.executeRetry(parseJob, taskData.getFileRecord(),
                        decision.getDelayMs(), newAttemptCount, decision.getReason());
                retryTriggered = true;
                log.info("任务触发重试: fileId={}, taskId={}, attempt={}",
                        taskData.getFileRecord().getId(), getTaskId(), newAttemptCount);
            } else {
                retryTriggered = false;
            }

            // 4.3. 抛出异常，中断任务执行
            throw new RuntimeException(e);

        } finally {
            // 5. 清理执行线程引用
            executingThread = null;
        }
    }

    @Override
    public void success() {
        if (cancelled) {
            log.debug("ParseFileTask success 回调忽略（任务已取消）: fileId={}, taskId={}",
                    taskData.getFileRecord().getId(), getTaskId());
            return;
        }
        if (shouldStop()) {
            cancel(cancelReason != null ? cancelReason : "任务已取消");
            return;
        }
        log.debug("ParseFileTask 成功回调: fileId={}, taskId={}", taskData.getFileRecord().getId(), getTaskId());
        try {
            taskData.updateProgress(100);
            taskData.setCurrentStageStatus("SUCCESS");
            updateTaskStatus(ParseJobStateEnum.SUCCESS);
            recordParseCompleteAudit();
        } catch (Exception e) {
            if (e instanceof TaskException taskEx
                    && TaskException.ErrorCode.TASK_CANCELLED.equals(taskEx.getErrorCode())) {
                cancel(cancelReason != null ? cancelReason : taskEx.getMessage());
                return;
            }
            log.error("更新任务成功状态失败: fileId={}, taskId={}, error={}",
                    taskData.getFileRecord().getId(), getTaskId(), e.getMessage(), e);
        }
    }

    private void recordParseCompleteAudit() {
        try {
            MongoTemplate mongoTemplate = ApplicationContextProvider.getBean(MongoTemplate.class);
            OperationAuditService auditService = ApplicationContextProvider.getBean(OperationAuditService.class);
            Long fileRecordId = taskData.getFileRecord().getId();
            FileRecord latest = mongoTemplate.findById(fileRecordId, FileRecord.class);
            if (latest == null) {
                return;
            }
            ParseJob parseJob = taskData.getParseJob();
            Long operatorId = parseJob != null ? parseJob.getCreatedBy() : latest.getUploadUserId();
            String operatorName = latest.getUploadUserName();
            AuditFileRecorder.recordFileOperation(auditService, OperationType.PARSE_COMPLETE.name(), latest,
                    Map.of("taskId", getTaskId()), operatorId, operatorName);
        } catch (Exception e) {
            log.debug("解析完成审计记录失败: fileId={}, taskId={}, error={}",
                    taskData.getFileRecord().getId(), getTaskId(), e.getMessage());
        }
    }

    @Override
    public void failed(Throwable throwable) {
        if (cancelled) {
            log.debug("ParseFileTask failed 回调忽略（任务已取消）: fileId={}, taskId={}",
                    taskData.getFileRecord().getId(), getTaskId());
            return;
        }
        String errMsg = throwable != null ? throwable.getMessage() : null;
        if (throwable != null) {
            log.error("ParseFileTask failed 回调: fileId={}, taskId={}, error={}",
                    taskData.getFileRecord().getId(), getTaskId(), errMsg, throwable);
        } else {
            log.error("ParseFileTask failed 回调: fileId={}, taskId={}, error=(null throwable)",
                    taskData.getFileRecord().getId(), getTaskId());
        }
        try {
            if (!retryTriggered) {
                updateTaskStatus(ParseJobStateEnum.FAILED, errMsg);
            }
        } catch (Exception e) {
            log.error("更新任务失败状态失败: fileId={}, taskId={}, error={}",
                    taskData.getFileRecord().getId(), getTaskId(), e.getMessage(), e);
        }
    }

    @Override
    public void fallback() {
        if (cancelled) {
            return;
        }
        log.warn("ParseFileTask fallback: fileId={}, taskId={}", taskData.getFileRecord().getId(), getTaskId());
        parseFileExecutor.rollback(taskData);
    }

    @Override
    public void cancel(String reason) {
        if (cancelled) {
            return;
        }
        cancelled = true;
        cancelReason = reason;
        taskData.setCurrentStageStatus("CANCELLED");
        taskData.setError(reason != null ? reason : "任务已取消");

        // 如果执行线程不为空且不为当前线程，则中断执行线程
        if (executingThread != null &&
                executingThread != Thread.currentThread()) {
            executingThread.interrupt();
        }

        updateTaskStatus(ParseJobStateEnum.CANCELLED);
        parseFileExecutor.rollback(taskData);

        log.info("ParseFileTask 已取消: fileId={}, taskId={}, reason={}",
                taskData.getFileRecord().getId(), getTaskId(), reason);
    }

    @Override
    public boolean shouldStop() {
        if (cancelled) {
            return true;
        }
        ParseJob parseJob = taskData.getParseJob();
        if (parseJob == null || parseJob.getId() == null) {
            return false;
        }
        if (parseJob.isCancelRequested()) {
            cancelled = true;
            cancelReason = parseJob.getCancelReason();
            return true;
        }
        try {
            MongoTemplate mongoTemplate = ApplicationContextProvider.getBean(MongoTemplate.class);
            ParseJob latest = mongoTemplate.findById(parseJob.getId(), ParseJob.class);
            if (latest != null) {
                taskData.setParseJob(latest);
                if (latest.isCancelRequested()) {
                    cancelled = true;
                    cancelReason = latest.getCancelReason();
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("刷新 ParseJob 取消状态失败: parseJobId={}, error={}",
                    parseJob.getId(), e.getMessage());
        }
        return false;
    }

    @Override
    public void checkCancellation() {
        if (!shouldStop()) {
            return;
        }
        throw new TaskException(TaskException.ErrorCode.TASK_CANCELLED,
                "任务已取消: " + (cancelReason != null ? cancelReason : taskId));
    }

    private void updateTaskStatus(ParseJobStateEnum status) {
        updateTaskStatus(status, null);
    }

    private void updateTaskStatus(ParseJobStateEnum status, String errorMessage) {
        MongoTemplate mongoTemplate = ApplicationContextProvider.getBean(MongoTemplate.class);
        ParseJobUpdateService parseJobUpdateService = ApplicationContextProvider.getBean(ParseJobUpdateService.class);

        switch (status) {
            case RUNNING -> {
                taskData.getFileRecord().setFileState(FileStateEnum.PARSING);
                mongoTemplate.save(taskData.getFileRecord());
                if (!parseJobUpdateService.updateRunning(taskData.getParseJob())) {
                    throw new TaskException(TaskException.ErrorCode.TASK_CANCELLED, "任务已取消");
                }
            }

            case SUCCESS -> {
                try {
                    PlatformTransactionManager txMgr = ApplicationContextProvider
                            .getBean(PlatformTransactionManager.class);
                    TransactionTemplate tpl = new TransactionTemplate(txMgr);
                    tpl.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
                    tpl.executeWithoutResult(tx -> persistParseSuccessState(mongoTemplate, parseJobUpdateService));
                } catch (TransactionException ex) {
                    log.warn(
                            "SUCCESS 落库未能在 Mongo 事务中完成（常见于未启用副本集），将顺序重试一次以保证可用性: {}",
                            ex.getMessage());
                    persistParseSuccessState(mongoTemplate, parseJobUpdateService);
                }
                deleteFillSnapshotQuietly();
            }

            case FAILED -> {
                taskData.getFileRecord().setFileState(FileStateEnum.PARSE_FAIL);
                taskData.getFileRecord().setPreprocessGridfsId(null);
                taskData.getFileRecord().setParseJobId(taskData.getParseJob().getId());
                parseJobUpdateService.updateJobFailed(taskData.getParseJob(), errorMessage);
                mongoTemplate.save(taskData.getFileRecord());
                try {
                    markProjectPartySummaryFormParseFailed(mongoTemplate, errorMessage);
                } catch (Exception ex) {
                    log.warn("同步项目方汇总主表解析失败状态失败: fileId={}, error={}",
                            taskData.getFileRecord().getId(), ex.getMessage());
                }
            }

            case CANCELLED -> {
                taskData.getFileRecord().setFileState(FileStateEnum.WAITING_PARSE);
                taskData.getFileRecord().setAutoParseQueuedAt(null);
                taskData.getFileRecord().setPreprocessGridfsId(null);
                taskData.getFileRecord().setParseJobId(taskData.getParseJob().getId());
                parseJobUpdateService.updateJobCancelled(taskData.getParseJob());
                mongoTemplate.save(taskData.getFileRecord());
            }

            default -> mongoTemplate.save(taskData.getParseJob());
        }
    }

    @Override
    public TaskResultSummary buildResultSummary(Throwable throwable) {
        if (cancelled || retryTriggered || taskData == null || taskData.getFileRecord() == null) {
            return TaskResultSummary.none();
        }
        Long fileId = taskData.getFileRecord().getId();
        Long projectId = taskData.getFileRecord().getProjectId();
        String fileName = taskData.getFileRecord().getOriginalName();
        String failMsg = throwable != null && throwable.getMessage() != null ? throwable.getMessage() : "unknown";
        FileParseResultMessage payload = throwable == null
                ? FileParseResultMessage.success(taskId, fileId, projectId, fileName, null)
                : FileParseResultMessage.failed(taskId, fileId, projectId, fileName, null, failMsg);
        String tag = throwable == null ? TopicConstants.TAG_PARSE_COMPLETED : TopicConstants.TAG_PARSE_FAILED;
        return new TaskResultSummary(
                TopicConstants.TOPIC_FILE_PARSE_RESULT,
                tag,
                taskId,
                payload,
                // 解析结果是前端状态同步关键链路，不能因全局限流被丢弃
                null);
    }

    private void deleteFillSnapshotQuietly() {
        try {
            ParseJob parseJob = taskData.getParseJob();
            if (parseJob == null || parseJob.getId() == null) {
                return;
            }
            ParseFillSnapshotService snapshotService = ApplicationContextProvider
                    .getBean(ParseFillSnapshotService.class);
            snapshotService.deleteByParseJobId(parseJob.getId());
        } catch (Exception ex) {
            log.warn("删除回填快照失败: fileId={}, taskId={}, error={}",
                    taskData.getFileRecord() != null ? taskData.getFileRecord().getId() : null,
                    getTaskId(), ex.getMessage(), ex);
        }
    }

    /**
     * 成功态：勘测报告、项目方汇总、文件记录与解析作业状态一致落库；优先走 Mongo 多文档事务，失败时退化为顺序提交。
     */
    private void persistParseSuccessState(MongoTemplate mongoTemplate, ParseJobUpdateService parseJobUpdateService) {
        taskData.markCompleted();
        taskData.getFileRecord().setFileState(FileStateEnum.PARSE_COMPLETE);
        taskData.getFileRecord().setParseJobId(taskData.getParseJob().getId());

        SurveyReportInfo surveyReportInfo = taskData.getSurveyReportInfo();
        if (surveyReportInfo != null) {
            surveyReportInfo.setIsParsed(1);
            mongoTemplate.save(surveyReportInfo);
        }
        ProjectPartySurveySummaryForm parsedForm = taskData.getProjectPartySummaryForm();
        if (parsedForm != null) {
            Long fileRecordId = taskData.getFileRecord().getId();
            Query query = new Query(Criteria.where("file_record_id").is(fileRecordId));
            ProjectPartySurveySummaryForm existing = mongoTemplate.findOne(query, ProjectPartySurveySummaryForm.class);
            ProjectPartySurveySummaryForm target = existing != null ? existing : parsedForm;
            if (existing == null) {
                if (target.getProjectId() == null) {
                    target.setProjectId(taskData.getFileRecord().getProjectId());
                }
                if (target.getFileRecordId() == null) {
                    target.setFileRecordId(fileRecordId);
                }
            }
            target.setDeclaredTotals(parsedForm.getDeclaredTotals());
            target.setIsParsed(1);
            if (StringUtils.hasText(parsedForm.getParseStatus())) {
                target.setParseStatus(parsedForm.getParseStatus());
            } else if (!StringUtils.hasText(target.getParseStatus())) {
                target.setParseStatus("SUCCESS");
            }
            target.setRemark(parsedForm.getRemark());
            if (existing == null) {
                target.preSave();
            } else {
                target.setUpdateTime(LocalDateTime.now());
            }
            mongoTemplate.save(target);
            publishProjectPartySummaryChanged(taskData.getFileRecord().getProjectId(), target.getId());
        }

        if (!parseJobUpdateService.updateJobSuccess(taskData.getParseJob(), taskData.getExecutionTime())) {
            throw new TaskException(TaskException.ErrorCode.TASK_CANCELLED, "任务已取消，跳过成功落库");
        }
        mongoTemplate.save(taskData.getFileRecord());
    }

    /**
     * 与 {@link FileStateEnum#PARSE_FAIL} 对齐：项目方汇总主表若仍为 PENDING，界面会误显示为「未解析」。
     */
    private void markProjectPartySummaryFormParseFailed(MongoTemplate mongoTemplate, String errorMessage) {
        if (taskData.getFileRecord() == null || taskData.getFileRecord().getId() == null) {
            return;
        }
        if (!FileContextType.PROJECT_PARTY_SURVEY_SUMMARY.equals(taskData.getFileRecord().getFileContextType())) {
            return;
        }
        Long fileRecordId = taskData.getFileRecord().getId();
        Long projectId = taskData.getFileRecord().getProjectId();
        if (projectId == null) {
            return;
        }
        String msg = StringUtils.hasText(errorMessage) ? errorMessage.trim() : "未知错误";
        if (msg.length() > PARTY_SUMMARY_FAIL_REMARK_MAX) {
            msg = msg.substring(0, PARTY_SUMMARY_FAIL_REMARK_MAX) + "…";
        }
        String remark = "解析失败: " + msg;
        Query query = new Query(Criteria.where("file_record_id").is(fileRecordId));
        ProjectPartySurveySummaryForm existing = mongoTemplate.findOne(query, ProjectPartySurveySummaryForm.class);
        Long formId;
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            ProjectPartySurveySummaryForm inserted = new ProjectPartySurveySummaryForm();
            inserted.setProjectId(projectId);
            inserted.setFileRecordId(fileRecordId);
            inserted.setIsParsed(0);
            inserted.setParseStatus("FAILED");
            inserted.setDeclaredTotals(null);
            inserted.setRemark(remark);
            inserted.preSave();
            mongoTemplate.save(inserted);
            formId = inserted.getId();
        } else {
            Update update = new Update()
                    .set("is_parsed", 0)
                    .set("parse_status", "FAILED")
                    .set("remark", remark)
                    .set("update_time", now);
            mongoTemplate.updateFirst(query, update, ProjectPartySurveySummaryForm.class);
            formId = existing.getId();
        }
        try {
            ApplicationEventPublisher publisher = ApplicationContextProvider.getBean(ApplicationEventPublisher.class);
            publisher.publishEvent(ProjectDataChangedEvent.projectPartySummaryChanged(projectId, formId));
        } catch (Exception ex) {
            log.warn("发布项目方汇总变更事件失败: projectId={}, formId={}, error={}", projectId, formId, ex.getMessage());
        }
    }

    private void publishProjectPartySummaryChanged(Long projectId, Long formId) {
        if (projectId == null || formId == null) {
            return;
        }
        try {
            ApplicationEventPublisher publisher = ApplicationContextProvider.getBean(ApplicationEventPublisher.class);
            publisher.publishEvent(ProjectDataChangedEvent.projectPartySummaryChanged(projectId, formId));
        } catch (Exception ex) {
            log.warn("发布项目方汇总变更事件失败: projectId={}, formId={}, error={}", projectId, formId, ex.getMessage());
        }
    }

}