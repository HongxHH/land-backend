package com.gov.landcheck.file.service.impl;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.gov.landcheck.core.bo.entity.ParseJob;
import com.gov.landcheck.core.enums.ParseJobStateEnum;
import com.gov.landcheck.file.service.ParseJobUpdateService;
import com.mongodb.client.result.UpdateResult;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * 解析任务状态更新服务实现
 * 统一负责：先更新内存中的 ParseJob，再按阶段字段落库，避免 Command 中「markXxx + updateXxx」重复更新。
 *
 * @author system
 * @date 2026/02/05
 */
@Slf4j
@Service
public class ParseJobUpdateServiceImpl implements ParseJobUpdateService {

    @Resource
    private MongoTemplate mongoTemplate;

    @Override
    public boolean updateRunning(ParseJob parseJob) {
        LocalDateTime now = LocalDateTime.now();
        parseJob.setJobStatus(ParseJobStateEnum.RUNNING);
        parseJob.setStartedAt(now);
        parseJob.setThreadId(Thread.currentThread().getName());
        parseJob.initializeStageStatusByContext();
        parseJob.setProgress(0);
        parseJob.setPreprocessStartedAt(null);
        parseJob.setPreprocessFinishedAt(null);
        parseJob.setPreprocessGridfsId(null);
        parseJob.setPreprocessError(null);
        parseJob.setOcrStartedAt(null);
        parseJob.setOcrFinishedAt(null);
        parseJob.setOcrResultPath(null);
        parseJob.setOcrError(null);
        parseJob.setParseStartedAt(null);
        parseJob.setParseFinishedAt(null);
        parseJob.setLlmResultPath(null);
        parseJob.setParseError(null);
        parseJob.setFillStartedAt(null);
        parseJob.setFillFinishedAt(null);
        parseJob.setFillResultCount(null);
        parseJob.setFillError(null);
        parseJob.setValidateStartedAt(null);
        parseJob.setValidateFinishedAt(null);
        parseJob.setValidateResultSummary(null);
        parseJob.setValidateError(null);
        parseJob.setPreprocessDurationMs(null);
        parseJob.setOcrDurationMs(null);
        parseJob.setParseDurationMs(null);
        parseJob.setFillDurationMs(null);
        parseJob.setValidateDurationMs(null);
        parseJob.setFinishedAt(null);
        parseJob.setErrorMessage(null);
        parseJob.setRetryStatus("NONE");
        parseJob.setNextRetryAt(null);
        parseJob.setRetryReason(null);
        return doUpdate(parseJob, new Update()
                .set("job_status", parseJob.getJobStatus())
                .set("started_at", parseJob.getStartedAt())
                .set("thread_id", parseJob.getThreadId())
                .set("preprocess_status", parseJob.getPreprocessStatus())
                .set("ocr_status", parseJob.getOcrStatus())
                .set("parse_status", parseJob.getParseStatus())
                .set("fill_status", parseJob.getFillStatus())
                .set("validate_status", parseJob.getValidateStatus())
                .set("progress", parseJob.getProgress())
                .set("preprocess_started_at", parseJob.getPreprocessStartedAt())
                .set("preprocess_finished_at", parseJob.getPreprocessFinishedAt())
                .set("preprocess_gridfs_id", parseJob.getPreprocessGridfsId())
                .set("preprocess_error", parseJob.getPreprocessError())
                .set("ocr_started_at", parseJob.getOcrStartedAt())
                .set("ocr_finished_at", parseJob.getOcrFinishedAt())
                .set("ocr_result_path", parseJob.getOcrResultPath())
                .set("ocr_error", parseJob.getOcrError())
                .set("parse_started_at", parseJob.getParseStartedAt())
                .set("parse_finished_at", parseJob.getParseFinishedAt())
                .set("llm_result_path", parseJob.getLlmResultPath())
                .set("parse_error", parseJob.getParseError())
                .set("fill_started_at", parseJob.getFillStartedAt())
                .set("fill_finished_at", parseJob.getFillFinishedAt())
                .set("fill_result_count", parseJob.getFillResultCount())
                .set("fill_error", parseJob.getFillError())
                .set("validate_started_at", parseJob.getValidateStartedAt())
                .set("validate_finished_at", parseJob.getValidateFinishedAt())
                .set("validate_result_summary", parseJob.getValidateResultSummary())
                .set("validate_error", parseJob.getValidateError())
                .set("preprocess_duration_ms", parseJob.getPreprocessDurationMs())
                .set("ocr_duration_ms", parseJob.getOcrDurationMs())
                .set("parse_duration_ms", parseJob.getParseDurationMs())
                .set("fill_duration_ms", parseJob.getFillDurationMs())
                .set("validate_duration_ms", parseJob.getValidateDurationMs())
                .set("finished_at", parseJob.getFinishedAt())
                .set("error_message", parseJob.getErrorMessage())
                .set("retry_status", parseJob.getRetryStatus())
                .set("next_retry_at", parseJob.getNextRetryAt())
                .set("retry_reason", parseJob.getRetryReason())
                .set("update_time", now), "RUNNING", true);
    }

