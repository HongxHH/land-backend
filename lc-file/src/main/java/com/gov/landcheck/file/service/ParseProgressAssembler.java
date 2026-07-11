package com.gov.landcheck.file.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.ParseJob;
import com.gov.landcheck.core.config.global.RetryConfig;
import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.core.enums.ParseJobStateEnum;
import com.gov.landcheck.file.dto.TaskStatusDTO;
import com.gov.landcheck.file.task.plan.ParsePipelinePlan;
import com.gov.landcheck.file.task.plan.ParsePipelinePlan.StageDef;

/**
 * 将内存中的阶段轨迹与 Mongo {@link ParseJob} 中的阶段字段合并为统一的
 * {@link TaskStatusDTO.RunningTaskInfo} 视图。
 */
@Component
public class ParseProgressAssembler {

    private final RetryConfig retryConfig;

    public ParseProgressAssembler(RetryConfig retryConfig) {
        this.retryConfig = retryConfig;
    }

    public TaskStatusDTO.RunningTaskInfo enrichFromActiveTask(TaskStatusDTO.RunningTaskInfo info, ParseJob job) {
        if (info == null || !"FILE_PARSE".equals(info.getTaskType())) {
            return info;
        }
        if ((info.getErrorMessage() == null || info.getErrorMessage().isBlank()) && job != null) {
            info.setErrorMessage(firstNonBlank(job.getErrorMessage(), job.getCancelReason()));
        }
        if (job != null) {
            applyRetryMeta(info, job);
        }
        FileContextType ctx = parseContext(info.getFileContextType());
        List<TaskStatusDTO.PipelineStepInfo> steps = buildSteps(ctx, info.getStageTraces(), job);
        info.setPipelineSteps(steps);
        applyCurrentStage(info, steps);
        return info;
    }

    public TaskStatusDTO.RunningTaskInfo fromParseJob(ParseJob job, FileRecord fileRecord) {
        if (job == null) {
            return null;
        }
        FileContextType ctx = job.getFileContextType() != null
                ? job.getFileContextType()
                : (fileRecord != null ? fileRecord.getFileContextType() : null);
        List<TaskStatusDTO.PipelineStepInfo> steps = buildSteps(ctx, List.of(), job);
        List<TaskStatusDTO.StageTraceInfo> traces = toStageTraces(steps);

        String fileName = fileRecord != null ? fileRecord.getOriginalName() : null;
        Long fileId = fileRecord != null ? fileRecord.getId() : job.getFileRecordId();
        Long projectId = fileRecord != null ? fileRecord.getProjectId() : null;

        TaskStatusDTO.RunningTaskInfo built = TaskStatusDTO.RunningTaskInfo.builder()
                .taskId(job.getTaskId())
                .taskName("文件解析任务")
                .taskType("FILE_PARSE")
                .status(mapJobStatus(job.getJobStatus()))
                .priority("-")
                .projectId(projectId)
                .fileId(fileId)
                .fileName(fileName)
                .parseJobId(job.getId())
                .submittedAt(null)
                .startedAt(toEpochMillis(job.getStartedAt()))
                .waitingDurationMs(null)
                .runningDurationMs(job.getExecutionTimeMs())
                .threadCpuTimeMs(null)
                .cancellable(false)
                .progress(job.getProgress())
                .errorMessage(job.getErrorMessage())
                .fileContextType(ctx != null ? ctx.name() : null)
                .attemptCount(job.getAttemptCount())
                .maxRetryAttempts(retryConfig.getMaxAttempts())
                .retryStatus(job.getRetryStatus())
                .nextRetryAt(toEpochMillis(job.getNextRetryAt()))
                .retryReason(firstNonBlank(job.getRetryReason(), job.getErrorMessage(), job.getCancelReason()))
                .pipelineSteps(steps)
                .stageTraces(traces)
                .build();
        applyCurrentStage(built, steps);
        return built;
    }

    private List<TaskStatusDTO.StageTraceInfo> toStageTraces(List<TaskStatusDTO.PipelineStepInfo> steps) {
        List<TaskStatusDTO.StageTraceInfo> list = new ArrayList<>(steps.size());
        for (TaskStatusDTO.PipelineStepInfo s : steps) {
            list.add(TaskStatusDTO.StageTraceInfo.builder()
                    .stageCode(s.getStageCode())
                    .stageName(s.getStageName())
                    .status(s.getStatus())
                    .startedAt(s.getStartedAt())
                    .endedAt(s.getEndedAt())
                    .durationMs(s.getDurationMs())
                    .message(s.getMessage())
                    .build());
        }
        return list;
    }

