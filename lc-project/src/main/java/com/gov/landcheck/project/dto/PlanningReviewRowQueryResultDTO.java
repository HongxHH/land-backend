package com.gov.landcheck.project.dto;

import java.util.List;

import com.gov.landcheck.core.bo.entity.PlanningReviewRow;
import com.gov.landcheck.core.bo.dto.BaseQueryResultDTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "规划复核表行查询分页结果")
public class PlanningReviewRowQueryResultDTO extends BaseQueryResultDTO<PlanningReviewRow> {

    @Schema(description = "规划复核表行列表")
    private List<PlanningReviewRow> records;
}