    @Override
    public void updatePreprocessStarted(ParseJob parseJob) {
        parseJob.setPreprocessStatus("PROCESSING");
        parseJob.setPreprocessStartedAt(LocalDateTime.now());
        parseJob.updateProgress();
        doUpdate(parseJob, buildStageUpdate(parseJob, "preprocess_status", "preprocess_started_at")
                .set("progress", parseJob.getProgress()), "PREPROCESS_STARTED");
    }

    @Override
    public void updatePreprocessCompleted(ParseJob parseJob, String preprocessGridfsId) {
        LocalDateTime now = LocalDateTime.now();
        parseJob.setPreprocessStatus("SUCCESS");
        parseJob.setPreprocessFinishedAt(now);
        parseJob.setPreprocessGridfsId(preprocessGridfsId);
        parseJob.setPreprocessDurationMs(stageDurationMs(parseJob.getPreprocessStartedAt(), now));
        parseJob.updateProgress();
        doUpdate(parseJob,
                buildStageUpdate(parseJob, "preprocess_status", "preprocess_finished_at", "preprocess_gridfs_id",
                        "preprocess_duration_ms")
                        .set("progress", parseJob.getProgress()),
                "PREPROCESS_COMPLETED");
    }

    @Override
    public void updatePreprocessFailed(ParseJob parseJob, String errorMessage) {
        LocalDateTime now = LocalDateTime.now();
        parseJob.setPreprocessStatus("FAILED");
        parseJob.setPreprocessFinishedAt(now);
        parseJob.setPreprocessDurationMs(stageDurationMs(parseJob.getPreprocessStartedAt(), now));
        parseJob.setPreprocessError(errorMessage);
        parseJob.setJobStatus(ParseJobStateEnum.FAILED);
        parseJob.setFinishedAt(now);
        doUpdate(parseJob, new Update()
                .set("preprocess_status", parseJob.getPreprocessStatus())
                .set("preprocess_finished_at", parseJob.getPreprocessFinishedAt())
                .set("preprocess_duration_ms", parseJob.getPreprocessDurationMs())
                .set("preprocess_error", parseJob.getPreprocessError())
                .set("job_status", parseJob.getJobStatus())
                .set("finished_at", parseJob.getFinishedAt())
                .set("update_time", now), "PREPROCESS_FAILED");
    }

    @Override
    public void updateOcrStarted(ParseJob parseJob) {
        parseJob.setOcrStatus("PROCESSING");
        parseJob.setOcrStartedAt(LocalDateTime.now());
        parseJob.updateProgress();
        doUpdate(parseJob, buildStageUpdate(parseJob, "ocr_status", "ocr_started_at")
                .set("progress", parseJob.getProgress()), "OCR_STARTED");
    }

    @Override
    public void updateOcrCompleted(ParseJob parseJob, String ocrResultPath) {
        LocalDateTime now = LocalDateTime.now();
        parseJob.setOcrStatus("SUCCESS");
        parseJob.setOcrFinishedAt(now);
        parseJob.setOcrResultPath(ocrResultPath);
        parseJob.setOcrDurationMs(stageDurationMs(parseJob.getOcrStartedAt(), now));
        parseJob.updateProgress();
        doUpdate(parseJob,
                buildStageUpdate(parseJob, "ocr_status", "ocr_finished_at", "ocr_result_path", "ocr_duration_ms")
                        .set("progress", parseJob.getProgress()),
                "OCR_COMPLETED");
    }

