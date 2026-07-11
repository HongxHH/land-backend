package com.gov.landcheck.project.dto;

import java.util.List;

import com.gov.landcheck.core.bo.dto.BaseQueryResultDTO;
import com.gov.landcheck.project.vo.CapacityIndicatorFormVO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "容量指标核查表查询分页结果")
public class CapacityIndicatorFormQueryResultDTO extends BaseQueryResultDTO<CapacityIndicatorFormVO> {

    @Schema(description = "容量指标核查表列表")
    private List<CapacityIndicatorFormVO> records;
}
