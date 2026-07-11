package com.gov.landcheck.project.dto;

import java.util.List;

import com.gov.landcheck.core.bo.dto.BaseQueryResultDTO;
import com.gov.landcheck.project.vo.ProjectDetailVO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目详细查询分页结果DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "项目信息详细查询分页结果")
public class ProjectDetailQueryResultDTO extends BaseQueryResultDTO<ProjectDetailVO> {

    @Schema(description = "项目详细列表")
    private List<ProjectDetailVO> records;
}

