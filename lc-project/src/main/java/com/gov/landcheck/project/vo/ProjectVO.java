package com.gov.landcheck.project.vo;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 项目信息视图对象
 * 用于向前端返回项目查询结果
 *
 * @author system
 * @date 2026/01/22
 */
@Data
@Schema(description = "项目信息视图对象")
public class ProjectVO {

    @Schema(description = "项目ID", example = "1")
    private Long id;

    @Schema(description = "项目名称", example = "XX住宅小区项目")
    private String projectName;

    @Schema(description = "项目编号", example = "PRJ2025001")
    private String projectCode;

    @Schema(description = "占地面积（㎡）", example = "50000.0000")
    private BigDecimal landArea;

    @Schema(description = "规划用途（住宅/商业等）", example = "住宅")
    private String plannedUse;
}