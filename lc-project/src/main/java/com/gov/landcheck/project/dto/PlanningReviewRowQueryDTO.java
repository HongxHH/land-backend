package com.gov.landcheck.project.dto;

import com.gov.landcheck.core.bo.dto.BaseQueryDTO;
import com.gov.landcheck.core.config.query.QueryField;
import com.gov.landcheck.core.config.query.QueryType;
import com.gov.landcheck.core.enums.PlanningReviewAreaCategory;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 规划复核表行通用查询 DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "规划复核表行通用查询请求")
public class PlanningReviewRowQueryDTO extends BaseQueryDTO {

    @QueryField("_id")
    @Schema(description = "规划复核表行ID")
    private Long planningReviewRowId;

    @QueryField("project_id")
    @Schema(description = "项目ID")
    private Long projectId;

    @QueryField("file_record_id")
    @Schema(description = "文件记录ID")
    private Long fileRecordId;

    @QueryField("planning_review_form_id")
    @Schema(description = "规划复核表主表ID")
    private Long planningReviewFormId;

    @QueryField("area_category")
    @Schema(description = "面积类别：RESIDENTIAL/COMMERCIAL/OTHER_PENDING")
    private PlanningReviewAreaCategory areaCategory;

    @QueryField(value = "engineering_project", type = QueryType.REGEX)
    @Schema(description = "工程项目/楼栋名称（模糊）")
    private String engineeringProject;
}
