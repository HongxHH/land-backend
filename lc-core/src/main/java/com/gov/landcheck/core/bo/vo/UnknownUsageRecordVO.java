package com.gov.landcheck.core.bo.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 未知用途记录展示 VO（含最近一次出现来源的文件名、项目名称）
 */
@Data
@Schema(description = "未知用途记录（含来源展示信息）")
public class UnknownUsageRecordVO {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "未知用途名称")
    private String usageName;

    @Schema(description = "关联的项目ID")
    private Long projectId;

    @Schema(description = "最近一次出现来源的文件记录ID")
    private Long fileRecordId;

    @Schema(description = "来源 RoomInfo ID")
    private Long roomInfoId;

    @Schema(description = "来源 SurveyReportInfo ID")
    private Long surveyReportInfoId;

    @Schema(description = "出现次数")
    private Integer occurrenceCount;

    @Schema(description = "处理状态（0待处理 1已处理 2已忽略）")
    private Integer status;

    @Schema(description = "处理人")
    private String handledBy;

    @Schema(description = "处理备注")
    private String handleRemark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "最近一次出现来源的原始文件名")
    private String recentFileName;

    @Schema(description = "最近一次出现所属项目名称")
    private String recentProjectName;
}
