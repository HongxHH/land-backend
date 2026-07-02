package com.gov.landcheck.project.vo;

import java.math.BigDecimal;

import com.gov.landcheck.core.enums.ContractParseStatusEnum;
import com.gov.landcheck.core.enums.ProjectStatusEnum;
import com.gov.landcheck.core.enums.SurveyParseStatusEnum;
import com.gov.landcheck.core.enums.SurveyValidationStatusEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 项目详细项目信息视图对象（用于季度/年度报表等场景）
 */
@Data
@Schema(description = "项目信息详细查询结果视图对象")
public class ProjectDetailVO {

    @Schema(description = "项目ID", example = "1")
    private Long id;

    @Schema(description = "项目名称", example = "XX住宅小区项目")
    private String projectName;

    @Schema(description = "项目时间（ISO yyyy-MM-dd 自然日）", example = "2025-11-15")
    private String projectTime;

    @Schema(description = "项目编号", example = "PRJ2025001")
    private String projectCode;

    @Schema(description = "占地面积（㎡）", example = "50000.0000")
    private BigDecimal landArea;

    @Schema(description = "规划用途（住宅/商业等）", example = "住宅")
    private String plannedUse;

    @Schema(description = "实测报告文件数量", example = "10")
    private Integer surveyReportFileCount;

    @Schema(description = "合同文件数量", example = "3")
    private Integer contractFileCount;

    @Schema(description = "总的合同约定建筑面积（㎡），无记录则返回 null", example = "50000.0000")
    private BigDecimal contractAgreedTotalBuildingArea;

    @Schema(description = "总的合同约定商业面积（㎡），无记录则返回 null", example = "20000.0000")
    private BigDecimal contractAgreedCommercialArea;

    @Schema(description = "总的合同约定住宅面积（㎡），无记录则返回 null", example = "30000.0000")
    private BigDecimal contractAgreedResidentialArea;

    @Schema(description = "出让方（任意一个非空），无则返回 null")
    private String transferor;

    @Schema(description = "受让方（任意一个非空），无则返回 null")
    private String transferee;

    @Schema(description = "合同解析状态")
    private ContractParseStatusEnum contractParseStatus;

    @Schema(description = "实测解析状态")
    private SurveyParseStatusEnum surveyParseStatus;

    @Schema(description = "实测校验状态")
    private SurveyValidationStatusEnum surveyValidationStatus;

    @Schema(description = "项目整体状态（优先级：解析失败 > 校验失败 > 未解析）")
    private ProjectStatusEnum projectStatus;
}
