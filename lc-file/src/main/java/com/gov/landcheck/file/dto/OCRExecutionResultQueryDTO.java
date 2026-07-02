package com.gov.landcheck.file.dto;

import java.time.LocalDateTime;

import com.gov.landcheck.core.bo.dto.BaseQueryDTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * OCR执行结果查询DTO
 *
 * @author system
 * @date 2026/01/26
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "OCR执行结果查询请求")
public class OCRExecutionResultQueryDTO extends BaseQueryDTO {

    @Schema(description = "关联文件记录ID")
    private Long fileRecordId;

    @Schema(description = "关联解析任务ID")
    private Long parseJobId;

    @Schema(description = "OCR处理的页面数量")
    private Integer pageCount;

    @Schema(description = "平均置信度范围-开始")
    private Double averageConfidenceStart;

    @Schema(description = "平均置信度范围-结束")
    private Double averageConfidenceEnd;

    @Schema(description = "OCR处理耗时范围-开始（毫秒）")
    private Long processingTimeMsStart;

    @Schema(description = "OCR处理耗时范围-结束（毫秒）")
    private Long processingTimeMsEnd;

    @Schema(description = "执行时间范围-开始")
    private LocalDateTime executionTimeStart;

    @Schema(description = "执行时间范围-结束")
    private LocalDateTime executionTimeEnd;

    @Schema(description = "是否从 GridFS 加载 OCR JSON / Markdown 正文（详情展示传 true，列表分页传 false）")
    private Boolean loadGridFsPayload;

}