package com.gov.landcheck.project.vo;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "面积对比单行")
public class AreaLineVO {

    @Schema(description = "合同约定面积")
    private BigDecimal contractAgreedArea;

    @Schema(description = "计容面积")
    private BigDecimal buildableArea;

    @Schema(description = "差值（合同约定-计容）")
    private BigDecimal difference;
}
