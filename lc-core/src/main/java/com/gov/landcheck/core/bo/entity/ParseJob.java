package com.gov.landcheck.core.bo.entity;

import java.time.LocalDateTime;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.gov.landcheck.core.config.mongo.MongoIdEntity;
import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.core.enums.ParseJobStateEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 解析任务实体类
 *
 * @author system
 * @date 2025/12/19
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "parse_job")
@Schema(description = "解析任务")
public class ParseJob extends MongoIdEntity {

    @Field(name = "file_record_id")
    @Schema(description = "关联 file_record.id")
    private Long fileRecordId;

    @Field(name = "file_context_type")
    @Schema(description = "文件内容类型（用于动态阶段进度模型）", example = "SURVEY_REPORT")
    private FileContextType fileContextType;

    @Field(name = "job_status")
    @Schema(description = "作业状态", example = "PENDING")
    private ParseJobStateEnum jobStatus;

    @Field(name = "attempt_count")
    @Schema(description = "已重试次数", example = "0")
    private Integer attemptCount;

    @Field(name = "started_at")
    @Schema(description = "开始处理时间")
    private LocalDateTime startedAt;

    @Field(name = "finished_at")
    @Schema(description = "完成时间")
    private LocalDateTime finishedAt;

    @Field(name = "worker_node")
    @Schema(description = "执行节点/worker 标识（便于追踪）", example = "worker-001")
    private String workerNode;

    @Field(name = "error_message")
    @Schema(description = "错误信息（失败时记录）")
    private String errorMessage;

    @Field(name = "created_by")
    @Schema(description = "创建者（sys_user.id/系统）")
    private Long createdBy;

    @Field(name = "progress")
    @Schema(description = "解析进度（0-100）", example = "50")
    private Integer progress;

    // ========== 预处理阶段 ==========
    @Field(name = "preprocess_status")
    @Schema(description = "预处理状态（PENDING/PROCESSING/SUCCESS/FAILED）", example = "SUCCESS")
    private String preprocessStatus;

    @Field(name = "preprocess_started_at")
    @Schema(description = "预处理开始时间")
    private LocalDateTime preprocessStartedAt;

    @Field(name = "preprocess_finished_at")
    @Schema(description = "预处理完成时间")
    private LocalDateTime preprocessFinishedAt;

    @Field(name = "preprocess_duration_ms")
    @Schema(description = "预处理耗时(毫秒)，阶段结束时在服务端用起止时间计算后落库，避免仅靠 Mongo 时间戳相减被截断")
    private Long preprocessDurationMs;

    @Field(name = "preprocess_gridfs_id")
    @Schema(description = "预处理后PDF的GridFS ID")
    private String preprocessGridfsId;

    @Field(name = "preprocess_error")
    @Schema(description = "预处理错误信息")
    private String preprocessError;

    // ========== OCR识别阶段 ==========
    @Field(name = "ocr_status")
    @Schema(description = "OCR识别状态（PENDING/PROCESSING/SUCCESS/FAILED）", example = "SUCCESS")
    private String ocrStatus;

    @Field(name = "ocr_started_at")
    @Schema(description = "OCR识别开始时间")
    private LocalDateTime ocrStartedAt;

    @Field(name = "ocr_finished_at")
    @Schema(description = "OCR识别完成时间")
    private LocalDateTime ocrFinishedAt;

    @Field(name = "ocr_duration_ms")
    @Schema(description = "OCR阶段耗时(毫秒)")
    private Long ocrDurationMs;

    @Field(name = "ocr_result_path")
    @Schema(description = "OCR结果存储路径（JSON格式）")
    private String ocrResultPath;

    @Field(name = "ocr_error")
    @Schema(description = "OCR识别错误信息")
    private String ocrError;

    // ========== 结果解析阶段 ==========
    @Field(name = "parse_status")
    @Schema(description = "结果解析状态（PENDING/PROCESSING/SUCCESS/FAILED）", example = "SUCCESS")
    private String parseStatus;

    @Field(name = "parse_started_at")
    @Schema(description = "结果解析开始时间")
    private LocalDateTime parseStartedAt;

    @Field(name = "parse_finished_at")
    @Schema(description = "结果解析完成时间")
    private LocalDateTime parseFinishedAt;

    @Field(name = "parse_duration_ms")
    @Schema(description = "数据解析阶段耗时(毫秒)")
    private Long parseDurationMs;

    @Field(name = "llm_result_path")
    @Schema(description = "大模型解析结果存储路径（JSON格式）")
    private String llmResultPath;

    @Field(name = "parse_error")
    @Schema(description = "结果解析错误信息")
    private String parseError;

    // ========== 数据回填阶段 ==========
    @Field(name = "fill_status")
    @Schema(description = "数据回填状态（PENDING/PROCESSING/SUCCESS/FAILED）", example = "SUCCESS")
    private String fillStatus;

    @Field(name = "fill_started_at")
    @Schema(description = "数据回填开始时间")
    private LocalDateTime fillStartedAt;

    @Field(name = "fill_finished_at")
    @Schema(description = "数据回填完成时间")
    private LocalDateTime fillFinishedAt;

    @Field(name = "fill_duration_ms")
    @Schema(description = "数据回填阶段耗时(毫秒)")
    private Long fillDurationMs;

    @Field(name = "fill_result_count")
    @Schema(description = "回填的数据条数", example = "100")
    private Integer fillResultCount;

    @Field(name = "fill_error")
    @Schema(description = "数据回填错误信息")
    private String fillError;

    // ========== 数据校验阶段 ==========
    @Field(name = "validate_status")
    @Schema(description = "数据校验状态（PENDING/PROCESSING/SUCCESS/FAILED）", example = "SUCCESS")
    private String validateStatus;

