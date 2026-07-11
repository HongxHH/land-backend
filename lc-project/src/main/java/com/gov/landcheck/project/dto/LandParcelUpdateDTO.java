package com.gov.landcheck.project.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 地块信息通用更新DTO
 *
 * @author system
 * @date 2026/02/05
 */
@Data
@Schema(description = "地块信息通用更新请求")
public class LandParcelUpdateDTO {
    @NotNull(message = "地块信息ID不能为空")
    @Schema(description = "地块信息ID（必填）", example = "1")
    private Long id;

    @Schema(description = "地块编号", example = "A")
    private String parcelCode;

    @Schema(description = "地块名称", example = "A地块")
    private String parcelName;

    @Schema(description = "规划用途：RESIDENTIAL(住宅)、COMMERCIAL(商业)、COMMERCIAL_AND_RESIDENTIAL(商业和住宅)", example = "RESIDENTIAL")
    private String plannedUse;

    @Schema(description = "地块总面积（㎡）", example = "25000.0000")
    private BigDecimal totalArea;

    @Schema(description = "容积率", example = "2.0")
    private BigDecimal floorAreaRatio;

    @Schema(description = "商住比（商业占比，范围0~1；例如 0.6 表示商业60%、住宅40%）", example = "0.6")
    private BigDecimal commercialResidentialRatio;

    @Schema(description = "备注")
    private String remark;

}