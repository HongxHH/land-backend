package com.gov.landcheck.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 全库待解析/解析失败文件批量入队结果。
 */
@Data
@Builder
@Schema(description = "批量入队解析结果")
public class BulkParseEnqueueResultDTO {

    @Schema(description = "扫描到的可自动解析类型文件数")
    private int scanned;

    @Schema(description = "成功提交到解析线程池的文件数")
    private int submitted;

    @Schema(description = "未提交（已在进行、不可解析、GridFS 缺失等）")
    private int skipped;

    @Schema(description = "是否因线程池队列已满而提前停止")
    private boolean queueFull;

    @Schema(description = "数据库中仍为待解析/解析失败且未在本轮提交的文件估计数")
    private int remainingEstimate;
}