    @Override
    public void updateOcrFailed(ParseJob parseJob, String errorMessage) {
        LocalDateTime now = LocalDateTime.now();
        parseJob.setOcrStatus("FAILED");
        parseJob.setOcrFinishedAt(now);
        parseJob.setOcrDurationMs(stageDurationMs(parseJob.getOcrStartedAt(), now));
        parseJob.setOcrError(errorMessage);
        parseJob.setJobStatus(ParseJobStateEnum.FAILED);
        parseJob.setFinishedAt(now);
        doUpdate(parseJob, new Update()
                .set("ocr_status", parseJob.getOcrStatus())
                .set("ocr_finished_at", parseJob.getOcrFinishedAt())
                .set("ocr_duration_ms", parseJob.getOcrDurationMs())
                .set("ocr_error", parseJob.getOcrError())
                .set("job_status", parseJob.getJobStatus())
                .set("finished_at", parseJob.getFinishedAt())
                .set("update_time", now), "OCR_FAILED");
    }

    @Override
    public void updateParseStarted(ParseJob parseJob) {
        parseJob.setParseStatus("PROCESSING");
        parseJob.setParseStartedAt(LocalDateTime.now());
        parseJob.updateProgress();
        doUpdate(parseJob, buildStageUpdate(parseJob, "parse_status", "parse_started_at")
                .set("progress", parseJob.getProgress()), "PARSE_STARTED");
    }

    @Override
    public void updateParseCompleted(ParseJob parseJob, String llmResultPath) {
        LocalDateTime now = LocalDateTime.now();
        parseJob.setParseStatus("SUCCESS");
        parseJob.setParseFinishedAt(now);
        parseJob.setLlmResultPath(llmResultPath != null ? llmResultPath : "");
        parseJob.setParseDurationMs(stageDurationMs(parseJob.getParseStartedAt(), now));
        parseJob.updateProgress();
        doUpdate(parseJob,
                buildStageUpdate(parseJob, "parse_status", "parse_finished_at", "llm_result_path", "parse_duration_ms")
                        .set("progress", parseJob.getProgress()),
                "PARSE_COMPLETED");
    }

    @Override
    public void updateParseFailed(ParseJob parseJob, String errorMessage) {
        LocalDateTime now = LocalDateTime.now();
        parseJob.setParseStatus("FAILED");
        parseJob.setParseFinishedAt(now);
        parseJob.setParseDurationMs(stageDurationMs(parseJob.getParseStartedAt(), now));
        parseJob.setParseError(errorMessage);
        parseJob.setJobStatus(ParseJobStateEnum.FAILED);
        parseJob.setFinishedAt(now);
        doUpdate(parseJob, new Update()
                .set("parse_status", parseJob.getParseStatus())
                .set("parse_finished_at", parseJob.getParseFinishedAt())
                .set("parse_duration_ms", parseJob.getParseDurationMs())
                .set("parse_error", parseJob.getParseError())
                .set("job_status", parseJob.getJobStatus())
                .set("finished_at", parseJob.getFinishedAt())
                .set("update_time", now), "PARSE_FAILED");
    }

    @Override
    public void updateFillStarted(ParseJob parseJob) {
        parseJob.setFillStatus("PROCESSING");
        parseJob.setFillStartedAt(LocalDateTime.now());
        parseJob.updateProgress();
        doUpdate(parseJob, buildStageUpdate(parseJob, "fill_status", "fill_started_at")
                .set("progress", parseJob.getProgress()), "FILL_STARTED");
    }

    @Override
    public void updateFillCompleted(ParseJob parseJob, Integer fillResultCount) {
        LocalDateTime now = LocalDateTime.now();
        parseJob.setFillStatus("SUCCESS");
        parseJob.setFillFinishedAt(now);
        parseJob.setFillResultCount(fillResultCount);
        parseJob.setFillDurationMs(stageDurationMs(parseJob.getFillStartedAt(), now));
        parseJob.updateProgress();
        doUpdate(parseJob,
                buildStageUpdate(parseJob, "fill_status", "fill_finished_at", "fill_result_count", "fill_duration_ms")
                        .set("progress", parseJob.getProgress()),
                "FILL_COMPLETED");
    }

