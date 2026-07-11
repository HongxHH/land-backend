package com.gov.landcheck.project.dto;

import java.math.BigDecimal;

import org.hibernate.validator.constraints.Range;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 地块信息创建DTO。
 * 住宅/商业面积由系统根据 totalArea 与商住比（commercialResidentialRatio）自动计算，无需传入。
 *
 * @author system
 * @date 2026/02/05
 */
@Data
@Schema(description = "地块信息创建请求")
public class LandParcelCreateDTO {

    @NotNull(message = "合同ID不能为空")
    @Schema(description = "关联合同ID（必填）", example = "1")
    private Long contractId;

    @NotBlank(message = "地块编码不能为空")
    @Schema(description = "地块编号（必填）", example = "A")
    private String parcelCode;

    @Schema(description = "地块名称", example = "A地块")
    private String parcelName;

    @NotBlank(message = "规划用途不能为空")
    @Schema(description = "规划用途：RESIDENTIAL(住宅)、COMMERCIAL(商业)、COMMERCIAL_AND_RESIDENTIAL(商业和住宅)", example = "RESIDENTIAL")
    private String plannedUse;

    @NotNull(message = "地块总面积不能为空")
    @Schema(description = "地块总面积（㎡）", example = "25000.0000")
    private BigDecimal totalArea;


    @Schema(description = "容积率", example = "2.0")
    private BigDecimal floorAreaRatio;

    @NotNull(message = "商住比不能为空")
    @Range(min = 0, max = 1, message = "商住比必须在0到1之间")
    @Schema(description = "商住比（商业占比，范围0~1；例如 0.6 表示商业60%、住宅40%）", example = "0.6")
    private BigDecimal commercialResidentialRatio;

    @Schema(description = "备注")
    private String remark;

}