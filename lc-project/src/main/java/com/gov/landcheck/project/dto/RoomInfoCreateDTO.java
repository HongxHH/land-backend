package com.gov.landcheck.project.dto;

import java.math.BigDecimal;

import com.gov.landcheck.core.enums.FloorAreaTypeEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 户室信息创建 DTO
 *
 * @author system
 * @date 2026/03/06
 */
@Data
@Schema(description = "户室信息创建请求")
public class RoomInfoCreateDTO {

    @NotNull(message = "项目ID不能为空")
    @Schema(description = "所属项目ID（必填）", example = "1")
    private Long projectId;

    @NotNull(message = "文件记录ID不能为空")
    @Schema(description = "关联的文件记录ID（必填）", example = "1")
    private Long fileRecordId;

    @NotNull(message = "实测报告ID不能为空")
    @Schema(description = "关联的实测报告ID（必填）", example = "1")
    private Long surveyReportInfoId;

    @NotBlank(message = "用途类别不能为空")
    @Schema(description = "用途类别：RESIDENTIAL/COMMERCIAL/MANAGEMENT/OTHER_BUILDABLE/COMMUNITY/OTHER_PUBLIC/UNKNOWN", example = "RESIDENTIAL")
    private String usageCategory;

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

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "是否参与计算（0否 1是）", example = "1")
    private Integer isCalculate;

    @Schema(description = "面积类型：BUILDABLE(计容)/NON_BUILDABLE(不计容)/UNKNOWN(未知)，不传则按用途类别自动填充", example = "BUILDABLE")
    private FloorAreaTypeEnum floorAreaType;
}
