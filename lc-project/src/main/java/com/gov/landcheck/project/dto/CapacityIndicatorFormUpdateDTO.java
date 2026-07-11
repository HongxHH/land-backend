package com.gov.landcheck.project.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 容量指标核查表更新 DTO（动态字段，非空则更新）
 */
@Data
@Schema(description = "容量指标核查表更新请求")
public class CapacityIndicatorFormUpdateDTO {

    @NotNull(message = "主表ID不能为空")
    @Schema(description = "主表ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(description = "是否已解析回填（0否 1是）")
    private Integer isParsed;

    @Schema(description = "本次报建建筑面积-合计（平方米）")
    private BigDecimal totalArea;

    @Schema(description = "本次报建建筑面积-商业类（平方米）")
    private BigDecimal commercialArea;

    @Schema(description = "本次报建建筑面积-住宅类（平方米）")
    private BigDecimal residentialArea;

    @Schema(description = "备注")
    private String remark;
}
