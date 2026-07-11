package com.gov.landcheck.project.dto;

import java.util.List;

import com.gov.landcheck.core.bo.dto.BaseQueryResultDTO;
import com.gov.landcheck.project.vo.ProjectPartySummaryFormVO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "项目方实测汇总主表查询分页结果")
public class ProjectPartySummaryFormQueryResultDTO extends BaseQueryResultDTO<ProjectPartySummaryFormVO> {

    @Schema(description = "项目方实测汇总主表列表")
    private List<ProjectPartySummaryFormVO> records;
}
