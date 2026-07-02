package com.gov.landcheck.project.dto;

import com.gov.landcheck.core.bo.dto.BaseQueryDTO;
import com.gov.landcheck.core.config.query.QueryField;
import com.gov.landcheck.core.config.query.QueryType;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 规划复核表主表通用查询 DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "规划复核表主表通用查询请求")
public class PlanningReviewFormQueryDTO extends BaseQueryDTO {

    @QueryField("_id")
    @Schema(description = "规划复核表主表ID")
    private Long planningReviewFormId;

    @QueryField("project_id")
    @Schema(description = "项目ID")
    private Long projectId;

    @QueryField("file_record_id")
    @Schema(description = "文件记录ID")
    private Long fileRecordId;

    @QueryField("is_parsed")
    @Schema(description = "是否已解析回填（0否 1是）")
    private Integer isParsed;

    @QueryField(value = "project_name", type = QueryType.REGEX)
    @Schema(description = "项目名称（模糊）")
    private String projectName;

    @QueryField(value = "construction_unit", type = QueryType.REGEX)
    @Schema(description = "建设单位（模糊）")
    private String constructionUnit;
}
