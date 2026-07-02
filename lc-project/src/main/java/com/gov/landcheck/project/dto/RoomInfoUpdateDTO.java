package com.gov.landcheck.project.dto;

import java.math.BigDecimal;

import com.gov.landcheck.core.enums.FloorAreaTypeEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 户室信息通用更新DTO
 *
 * @author system
 * @date 2026/02/05
 */
@Data
@Schema(description = "户室信息通用更新请求")
public class RoomInfoUpdateDTO {

    @NotNull(message = "户室信息ID不能为空")
    @Schema(description = "户室信息ID（必填）", example = "1")
    private Long id;

    @Schema(description = "层次", example = "1")
    private String roomLevel;

    @Schema(description = "户室号", example = "101")
    private String roomNumber;

    @Schema(description = "建筑面积", example = "100.00")
    private BigDecimal buildingArea;

    @Schema(description = "套内面积", example = "90.00")
    private BigDecimal innerArea;

    @Schema(description = "阳台面积", example = "10.00")
    private BigDecimal balconyArea;

    @Schema(description = "分摊面积", example = "10.00")
    private BigDecimal sharedArea;

    @Schema(description = "户室结构", example = "1室1厅1卫")
    private String roomStructure;

    @Schema(description = "用途", example = "住宅")
    private String roomUsage;

    @Schema(description = "备注", example = "备注")
    private String remark;

    @Schema(description = "是否参与计算（0否 1是）", example = "1")
    private Integer isCalculate;

    @Schema(description = "用途类别：RESIDENTIAL/COMMERCIAL/MANAGEMENT/OTHER_BUILDABLE/COMMUNITY/OTHER_PUBLIC/UNKNOWN", example = "RESIDENTIAL")
    private String usageCategory;

    @Schema(description = "面积类型：BUILDABLE(计容)/NON_BUILDABLE(不计容)/UNKNOWN(未知)", example = "BUILDABLE")
    private FloorAreaTypeEnum floorAreaType;

}