    @Override
    public void updateFillFailed(ParseJob parseJob, String errorMessage) {
        LocalDateTime now = LocalDateTime.now();
        parseJob.setFillStatus("FAILED");
        parseJob.setFillFinishedAt(now);
        parseJob.setFillDurationMs(stageDurationMs(parseJob.getFillStartedAt(), now));
        parseJob.setFillError(errorMessage);
        parseJob.setJobStatus(ParseJobStateEnum.FAILED);
        parseJob.setFinishedAt(now);
        doUpdate(parseJob, new Update()
                .set("fill_status", parseJob.getFillStatus())
                .set("fill_finished_at", parseJob.getFillFinishedAt())
                .set("fill_duration_ms", parseJob.getFillDurationMs())
                .set("fill_error", parseJob.getFillError())
                .set("job_status", parseJob.getJobStatus())
                .set("finished_at", parseJob.getFinishedAt())
                .set("update_time", now), "FILL_FAILED");
    }

    @Override
    public void updateValidateStarted(ParseJob parseJob) {
        parseJob.setValidateStatus("PROCESSING");
        parseJob.setValidateStartedAt(LocalDateTime.now());
        parseJob.updateProgress();
        doUpdate(parseJob, buildStageUpdate(parseJob, "validate_status", "validate_started_at")
                .set("progress", parseJob.getProgress()), "VALIDATE_STARTED");
    }

    @Override
    public void updateValidateCompleted(ParseJob parseJob, String summary) {
        LocalDateTime now = LocalDateTime.now();
        parseJob.setValidateStatus("SUCCESS");
        parseJob.setValidateFinishedAt(now);
        parseJob.setValidateResultSummary(summary != null ? summary : "");
        parseJob.setValidateDurationMs(stageDurationMs(parseJob.getValidateStartedAt(), now));
        parseJob.updateProgress();
        doUpdate(parseJob,
                buildStageUpdate(parseJob, "validate_status", "validate_finished_at", "validate_result_summary",
                        "validate_duration_ms")
                        .set("progress", parseJob.getProgress()),
                "VALIDATE_COMPLETED");
    }

    @Override
    public void updateValidateFailed(ParseJob parseJob, String errorMessage) {
        LocalDateTime now = LocalDateTime.now();
        parseJob.setValidateStatus("FAILED");
        parseJob.setValidateFinishedAt(now);
        parseJob.setValidateDurationMs(stageDurationMs(parseJob.getValidateStartedAt(), now));
        parseJob.setValidateError(errorMessage);
        parseJob.setJobStatus(ParseJobStateEnum.FAILED);
        parseJob.setFinishedAt(now);
        doUpdate(parseJob, new Update()
                .set("validate_status", parseJob.getValidateStatus())
                .set("validate_finished_at", parseJob.getValidateFinishedAt())
                .set("validate_duration_ms", parseJob.getValidateDurationMs())
                .set("validate_error", parseJob.getValidateError())
                .set("job_status", parseJob.getJobStatus())
                .set("finished_at", parseJob.getFinishedAt())
                .set("update_time", now), "VALIDATE_FAILED");
    }

    @Override
    public boolean updateJobSuccess(ParseJob parseJob, Long executionTimeMs) {
        LocalDateTime now = LocalDateTime.now();
        parseJob.setJobStatus(ParseJobStateEnum.SUCCESS);
        parseJob.setFinishedAt(now);
        parseJob.setExecutionTimeMs(executionTimeMs);
        parseJob.setProgress(100);
        parseJob.setRetryStatus("NONE");
        parseJob.setNextRetryAt(null);
        parseJob.setRetryReason(null);
        return doUpdate(parseJob, new Update()
                .set("job_status", parseJob.getJobStatus())
                .set("finished_at", parseJob.getFinishedAt())
                .set("execution_time_ms", parseJob.getExecutionTimeMs())
                .set("progress", parseJob.getProgress())
                .set("retry_status", parseJob.getRetryStatus())
                .set("next_retry_at", parseJob.getNextRetryAt())
                .set("retry_reason", parseJob.getRetryReason())
                .set("update_time", now), "JOB_SUCCESS", true);
    }

    @Override
    public void updateJobFailed(ParseJob parseJob, String errorMessage) {
        LocalDateTime now = LocalDateTime.now();
        parseJob.setJobStatus(ParseJobStateEnum.FAILED);
        parseJob.setFinishedAt(now);
        parseJob.setErrorMessage(errorMessage);
        parseJob.setRetryStatus("NONE");
        parseJob.setNextRetryAt(null);
        doUpdate(parseJob, new Update()
                .set("job_status", parseJob.getJobStatus())
                .set("finished_at", parseJob.getFinishedAt())
                .set("error_message", parseJob.getErrorMessage())
                .set("retry_status", parseJob.getRetryStatus())
                .set("next_retry_at", parseJob.getNextRetryAt())
                .set("update_time", now), "JOB_FAILED");
    }

