package com.gov.landcheck.project.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 规划复核表主表更新 DTO（动态字段，非空则更新）
 */
@Data
@Schema(description = "规划复核表主表更新请求")
public class PlanningReviewFormUpdateDTO {

    @NotNull(message = "规划复核表主表ID不能为空")
    @Schema(description = "主表ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(description = "是否已解析回填（0否 1是）")
    private Integer isParsed;

    @Schema(description = "项目名称")
    private String projectName;

    @Schema(description = "建设单位")
    private String constructionUnit;

    @Schema(description = "设计单位")
    private String designUnit;

    @Schema(description = "建设地点")
    private String constructionLocation;

    @Schema(description = "用地性质")
    private String landUseNature;

    @Schema(description = "联系人")
    private String contactPerson;

    @Schema(description = "联系电话")
    private String contactPhone;

    @Schema(description = "备注")
    private String remarks;
}
