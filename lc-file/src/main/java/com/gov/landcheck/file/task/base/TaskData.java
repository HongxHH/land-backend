package com.gov.landcheck.file.task.base;

import java.util.ArrayList;
import java.util.List;

import com.gov.landcheck.core.bo.entity.ContractInfo;
import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.ParseJob;
import com.gov.landcheck.core.bo.entity.ParsedDataHeader;
import com.gov.landcheck.core.bo.entity.ParsedDataItem;
import com.gov.landcheck.core.bo.entity.PlanningReviewForm;
import com.gov.landcheck.core.bo.entity.PlanningReviewRow;
import com.gov.landcheck.core.bo.entity.CapacityIndicatorInfo;
import com.gov.landcheck.core.bo.entity.ProjectPartySurveySummaryForm;
import com.gov.landcheck.core.bo.entity.RoomInfo;
import com.gov.landcheck.core.bo.entity.SurveyReportInfo;
import com.gov.landcheck.file.dto.OCRProcessResult;

import lombok.Data;

/**
 * 解析任务数据类
 * 包含文件解析过程中的所有相关数据
 *
 * @author system
 * @date 2025/01/17
 */
@Data
public class TaskData {

    /**
     * 文件记录
     */
    private FileRecord fileRecord;

    /**
     * 解析任务记录
     */
    private ParseJob parseJob;

    /**
     * 任务引用（用于检查任务取消状态）
     */
    private Task task;

    /**
     * 预处理后的PDF GridFS ID
     */
    private String preprocessGridfsId;

    /**
     * OCR结果数据
     */
    private OCRProcessResult ocrProcessResult;

    /**
     * 解析结果头
     */
    private ParsedDataHeader parsedDataHeader;

    /**
     * 解析结果明细列表
     */
    private List<ParsedDataItem> parsedDataItems;

    /**
     * 解析出的房间信息列表
     */
    private List<RoomInfo> roomInfos;

    /**
     * 合同信息
     */
    private ContractInfo contractInfo;

    /**
     * 实测报告信息
     */
    private SurveyReportInfo surveyReportInfo;

    /**
     * 规划复核表主表（解析阶段产出，回填阶段写入库）
     */
    private PlanningReviewForm planningReviewForm;

    /**
     * 规划复核表行
     */
    private List<PlanningReviewRow> planningReviewRows;

    /**
     * 项目方汇总主表（解析阶段产出，回填阶段写入库）
     */
    private ProjectPartySurveySummaryForm projectPartySummaryForm;

    /**
     * 容量指标核查表信息（解析阶段产出，回填阶段写入库）
     */
    private CapacityIndicatorInfo capacityIndicatorInfo;

    /**
     * 本次解析管道是否已执行到回填命令（用于失败回滚时避免误删「上一轮已成功回填」的业务数据）
     */
    private boolean fillCommandEntered;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 解析进度 (0-100)
     */
    private Integer progress;

    /**
     * 当前阶段编码
     */
    private String currentStageCode;

    /**
     * 当前阶段名称
     */
    private String currentStageName;

    /**
     * 当前阶段状态：PENDING/RUNNING/SUCCESS/FAILED/SKIPPED
     */
    private String currentStageStatus;

    /**
     * 阶段执行轨迹
     */
    private List<TaskStageTrace> stageTraces;

    /**
     * 任务开始时间
     */
    private Long startTime;

    /**
     * 任务结束时间
     */
    private Long endTime;

    public TaskData() {
        this.progress = 0;
        this.startTime = System.currentTimeMillis();
        this.stageTraces = new ArrayList<>();
    }

    public TaskData(FileRecord fileRecord) {
        this.fileRecord = fileRecord;
        this.progress = 0;
        this.startTime = System.currentTimeMillis();
        this.stageTraces = new ArrayList<>();
    }

    /**
     * 更新解析进度
     */
    public void updateProgress(int progress) {
        this.progress = Math.min(100, Math.max(0, progress));
    }

    /**
     * 标记任务完成
     */
    public void markCompleted() {
        this.endTime = System.currentTimeMillis();
    }