    @Override
    public void updateJobCancelled(ParseJob parseJob) {
        LocalDateTime now = LocalDateTime.now();
        parseJob.setJobStatus(ParseJobStateEnum.CANCELLED);
        parseJob.setCancelledAt(now);
        parseJob.setFinishedAt(now);
        parseJob.setErrorMessage(parseJob.getCancelReason());
        parseJob.setRetryStatus("NONE");
        parseJob.setNextRetryAt(null);

        // 收敛阶段状态：避免作业已取消但阶段仍显示 PROCESSING，导致前端继续计时。
        if ("PROCESSING".equalsIgnoreCase(parseJob.getPreprocessStatus())) {
            parseJob.setPreprocessStatus("CANCELLED");
            parseJob.setPreprocessFinishedAt(now);
            parseJob.setPreprocessDurationMs(stageDurationMs(parseJob.getPreprocessStartedAt(), now));
        }
        if ("PROCESSING".equalsIgnoreCase(parseJob.getOcrStatus())) {
            parseJob.setOcrStatus("CANCELLED");
            parseJob.setOcrFinishedAt(now);
            parseJob.setOcrDurationMs(stageDurationMs(parseJob.getOcrStartedAt(), now));
        }
        if ("PROCESSING".equalsIgnoreCase(parseJob.getParseStatus())) {
            parseJob.setParseStatus("CANCELLED");
            parseJob.setParseFinishedAt(now);
            parseJob.setParseDurationMs(stageDurationMs(parseJob.getParseStartedAt(), now));
        }
        if ("PROCESSING".equalsIgnoreCase(parseJob.getFillStatus())) {
            parseJob.setFillStatus("CANCELLED");
            parseJob.setFillFinishedAt(now);
            parseJob.setFillDurationMs(stageDurationMs(parseJob.getFillStartedAt(), now));
        }
        if ("PROCESSING".equalsIgnoreCase(parseJob.getValidateStatus())) {
            parseJob.setValidateStatus("CANCELLED");
            parseJob.setValidateFinishedAt(now);
            parseJob.setValidateDurationMs(stageDurationMs(parseJob.getValidateStartedAt(), now));
        }

        doUpdate(parseJob, new Update()
                .set("job_status", parseJob.getJobStatus())
                .set("cancelled_at", parseJob.getCancelledAt())
                .set("finished_at", parseJob.getFinishedAt())
                .set("error_message", parseJob.getErrorMessage())
                .set("retry_status", parseJob.getRetryStatus())
                .set("next_retry_at", parseJob.getNextRetryAt())
                .set("preprocess_status", parseJob.getPreprocessStatus())
                .set("preprocess_finished_at", parseJob.getPreprocessFinishedAt())
                .set("preprocess_duration_ms", parseJob.getPreprocessDurationMs())
                .set("ocr_status", parseJob.getOcrStatus())
                .set("ocr_finished_at", parseJob.getOcrFinishedAt())
                .set("ocr_duration_ms", parseJob.getOcrDurationMs())
                .set("parse_status", parseJob.getParseStatus())
                .set("parse_finished_at", parseJob.getParseFinishedAt())
                .set("parse_duration_ms", parseJob.getParseDurationMs())
                .set("fill_status", parseJob.getFillStatus())
                .set("fill_finished_at", parseJob.getFillFinishedAt())
                .set("fill_duration_ms", parseJob.getFillDurationMs())
                .set("validate_status", parseJob.getValidateStatus())
                .set("validate_finished_at", parseJob.getValidateFinishedAt())
                .set("validate_duration_ms", parseJob.getValidateDurationMs())
                .set("update_time", now), "JOB_CANCELLED");
    }

