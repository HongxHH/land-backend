package com.gov.landcheck.project.dto;

import java.math.BigDecimal;
import java.util.List;

import com.gov.landcheck.core.bo.entity.ContractInfo;
import com.gov.landcheck.core.bo.entity.LandParcel;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 合同及其地块信息DTO
 *
 * @author system
 * @date 2026/02/05
 */
@Data
@Schema(description = "合同及其地块信息")
public class ContractWithParcelsDTO {

    @Schema(description = "合同信息")
    private ContractInfo contractInfo;

    @Schema(description = "地块信息列表")
    private List<LandParcel> parcels;

    @Schema(description = "汇总信息")
    private ContractSummary summary;

    /**
     * 合同汇总信息
     */
    @Data
    @Schema(description = "合同汇总信息")
    public static class ContractSummary {

        @Schema(description = "总地块数量")
        private Integer totalParcels;

        @Schema(description = "总面积")
        private BigDecimal totalArea;

        @Schema(description = "住宅总面积")
        private BigDecimal totalResidentialArea;

        @Schema(description = "商业总面积")
        private BigDecimal totalCommercialArea;

        @Schema(description = "平均容积率")
        private BigDecimal averageFloorAreaRatio;
    }
}