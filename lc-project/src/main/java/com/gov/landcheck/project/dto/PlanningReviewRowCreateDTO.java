package com.gov.landcheck.project.dto;

import java.math.BigDecimal;

import com.gov.landcheck.core.enums.PlanningReviewAreaCategory;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 规划复核表行创建 DTO
 */
@Data
@Schema(description = "规划复核表行创建请求")
public class PlanningReviewRowCreateDTO {

    @NotNull(message = "项目ID不能为空")
    @Schema(description = "所属项目ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long projectId;

    @NotNull(message = "文件记录ID不能为空")
    @Schema(description = "关联 file_record.id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long fileRecordId;

    @NotNull(message = "规划复核表主表ID不能为空")
    @Schema(description = "关联 planning_review_form.id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long planningReviewFormId;

    @Schema(description = "表格序号")
    private Integer rowIndex;

    @Schema(description = "工程项目（楼栋/建筑名称）")
    private String engineeringProject;

    @Schema(description = "建筑性质（原文）")
    private String buildingNatureRaw;

    @Schema(description = "面积类别：RESIDENTIAL/COMMERCIAL/OTHER_PENDING")
    private PlanningReviewAreaCategory areaCategory;

    @Schema(description = "建设性质")
    private String constructionNature;

    @Schema(description = "栋数")
    private Integer buildingCount;

    @Schema(description = "地上层数")
    private Integer aboveGroundFloors;

    @Schema(description = "地下层数")
    private Integer belowGroundFloors;

    @Schema(description = "高度（m）")
    private BigDecimal heightM;

    @Schema(description = "基底面积（㎡）")
    private BigDecimal baseAreaM2;

    @Schema(description = "住宅-住宅面积")
    private BigDecimal residentialResidentialArea;

    @Schema(description = "住宅-酒店公寓面积")
    private BigDecimal residentialHotelApartmentArea;

    @Schema(description = "住宅-其他面积")
    private BigDecimal residentialOtherArea;

    @Schema(description = "非居住-地上-商业")
    private BigDecimal nrAboveCommercial;

    @Schema(description = "非居住-地上-车库")
    private BigDecimal nrAboveGarage;

    @Schema(description = "非居住-地上-其他")
    private BigDecimal nrAboveOther;

    @Schema(description = "非居住-地下-商业")
    private BigDecimal nrBelowCommercial;

    @Schema(description = "非居住-地下-配套")
    private BigDecimal nrBelowSupporting;

    @Schema(description = "非居住-地下-其他")
    private BigDecimal nrBelowOther;

    @Schema(description = "地上建筑面积")
    private BigDecimal aboveGroundArea;

    @Schema(description = "地下建筑面积")
    private BigDecimal belowGroundArea;

    @Schema(description = "总建筑面积")
    private BigDecimal totalArea;

    @Schema(description = "计容面积-地上")
    private BigDecimal farAboveGround;

    @Schema(description = "计容面积-地下")
    private BigDecimal farBelowGround;
}
