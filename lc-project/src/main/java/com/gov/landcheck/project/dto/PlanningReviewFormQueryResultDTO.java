package com.gov.landcheck.project.dto;

import java.util.List;

import com.gov.landcheck.core.bo.entity.PlanningReviewForm;
import com.gov.landcheck.core.bo.dto.BaseQueryResultDTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "规划复核表主表查询分页结果")
public class PlanningReviewFormQueryResultDTO extends BaseQueryResultDTO<PlanningReviewForm> {

    @Schema(description = "规划复核表主表列表")
    private List<PlanningReviewForm> records;
}