    @Field(name = "validate_started_at")
    @Schema(description = "数据校验开始时间")
    private LocalDateTime validateStartedAt;

    @Field(name = "validate_finished_at")
    @Schema(description = "数据校验完成时间")
    private LocalDateTime validateFinishedAt;

    @Field(name = "validate_duration_ms")
    @Schema(description = "数据校验阶段耗时(毫秒)")
    private Long validateDurationMs;

    @Field(name = "validate_result_summary")
    @Schema(description = "校验结果摘要")
    private String validateResultSummary;

    @Field(name = "validate_error")
    @Schema(description = "数据校验错误信息")
    private String validateError;

    // ========== 统计信息 ==========
    @Field(name = "processed_pages")
    @Schema(description = "已处理页数", example = "8")
    private Integer processedPages;

    @Field(name = "ocr_confidence_avg")
    @Schema(description = "OCR平均置信度", example = "0.95")
    private Double ocrConfidenceAvg;

    @Field(name = "execution_time_ms")
    @Schema(description = "总执行时间(毫秒)", example = "180000")
    private Long executionTimeMs;

    // ========== 进程管理字段 ==========
    @Field(name = "task_id")
    @Schema(description = "任务唯一标识符（用于取消任务）")
    private String taskId;

    @Field(name = "thread_id")
    @Schema(description = "执行线程ID")
    private String threadId;

    @Field(name = "cancel_requested")
    @Schema(description = "是否请求取消")
    private Boolean cancelRequested = false;

    @Field(name = "cancel_reason")
    @Schema(description = "取消原因")
    private String cancelReason;

    @Field(name = "cancelled_at")
    @Schema(description = "取消时间")
    private LocalDateTime cancelledAt;

    @Field(name = "retry_status")
    @Schema(description = "重试状态（NONE/SCHEDULED/SUBMITTED）")
    private String retryStatus;

    @Field(name = "next_retry_at")
    @Schema(description = "下次重试触发时间")
    private LocalDateTime nextRetryAt;

    @Field(name = "retry_reason")
    @Schema(description = "最近一次重试原因")
    private String retryReason;

    public ParseJob() {
        this.attemptCount = 0;
        this.progress = 0;
        this.preprocessStatus = "PENDING";
        this.ocrStatus = "PENDING";
        this.parseStatus = "PENDING";
        this.fillStatus = "PENDING";
        this.validateStatus = "PENDING";
        this.cancelRequested = false;
        this.retryStatus = "NONE";
    }

    /**
     * 更新整体进度
     */
    public void updateProgress() {
        int totalSteps = 0;
        int completedSteps = 0;

        if (isStageEnabled(Stage.PREPROCESS)) {
            totalSteps++;
            if (isStageCompleted(preprocessStatus)) {
                completedSteps++;
            }
        }
        if (isStageEnabled(Stage.OCR)) {
            totalSteps++;
            if (isStageCompleted(ocrStatus)) {
                completedSteps++;
            }
        }
        if (isStageEnabled(Stage.PARSE)) {
            totalSteps++;
            if (isStageCompleted(parseStatus)) {
                completedSteps++;
            }
        }
        if (isStageEnabled(Stage.FILL)) {
            totalSteps++;
            if (isStageCompleted(fillStatus)) {
                completedSteps++;
            }
        }
        if (isStageEnabled(Stage.VALIDATE)) {
            totalSteps++;
            if (isStageCompleted(validateStatus)) {
                completedSteps++;
            }
        }

        this.progress = totalSteps == 0 ? 0 : (completedSteps * 100) / totalSteps;
    }

    /**
     * 在任务启动/重试启动时，按文件类型初始化阶段状态：
     * - 参与阶段置为 PENDING
     * - 不参与阶段置为 SKIPPED（避免固定5阶段造成进度失真）
     */
    public void initializeStageStatusByContext() {
        this.preprocessStatus = isStageEnabled(Stage.PREPROCESS) ? "PENDING" : "SKIPPED";
        this.ocrStatus = isStageEnabled(Stage.OCR) ? "PENDING" : "SKIPPED";
        this.parseStatus = isStageEnabled(Stage.PARSE) ? "PENDING" : "SKIPPED";
        this.fillStatus = isStageEnabled(Stage.FILL) ? "PENDING" : "SKIPPED";
        this.validateStatus = isStageEnabled(Stage.VALIDATE) ? "PENDING" : "SKIPPED";
    }

    private boolean isStageCompleted(String status) {
        return "SUCCESS".equals(status) || "SKIPPED".equals(status);
    }

    private boolean isStageEnabled(Stage stage) {
        if (fileContextType == null) {
            return stage != Stage.VALIDATE;
        }
        return switch (fileContextType) {
            case PROJECT_PARTY_SURVEY_SUMMARY -> stage == Stage.PARSE || stage == Stage.FILL;
            case SURVEY_REPORT -> true;
            default -> stage != Stage.VALIDATE;
        };
    }

    private enum Stage {
        PREPROCESS,
        OCR,
        PARSE,
        FILL,
        VALIDATE
    }

    // ========== 取消相关方法 ==========

    /**
     * 检查是否请求取消
     */
    public boolean isCancelRequested() {
        return Boolean.TRUE.equals(cancelRequested);
    }

    /**
     * 请求取消任务
     */
    public void requestCancel(String reason) {
        this.cancelRequested = true;
        this.cancelReason = reason;
        this.cancelledAt = LocalDateTime.now();
        this.jobStatus = ParseJobStateEnum.CANCELLED;
    }
}