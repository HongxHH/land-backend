package com.gov.landcheck.project.vo;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 合同按项目的统计信息（既用于项目详细查询，也可用于对外的面积汇总接口）
 */
@Data
@Schema(description = "合同按项目统计信息")
public class ContractProjectStatsVO {

    @Schema(description = "总的合同约定建筑面积（㎡），无记录则返回 null")
    private BigDecimal totalArea;

    @Schema(description = "总的合同约定住宅面积（㎡），无记录则返回 null")
    private BigDecimal residentialArea;

    @Schema(description = "总的合同约定商业面积（㎡），无记录则返回 null")
    private BigDecimal commercialArea;

    @Schema(description = "出让方（任意一个非空），无则返回 null")
    private String transferor;

    @Schema(description = "受让方（任意一个非空），无则返回 null")
    private String transferee;
}

