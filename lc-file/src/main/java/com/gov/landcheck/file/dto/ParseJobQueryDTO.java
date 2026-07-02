package com.gov.landcheck.file.dto;

import com.gov.landcheck.core.bo.dto.BaseQueryDTO;
import com.gov.landcheck.core.enums.ParseJobStateEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 解析任务查询DTO
 *
 * @author system
 * @date 2026/01/26
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "解析任务查询请求")
public class ParseJobQueryDTO extends BaseQueryDTO {

    @Schema(description = "关联文件记录ID")
    private Long fileRecordId;

    @Schema(description = "作业状态")
    private ParseJobStateEnum jobStatus;

    @Schema(description = "执行节点/worker标识（支持模糊查询）")
    private String workerNode;

    @Schema(description = "已重试次数")
    private Integer attemptCount;

    @Schema(description = "解析进度（0-100）")
    private Integer progress;

    @Schema(description = "开始处理时间范围-开始")
    private LocalDateTime startedAtStart;

    @Schema(description = "开始处理时间范围-结束")
    private LocalDateTime startedAtEnd;

    @Schema(description = "完成时间范围-开始")
    private LocalDateTime finishedAtStart;

    @Schema(description = "完成时间范围-结束")
    private LocalDateTime finishedAtEnd;

    @Schema(description = "预处理状态（PENDING/PROCESSING/SUCCESS/FAILED）")
    private String preprocessStatus;

    @Schema(description = "OCR识别状态（PENDING/PROCESSING/SUCCESS/FAILED）")
    private String ocrStatus;

}