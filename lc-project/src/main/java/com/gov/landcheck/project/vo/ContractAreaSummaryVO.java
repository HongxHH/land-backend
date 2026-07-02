package com.gov.landcheck.project.vo;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 合同约定面积汇总（按项目维度）
 */
@Data
@Schema(description = "合同约定面积汇总结果")
public class ContractAreaSummaryVO {

    @Schema(description = "总的合同约定建筑面积（㎡），无记录则返回 null")
    private BigDecimal totalArea;

    @Schema(description = "总的合同约定住宅面积（㎡），无记录则返回 null")
    private BigDecimal residentialArea;

    @Schema(description = "总的合同约定商业面积（㎡），无记录则返回 null")
    private BigDecimal commercialArea;
}

