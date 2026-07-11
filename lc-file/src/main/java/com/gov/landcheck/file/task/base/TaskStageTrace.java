package com.gov.landcheck.file.task.base;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单个解析阶段的执行轨迹。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskStageTrace {
    private String stageCode;
    private String stageName;
    private String status;
    private Long startedAt;
    private Long endedAt;
    private Long durationMs;
    private String message;
}
