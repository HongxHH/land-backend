package com.gov.landcheck.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * OCR执行结果响应DTO
 * 包含格式化的文件内容而非GridFS ID
 *
 * @author system
 * @date 2026/01/26
 */
@Data
@Schema(description = "OCR执行结果响应")
public class OCRExecutionResultResponseDTO {

    @Schema(description = "记录ID")
    private Long id;

    @Schema(description = "关联文件记录ID")
    private Long fileRecordId;

    @Schema(description = "关联解析任务ID")
    private Long parseJobId;

    @Schema(description = "OCR处理结果JSON内容")
    private String ocrResultJson;

    @Schema(description = "OCR处理结果JSON文件的GridFS ID")
    private String ocrResultJsonGridfsId;

    @Schema(description = "Markdown文件内容")
    private String markdownContent;

    @Schema(description = "Markdown文件的GridFS ID")
    private String markdownFileGridfsId;

    @Schema(description = "页面数量")
    private Integer pageCount;

    @Schema(description = "处理耗时(毫秒)")
    private Long processingTimeMs;

    @Schema(description = "执行时间")
    private LocalDateTime executionTime;
}