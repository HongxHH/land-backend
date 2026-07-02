package com.gov.landcheck.file.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务状态查询响应DTO
 * 
 * 包含线程池状态信息、正在执行的任务列表、排队等待的任务信息、阶段轨迹信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskStatusDTO {

    /**
     * 线程池状态信息
     */
    private ThreadPoolStatus threadPoolStatus;

    /**
     * 正在执行的任务列表
     */
    private List<RunningTaskInfo> runningTasks;

    /**
     * 排队等待的任务信息
     */
    private QueueTaskInfo queueTasks;

    /**
     * 线程池状态信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThreadPoolStatus {
        /**
         * 核心线程数
         */
        private int corePoolSize;

        /**
         * 最大线程数
         */
        private int maximumPoolSize;

        /**
         * 当前活动线程数
         */
        private int activeThreadCount;

        /**
         * 当前线程池大小
         */
        private int poolSize;

        /**
         * 队列最大容量，-1 表示无界（如 PriorityBlockingQueue）
         */
        private int queueCapacity;

        /**
         * 当前队列大小
         */
        private int queueSize;

        /**
         * 已完成任务总数
         */
        private long completedTaskCount;

        /**
         * 线程池是否正在关闭
         */
        private boolean isShutdown;

        /**
         * 线程池是否已终止
         */
        private boolean isTerminated;
    }

    /**
     * 正在执行的任务信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RunningTaskInfo {
        /**
         * 任务ID
         */
        private String taskId;

        /**
         * 任务名称/描述
         */
        private String taskName;

        /**
         * 任务类型
         */
        private String taskType;

        /**
         * 任务状态
         */
        private String status;

        /**
         * 优先级
         */
        private String priority;

        /**
         * 项目ID（适用于文件解析、批量上传等任务）
         */
        private Long projectId;

        /**
         * 文件ID（适用于文件解析任务）
         */
        private Long fileId;

        /**
         * 文件名（适用于文件解析任务）
         */
        private String fileName;

        /**
         * 解析任务ID（适用于文件解析任务）
         */
        private Long parseJobId;

        /**
         * 提交时间戳（毫秒）
         */
        private Long submittedAt;

        /**
         * 开始执行时间戳（毫秒）
         */
        private Long startedAt;

        /**
         * 等待时长（毫秒）
         */
        private Long waitingDurationMs;

        /**
         * 运行时长（毫秒）
         */
        private Long runningDurationMs;

        /**
         * 线程CPU时间（毫秒）
         */
        private Long threadCpuTimeMs;

        /**
         * 是否可取消
         */
        private boolean cancellable;

        /**
         * 当前阶段编码
         */
        private String currentStageCode;

        /**
         * 当前阶段名称
         */
        private String currentStageName;

        /**
         * 当前阶段状态
         */
        private String currentStageStatus;

        /**
         * 任务进度（0-100）
         */
        private Integer progress;

        /**
         * 错误信息
         */
        private String errorMessage;

        /**
         * 文件内容类型（如 SURVEY_REPORT），用于专属解析链路展示
         */
        private String fileContextType;

        /**
         * 当前重试次数（第几次重试）
         */
        private Integer attemptCount;

        /**
         * 最大重试次数
         */
        private Integer maxRetryAttempts;

        /**
         * 重试状态（NONE/SCHEDULED/SUBMITTED）
         */
        private String retryStatus;

        /**
         * 下次重试时间戳（毫秒）
         */
        private Long nextRetryAt;

        /**
         * 最近重试/失败原因
         */
        private String retryReason;

        /**
         * 按文件类型裁剪后的解析流水线步骤（与 {@link com.gov.landcheck.file.task.plan.ParsePipelinePlan}
         * 一致）
         */
        private List<PipelineStepInfo> pipelineSteps;

        /**
         * 阶段轨迹
         */
        private List<StageTraceInfo> stageTraces;
    }

    /**
     * 解析流水线单步（用于前端渲染流程可视化）
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PipelineStepInfo {
        private String stageCode;
        private String stageName;
        private String status;
        private Long startedAt;
        private Long endedAt;
        private Long durationMs;
        private String message;
    }

    /**
     * 排队任务信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueueTaskInfo {
        /**
         * 排队任务数量
         */
        private int queueSize;

        /**
         * 高优先级排队任务数量
         */
        private int highPriorityCount;

        /**
         * 普通优先级排队任务数量
         */
        private int normalPriorityCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StageTraceInfo {
        private String stageCode;
        private String stageName;
        private String status;
        private Long startedAt;
        private Long endedAt;
        private Long durationMs;
        private String message;
    }
}