    private List<TaskStatusDTO.PipelineStepInfo> buildSteps(
            FileContextType ctx,
            List<TaskStatusDTO.StageTraceInfo> traces,
            ParseJob job) {
        ParseJobStateEnum jobStatus = job != null ? job.getJobStatus() : null;
        String terminalMessage = job != null ? firstNonBlank(job.getErrorMessage(), job.getCancelReason()) : null;
        Map<String, TaskStatusDTO.StageTraceInfo> traceByCode = new LinkedHashMap<>();
        if (traces != null) {
            for (TaskStatusDTO.StageTraceInfo t : traces) {
                if (t != null && t.getStageCode() != null) {
                    traceByCode.putIfAbsent(t.getStageCode(), t);
                }
            }
        }
        List<StageDef> defs = ParsePipelinePlan.stagesFor(ctx);
        List<TaskStatusDTO.PipelineStepInfo> steps = new ArrayList<>(defs.size());
        long nowMs = System.currentTimeMillis();
        for (StageDef def : defs) {
            TaskStatusDTO.StageTraceInfo tr = traceByCode.get(def.code());
            String rawStatus;
            Long durationMs;
            String message;
            Long startedAtMs;
            Long endedAtMs;
            if (tr != null) {
                rawStatus = tr.getStatus() != null ? tr.getStatus() : readRawStatusFromJob(job, def.code());
                durationMs = tr.getDurationMs() != null ? tr.getDurationMs() : readDurationMsFromJob(job, def.code());
                message = tr.getMessage() != null ? tr.getMessage() : readErrorFromJob(job, def.code());
                startedAtMs = tr.getStartedAt() != null ? tr.getStartedAt() : readStartedAtFromJob(job, def.code());
                endedAtMs = tr.getEndedAt() != null ? tr.getEndedAt() : readEndedAtFromJob(job, def.code());
            } else {
                rawStatus = readRawStatusFromJob(job, def.code());
                durationMs = readDurationMsFromJob(job, def.code());
                message = readErrorFromJob(job, def.code());
                startedAtMs = readStartedAtFromJob(job, def.code());
                endedAtMs = readEndedAtFromJob(job, def.code());
            }
            String uiStatus = normalizeUiStatus(rawStatus);
            // 作业已终态时，不应继续显示阶段 RUNNING（避免前端继续实时计时）
            if ("RUNNING".equals(uiStatus) && isTerminalJobStatus(jobStatus)) {
                uiStatus = mapTerminalStageStatus(jobStatus);
            }
            if (durationMs == null) {
                if ("RUNNING".equals(uiStatus)) {
                    durationMs = durationSince(startedAtMs, nowMs);
                } else {
                    durationMs = durationBetween(startedAtMs, endedAtMs);
                }
            }
            // 失败/取消阶段若无阶段级错误，回退显示作业级错误原因
            if ((message == null || message.isBlank())
                    && ("FAILED".equals(uiStatus) || "CANCELLED".equals(uiStatus))) {
                message = terminalMessage;
            }
            steps.add(TaskStatusDTO.PipelineStepInfo.builder()
                    .stageCode(def.code())
                    .stageName(def.displayName())
                    .status(uiStatus)
                    .startedAt(startedAtMs)
                    .endedAt(endedAtMs)
                    .durationMs(durationMs)
                    .message(message)
                    .build());
        }
        return steps;
    }

    private void applyCurrentStage(TaskStatusDTO.RunningTaskInfo info, List<TaskStatusDTO.PipelineStepInfo> steps) {
        if (steps == null || steps.isEmpty()) {
            return;
        }
        for (TaskStatusDTO.PipelineStepInfo s : steps) {
            if ("FAILED".equals(s.getStatus())) {
                info.setCurrentStageCode(s.getStageCode());
                info.setCurrentStageName(s.getStageName());
                info.setCurrentStageStatus("FAILED");
                return;
            }
        }
        for (TaskStatusDTO.PipelineStepInfo s : steps) {
            if ("RUNNING".equals(s.getStatus())) {
                info.setCurrentStageCode(s.getStageCode());
                info.setCurrentStageName(s.getStageName());
                info.setCurrentStageStatus("RUNNING");
                return;
            }
        }
        for (TaskStatusDTO.PipelineStepInfo s : steps) {
            if ("PENDING".equals(s.getStatus())) {
                info.setCurrentStageCode(s.getStageCode());
                info.setCurrentStageName(s.getStageName());
                info.setCurrentStageStatus("PENDING");
                return;
            }
        }
        TaskStatusDTO.PipelineStepInfo last = steps.get(steps.size() - 1);
        info.setCurrentStageCode(last.getStageCode());
        info.setCurrentStageName(last.getStageName());
        info.setCurrentStageStatus(last.getStatus());
    }