    private Update buildStageUpdate(ParseJob parseJob, String... fieldNames) {
        Update update = new Update().set("update_time", LocalDateTime.now());
        for (String field : fieldNames) {
            switch (field) {
                case "preprocess_status" -> update.set("preprocess_status", parseJob.getPreprocessStatus());
                case "preprocess_started_at" -> update.set("preprocess_started_at", parseJob.getPreprocessStartedAt());
                case "preprocess_finished_at" ->
                    update.set("preprocess_finished_at", parseJob.getPreprocessFinishedAt());
                case "preprocess_gridfs_id" -> update.set("preprocess_gridfs_id", parseJob.getPreprocessGridfsId());
                case "ocr_status" -> update.set("ocr_status", parseJob.getOcrStatus());
                case "ocr_started_at" -> update.set("ocr_started_at", parseJob.getOcrStartedAt());
                case "ocr_finished_at" -> update.set("ocr_finished_at", parseJob.getOcrFinishedAt());
                case "ocr_result_path" -> update.set("ocr_result_path", parseJob.getOcrResultPath());
                case "parse_status" -> update.set("parse_status", parseJob.getParseStatus());
                case "parse_started_at" -> update.set("parse_started_at", parseJob.getParseStartedAt());
                case "parse_finished_at" -> update.set("parse_finished_at", parseJob.getParseFinishedAt());
                case "llm_result_path" -> update.set("llm_result_path", parseJob.getLlmResultPath());
                case "fill_status" -> update.set("fill_status", parseJob.getFillStatus());
                case "fill_started_at" -> update.set("fill_started_at", parseJob.getFillStartedAt());
                case "fill_finished_at" -> update.set("fill_finished_at", parseJob.getFillFinishedAt());
                case "fill_result_count" -> update.set("fill_result_count", parseJob.getFillResultCount());
                case "preprocess_duration_ms" ->
                    update.set("preprocess_duration_ms", parseJob.getPreprocessDurationMs());
                case "ocr_duration_ms" -> update.set("ocr_duration_ms", parseJob.getOcrDurationMs());
                case "parse_duration_ms" -> update.set("parse_duration_ms", parseJob.getParseDurationMs());
                case "fill_duration_ms" -> update.set("fill_duration_ms", parseJob.getFillDurationMs());
                case "validate_duration_ms" -> update.set("validate_duration_ms", parseJob.getValidateDurationMs());
                case "validate_status" -> update.set("validate_status", parseJob.getValidateStatus());
                case "validate_started_at" -> update.set("validate_started_at", parseJob.getValidateStartedAt());
                case "validate_finished_at" -> update.set("validate_finished_at", parseJob.getValidateFinishedAt());
                case "validate_result_summary" ->
                    update.set("validate_result_summary", parseJob.getValidateResultSummary());
                default -> {
                }
            }
        }
        return update;
    }

    /**
     * 阶段耗时：用内存中的阶段开始时间与当前时刻计算，避免依赖 Mongo 读回的时间戳精度/舍入。
     */
    private static Long stageDurationMs(LocalDateTime startedAt, LocalDateTime finishedAt) {
        if (startedAt == null || finishedAt == null) {
            return null;
        }
        long ms = ChronoUnit.MILLIS.between(startedAt, finishedAt);
        return ms >= 0 ? ms : null;
    }

    private boolean doUpdate(ParseJob parseJob, Update update, String stage) {
        return doUpdate(parseJob, update, stage, false);
    }

    private boolean doUpdate(ParseJob parseJob, Update update, String stage, boolean rejectIfCancelled) {
        if (parseJob == null || parseJob.getId() == null) {
            log.warn("ParseJobUpdateService skip: parseJob or id is null, stage={}", stage);
            return false;
        }
        try {
            Query query = Query.query(Criteria.where("_id").is(parseJob.getId()));
            if (rejectIfCancelled) {
                query.addCriteria(Criteria.where("cancel_requested").ne(true));
                query.addCriteria(Criteria.where("job_status").ne(ParseJobStateEnum.CANCELLED));
            }
            UpdateResult result = mongoTemplate.updateFirst(query, update, ParseJob.class);
            boolean updated = result.getModifiedCount() > 0;
            if (rejectIfCancelled && !updated) {
                log.info("ParseJobUpdateService skip cancelled job: parseJobId={}, stage={}", parseJob.getId(), stage);
            }
            return updated;
        } catch (Exception e) {
            log.error("ParseJobUpdateService update failed: parseJobId={}, stage={}, error={}",
                    parseJob.getId(), stage, e.getMessage(), e);
            throw e;
        }
    }
}
