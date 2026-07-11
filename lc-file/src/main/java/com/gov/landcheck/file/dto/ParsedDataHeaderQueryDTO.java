package com.gov.landcheck.file.dto;

import com.gov.landcheck.core.bo.dto.BaseQueryDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.core.enums.FileStateEnum;
import com.gov.landcheck.core.enums.FileType;

/**
 * 解析数据头表查询DTO
 *
 * @author system
 * @date 2026/01/24
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "解析数据头表查询请求")
public class ParsedDataHeaderQueryDTO extends BaseQueryDTO {

    @Schema(description = "文件记录ID")
    private Long fileRecordId;

    @Schema(description = "解析任务ID")
    private Long parseJobId;

    @Schema(description = "项目ID")
    private Long projectId;



    @Schema(description = "文件类型（PDF/JPG/XLS等）")
    private FileType fileType;

    @Schema(description = "文件内容类型（CONTRACT/SURVEY_REPORT/OTHER）")
    private FileContextType fileContextType;

    @Schema(description = "解析状态")
    private FileStateEnum parseStatus;

    @Schema(description = "解析开始时间范围-开始")
    private LocalDateTime parseStartTimeStart;

    @Schema(description = "解析开始时间范围-结束")
    private LocalDateTime parseStartTimeEnd;

    @Schema(description = "解析完成时间范围-开始")
    private LocalDateTime parseEndTimeStart;

    @Schema(description = "解析完成时间范围-结束")
    private LocalDateTime parseEndTimeEnd;

    @Schema(description = "解析引擎")
    private String parseEngine;

    @Schema(description = "原始文件名（支持模糊查询）")
    private String originalFileName;

    @Schema(description = "是否为扫描件（0否 1是）")
    private Integer isScanned;

    @Schema(description = "大模型名称")
    private String llmModel;

    @Schema(description = "执行时间范围-开始（毫秒）")
    private Long executionTimeMsStart;

    @Schema(description = "执行时间范围-结束（毫秒）")
    private Long executionTimeMsEnd;

}