    /**
     * 获取任务执行时间(毫秒)
     */
    public long getExecutionTime() {
        if (endTime != null && startTime != null) {
            return endTime - startTime;
        }
        long st = startTime != null ? startTime : System.currentTimeMillis();
        return System.currentTimeMillis() - st;
    }

    /**
     * 设置错误信息
     */
    public void setError(String errorMessage) {
        this.errorMessage = errorMessage;
        markCompleted();
    }

    public synchronized void startStage(String stageCode, String stageName, String message, Integer progress) {
        long now = System.currentTimeMillis();
        this.currentStageCode = stageCode;
        this.currentStageName = stageName;
        this.currentStageStatus = "RUNNING";
        if (progress != null) {
            updateProgress(progress);
        }
        TaskStageTrace trace = getOrCreateTrace(stageCode, stageName);
        trace.setStatus("RUNNING");
        trace.setStartedAt(now);
        trace.setEndedAt(null);
        trace.setDurationMs(null);
        if (message != null) {
            trace.setMessage(message);
        }
    }

    public synchronized void completeStage(String stageCode, String message, Integer progress) {
        long now = System.currentTimeMillis();
        TaskStageTrace trace = getOrCreateTrace(stageCode, currentStageName != null ? currentStageName : stageCode);
        if (trace.getStartedAt() == null) {
            trace.setStartedAt(now);
        }
        trace.setStatus("SUCCESS");
        trace.setEndedAt(now);
        trace.setDurationMs(Math.max(0L, now - trace.getStartedAt()));
        if (message != null) {
            trace.setMessage(message);
        }
        this.currentStageCode = stageCode;
        this.currentStageName = trace.getStageName();
        this.currentStageStatus = "SUCCESS";
        if (progress != null) {
            updateProgress(progress);
        }
    }

    public synchronized void skipStage(String stageCode, String stageName, String message, Integer progress) {
        long now = System.currentTimeMillis();
        TaskStageTrace trace = getOrCreateTrace(stageCode, stageName);
        trace.setStatus("SKIPPED");
        trace.setStartedAt(now);
        trace.setEndedAt(now);
        trace.setDurationMs(0L);
        trace.setMessage(message != null ? message : "跳过执行");
        if (progress != null) {
            updateProgress(progress);
        }
    }

    public synchronized void failCurrentStage(String message) {
        long now = System.currentTimeMillis();
        if (currentStageCode == null) {
            this.currentStageStatus = "FAILED";
            this.errorMessage = message;
            return;
        }
        TaskStageTrace trace = getOrCreateTrace(currentStageCode,
                currentStageName != null ? currentStageName : currentStageCode);
        if (trace.getStartedAt() == null) {
            trace.setStartedAt(now);
        }
        trace.setStatus("FAILED");
        trace.setEndedAt(now);
        trace.setDurationMs(Math.max(0L, now - trace.getStartedAt()));
        trace.setMessage(message);
        this.currentStageStatus = "FAILED";
        this.errorMessage = message;
    }

    public synchronized List<TaskStageTrace> getStageTracesSnapshot() {
        if (stageTraces == null || stageTraces.isEmpty()) {
            return new ArrayList<>();
        }
        List<TaskStageTrace> copy = new ArrayList<>(stageTraces.size());
        for (TaskStageTrace trace : stageTraces) {
            copy.add(TaskStageTrace.builder()
                    .stageCode(trace.getStageCode())
                    .stageName(trace.getStageName())
                    .status(trace.getStatus())
                    .startedAt(trace.getStartedAt())
                    .endedAt(trace.getEndedAt())
                    .durationMs(trace.getDurationMs())
                    .message(trace.getMessage())
                    .build());
        }
        return copy;
    }

    private TaskStageTrace getOrCreateTrace(String stageCode, String stageName) {
        if (stageTraces == null) {
            stageTraces = new ArrayList<>();
        }
        for (TaskStageTrace trace : stageTraces) {
            if (stageCode != null && stageCode.equals(trace.getStageCode())) {
                if (trace.getStageName() == null) {
                    trace.setStageName(stageName);
                }
                return trace;
            }
        }
        TaskStageTrace newTrace = TaskStageTrace.builder()
                .stageCode(stageCode)
                .stageName(stageName)
                .status("PENDING")
                .build();
        stageTraces.add(newTrace);
        return newTrace;
    }
}