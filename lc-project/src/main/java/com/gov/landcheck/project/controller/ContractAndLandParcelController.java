package com.gov.landcheck.project.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gov.landcheck.core.bo.R.AjaxJson;
import com.gov.landcheck.project.dto.ContractInfoQueryDTO;
import com.gov.landcheck.project.dto.ContractInfoUpdateDTO;
import com.gov.landcheck.project.dto.LandParcelCreateDTO;
import com.gov.landcheck.project.dto.LandParcelUpdateDTO;
import com.gov.landcheck.project.service.ContractAndLandParcelService;
import com.gov.landcheck.project.vo.ContractAreaSummaryVO;
import com.gov.landcheck.project.vo.ContractProjectStatsVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Tag(name = "合同与地块")
@RestController
@RequestMapping("/project")
public class ContractAndLandParcelController {

    @Resource
    private ContractAndLandParcelService contractAndLandParcelService;

    @Operation(summary = "合同信息通用查询", description = "根据多个条件组合查询合同信息，支持分页和排序")
    @PostMapping("/contracts/query")
    public AjaxJson queryContractInfos(@Parameter(description = "查询条件") @RequestBody ContractInfoQueryDTO queryDTO) {
        return contractAndLandParcelService.queryContractInfos(queryDTO);
    }

    @Operation(summary = "合同信息通用更新", description = "根据提供的字段动态更新合同信息")
    @PutMapping("/contract-info/update")
    public AjaxJson updateContractInfo(
            @Parameter(description = "合同信息更新信息", required = true) @Valid @RequestBody ContractInfoUpdateDTO updateDTO) {
        return contractAndLandParcelService.updateContractInfo(updateDTO);
    }

    @Operation(summary = "获取合同及其地块信息", description = "获取合同基础信息以及其下所有地块详情")
    @GetMapping("/contract/{contractId}/with-parcels")
    public AjaxJson getContractWithParcels(
            @Parameter(description = "合同ID", required = true) @PathVariable Long contractId) {
        return contractAndLandParcelService.getContractWithParcels(contractId);
    }

    @Operation(summary = "创建地块信息", description = "为指定合同创建新的地块信息")
    @PostMapping("/land-parcel/create")
    public AjaxJson createLandParcel(
            @Parameter(description = "地块创建信息", required = true) @Valid @RequestBody LandParcelCreateDTO createDTO) {
        return contractAndLandParcelService.createLandParcel(createDTO);
    }

    @Operation(summary = "地块信息通用更新", description = "根据提供字段动态更新地块")
    @PutMapping("/land-parcel/update")
    public AjaxJson updateLandParcel(
            @Parameter(description = "地块信息更新信息", required = true) @Valid @RequestBody LandParcelUpdateDTO updateDTO) {
        return contractAndLandParcelService.updateLandParcel(updateDTO);
    }

    @Operation(summary = "删除地块信息", description = "删除指定地块")
    @DeleteMapping("/land-parcel/{parcelId}")
    public AjaxJson deleteLandParcel(
            @Parameter(description = "地块ID", required = true) @NotNull(message = "地块ID不能为空") @PathVariable Long parcelId) {
        return contractAndLandParcelService.deleteLandParcel(parcelId);
    }

    @Operation(summary = "按项目ID获取合同约定面积汇总", description = "获取指定项目下所有合同约定面积合计")
    @GetMapping("/contracts/area-summary/{projectId}")
    public AjaxJson getContractAreaSummaryByProjectId(
            @Parameter(description = "项目ID", required = true) @PathVariable Long projectId) {
        ContractProjectStatsVO stats = contractAndLandParcelService.getContractProjectStatsByProjectId(projectId);
        ContractAreaSummaryVO vo = new ContractAreaSummaryVO();
        if (stats != null) {
            vo.setTotalArea(stats.getTotalArea());
            vo.setResidentialArea(stats.getResidentialArea());
            vo.setCommercialArea(stats.getCommercialArea());
        }
        return AjaxJson.getSuccessData(vo);
    }

}
