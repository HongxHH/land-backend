package com.gov.landcheck.project.dto;

import com.gov.landcheck.core.bo.dto.BaseQueryDTO;
import com.gov.landcheck.core.enums.FloorAreaTypeEnum;
import com.gov.landcheck.core.config.query.QueryField;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 户室信息通用查询DTO
 *
 * @author system
 * @date 2026/01/26
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "户室信息通用查询请求")
public class RoomInfoQueryDTO extends BaseQueryDTO {

    @QueryField("_id")
    @Schema(description = "户室信息ID")
    private Long roomInfoId;

    @QueryField("project_id")
    @Schema(description = "项目ID")
    private Long projectId;

    @QueryField("file_record_id")
    @Schema(description = "文件记录ID")
    private Long fileRecordId;

    @QueryField("survey_report_info_id")
    @Schema(description = "实测报告信息ID")
    private Long surveyReportInfoId;

    @QueryField("is_calculate")
    @Schema(description = "是否参与计算（0否 1是）")
    private Integer isCalculate;

    @QueryField("usage_category")
    @Schema(description = "用途类别：RESIDENTIAL/COMMERCIAL/MANAGEMENT/OTHER_BUILDABLE/COMMUNITY/OTHER_PUBLIC/UNKNOWN")
    private String usageCategory;

    @QueryField("floor_area_type")
    @Schema(description = "面积类型：BUILDABLE(计容)/NON_BUILDABLE(不计容)/UNKNOWN(未知)")
    private FloorAreaTypeEnum floorAreaType;

}