    private static FileContextType parseContext(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return FileContextType.valueOf(name.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String normalizeUiStatus(String raw) {
        if (raw == null) {
            return "PENDING";
        }
        if ("PROCESSING".equalsIgnoreCase(raw)) {
            return "RUNNING";
        }
        if ("CANCELLED".equalsIgnoreCase(raw)) {
            return "CANCELLED";
        }
        return raw;
    }

    private static boolean isTerminalJobStatus(ParseJobStateEnum jobStatus) {
        return jobStatus == ParseJobStateEnum.CANCELLED || jobStatus == ParseJobStateEnum.FAILED
                || jobStatus == ParseJobStateEnum.SUCCESS;
    }

    private static String mapTerminalStageStatus(ParseJobStateEnum jobStatus) {
        if (jobStatus == ParseJobStateEnum.CANCELLED) {
            return "CANCELLED";
        }
        if (jobStatus == ParseJobStateEnum.FAILED) {
            return "FAILED";
        }
        if (jobStatus == ParseJobStateEnum.SUCCESS) {
            return "SUCCESS";
        }
        return "PENDING";
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static String readRawStatusFromJob(ParseJob job, String code) {
        if (job == null) {
            return "PENDING";
        }
        return switch (code) {
            case "PREPROCESS" -> nz(job.getPreprocessStatus(), "PENDING");
            case "OCR" -> nz(job.getOcrStatus(), "PENDING");
            case "PARSE" -> nz(job.getParseStatus(), "PENDING");
            case "FILL" -> nz(job.getFillStatus(), "PENDING");
            case "VALIDATE" -> nz(job.getValidateStatus(), "PENDING");
            default -> "PENDING";
        };
    }

    private static Long readDurationMsFromJob(ParseJob job, String code) {
        if (job == null) {
            return null;
        }
        return switch (code) {
            case "PREPROCESS" -> coalesceDuration(job.getPreprocessDurationMs(), job.getPreprocessStartedAt(),
                    job.getPreprocessFinishedAt());
            case "OCR" -> coalesceDuration(job.getOcrDurationMs(), job.getOcrStartedAt(), job.getOcrFinishedAt());
            case "PARSE" ->
                coalesceDuration(job.getParseDurationMs(), job.getParseStartedAt(), job.getParseFinishedAt());
            case "FILL" -> coalesceDuration(job.getFillDurationMs(), job.getFillStartedAt(), job.getFillFinishedAt());
            case "VALIDATE" ->
                coalesceDuration(job.getValidateDurationMs(), job.getValidateStartedAt(), job.getValidateFinishedAt());
            default -> null;
        };
    }

    private static Long readStartedAtFromJob(ParseJob job, String code) {
        if (job == null) {
            return null;
        }
        LocalDateTime startedAt = switch (code) {
            case "PREPROCESS" -> job.getPreprocessStartedAt();
            case "OCR" -> job.getOcrStartedAt();
            case "PARSE" -> job.getParseStartedAt();
            case "FILL" -> job.getFillStartedAt();
            case "VALIDATE" -> job.getValidateStartedAt();
            default -> null;
        };
        return toEpochMillis(startedAt);
    }

    private static Long readEndedAtFromJob(ParseJob job, String code) {
        if (job == null) {
            return null;
        }
        LocalDateTime endedAt = switch (code) {
            case "PREPROCESS" -> job.getPreprocessFinishedAt();
            case "OCR" -> job.getOcrFinishedAt();
            case "PARSE" -> job.getParseFinishedAt();
            case "FILL" -> job.getFillFinishedAt();
            case "VALIDATE" -> job.getValidateFinishedAt();
            default -> null;
        };
        return toEpochMillis(endedAt);
    }

    /** 优先使用阶段完成时写入的毫秒数，老数据再退回起止时间相减。 */
    private static Long coalesceDuration(Long persistedMs, LocalDateTime start, LocalDateTime end) {
        if (persistedMs != null && persistedMs >= 0) {
            return persistedMs;
        }
        return durationMillis(start, end);
    }

    private static String readErrorFromJob(ParseJob job, String code) {
        if (job == null) {
            return null;
        }
        return switch (code) {
            case "PREPROCESS" -> job.getPreprocessError();
            case "OCR" -> job.getOcrError();
            case "PARSE" -> job.getParseError();
            case "FILL" -> job.getFillError();
            case "VALIDATE" -> job.getValidateError();
            default -> null;
        };
    }

    private static Long durationMillis(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return null;
        }
        long ms = Duration.between(start, end).toMillis();
        return ms >= 0 ? ms : null;
    }

    private static Long durationSince(Long startMs, long nowMs) {
        if (startMs == null) {
            return null;
        }
        return Math.max(0L, nowMs - startMs);
    }

    private static Long durationBetween(Long startMs, Long endMs) {
        if (startMs == null || endMs == null) {
            return null;
        }
        return Math.max(0L, endMs - startMs);
    }

    private static String nz(String v, String dft) {
        return v != null && !v.isBlank() ? v : dft;
    }

    private static Long toEpochMillis(LocalDateTime t) {
        if (t == null) {
            return null;
        }
        return t.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private void applyRetryMeta(TaskStatusDTO.RunningTaskInfo info, ParseJob job) {
        info.setAttemptCount(job.getAttemptCount());
        info.setMaxRetryAttempts(retryConfig.getMaxAttempts());
        info.setRetryStatus(job.getRetryStatus());
        info.setNextRetryAt(toEpochMillis(job.getNextRetryAt()));
        info.setRetryReason(firstNonBlank(job.getRetryReason(), job.getErrorMessage(), job.getCancelReason()));
    }

    private static String mapJobStatus(ParseJobStateEnum jobStatus) {
        if (jobStatus == null) {
            return "UNKNOWN";
        }
        return switch (jobStatus) {
            case PENDING -> "PENDING";
            case RUNNING -> "RUNNING";
            case SUCCESS -> "SUCCESS";
            case FAILED -> "FAILED";
            case CANCELLED -> "CANCELLED";
        };
    }
}
