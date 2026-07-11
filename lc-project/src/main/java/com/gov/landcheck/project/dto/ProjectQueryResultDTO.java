package com.gov.landcheck.project.dto;

import com.gov.landcheck.core.bo.entity.Project;
import com.gov.landcheck.core.bo.dto.BaseQueryResultDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 项目查询结果DTO
 *
 * @author system
 * @date 2026/01/26
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "项目查询分页结果")
public class ProjectQueryResultDTO extends BaseQueryResultDTO<Project> {

    @Schema(description = "项目列表")
    private List<Project> records;

}