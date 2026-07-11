package com.gov.landcheck.core.bo.entity;

import java.math.BigDecimal;

import org.springframework.data.mongodb.core.mapping.Field;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 项目方声明口径底部三行汇总。
 */
@Data
@Schema(description = "项目方声明汇总三行")
public class ProjectPartyDeclaredTotals {

    @Field(name = "contract_agreed_total_building_area")
    @Schema(description = "合同约定建筑面积")
    private BigDecimal contractAgreedTotalBuildingArea;

    @Field(name = "buildable_total_building_area")
    @Schema(description = "计容建筑面积")
    private BigDecimal buildableTotalBuildingArea;

    @Field(name = "difference_total_building_area")
    @Schema(description = "建筑面积差值（合同约定-计容）")
    private BigDecimal differenceTotalBuildingArea;

    @Field(name = "contract_agreed_commercial_area")
    @Schema(description = "合同约定商业面积")
    private BigDecimal contractAgreedCommercialArea;

    @Field(name = "buildable_commercial_area")
    @Schema(description = "计容商业面积")
    private BigDecimal buildableCommercialArea;

    @Field(name = "difference_commercial_area")
    @Schema(description = "商业面积差值（合同约定-计容）")
    private BigDecimal differenceCommercialArea;

    @Field(name = "contract_agreed_residential_area")
    @Schema(description = "合同约定住宅面积")
    private BigDecimal contractAgreedResidentialArea;

    @Field(name = "buildable_residential_area")
    @Schema(description = "计容住宅面积")
    private BigDecimal buildableResidentialArea;

    @Field(name = "difference_residential_area")
    @Schema(description = "住宅面积差值（合同约定-计容）")
    private BigDecimal differenceResidentialArea;

    /**
     * 是否存在任一非空汇总字段（用于解析/回填：允许仅建筑面积等非九项全齐场景）。
     */
    public boolean hasAnyDeclaredField() {
        return contractAgreedTotalBuildingArea != null
                || buildableTotalBuildingArea != null
                || differenceTotalBuildingArea != null
                || contractAgreedCommercialArea != null
                || buildableCommercialArea != null
                || differenceCommercialArea != null
                || contractAgreedResidentialArea != null
                || buildableResidentialArea != null
                || differenceResidentialArea != null;
    }

    /**
     * 当 Excel 无「差值」列时，用合同约定与计容面积补算差值（合同约定 − 计容）。
     * 已有差值（含从表格或孤儿数字行解析出的值）不会被覆盖。
     */
    public void fillMissingDifferences() {
        if (differenceTotalBuildingArea == null) {
            differenceTotalBuildingArea = subtract(contractAgreedTotalBuildingArea, buildableTotalBuildingArea);
        }
        if (differenceCommercialArea == null) {
            differenceCommercialArea = subtract(contractAgreedCommercialArea, buildableCommercialArea);
        }
        if (differenceResidentialArea == null) {
            differenceResidentialArea = subtract(contractAgreedResidentialArea, buildableResidentialArea);
        }
    }

    private static BigDecimal subtract(BigDecimal contract, BigDecimal buildable) {
        if (contract == null || buildable == null) {
            return null;
        }
        return contract.subtract(buildable);
    }
}
