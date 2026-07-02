package com.gov.landcheck.project.dto;

import com.gov.landcheck.core.bo.dto.BaseQueryDTO;
import com.gov.landcheck.core.config.query.QueryField;
import com.gov.landcheck.core.config.query.QueryType;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目信息通用查询DTO
 *
 * @author system
 * @date 2026/01/26
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "项目信息通用查询请求")
public class ProjectQueryDTO extends BaseQueryDTO {

    @QueryField("_id")
    @Schema(description = "项目ID")
    private Long projectId;

    @QueryField(value = "project_name", type = QueryType.REGEX)
    @Schema(description = "项目名称（支持模糊查询）")
    private String projectName;

    @QueryField(value = "project_time", type = QueryType.REGEX)
    @Schema(description = "项目时间（ISO yyyy-MM-dd 子串模糊匹配，如 2025-11）")
    private String projectTime;

    @Schema(description = "项目时间范围-起始（含），ISO yyyy-MM-dd；与 projectTimeEnd 同时传入时按时间范围查询", example = "2025-01-01")
    private String projectTimeStart;

    @Schema(description = "项目时间范围-结束（含），ISO yyyy-MM-dd；与 projectTimeStart 同时传入时按时间范围查询", example = "2025-12-31")
    private String projectTimeEnd;

}