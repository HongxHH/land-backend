package com.gov.landcheck.project.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gov.landcheck.core.bo.R.AjaxJson;
import com.gov.landcheck.project.dto.CapacityIndicatorFormQueryDTO;
import com.gov.landcheck.project.dto.CapacityIndicatorFormUpdateDTO;
import com.gov.landcheck.project.service.CapacityIndicatorService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;

/**
 * 容量指标核查表接口
 */
@Tag(name = "容量指标核查表")
@RestController
@RequestMapping("/project")
public class CapacityIndicatorController {

    @Resource
    private CapacityIndicatorService capacityIndicatorService;

    @Operation(summary = "容量指标核查表通用查询", description = "多条件组合分页查询")
    @PostMapping("/capacity-indicator-forms/query")
    public AjaxJson queryCapacityIndicatorForms(
            @Parameter(description = "查询条件") @RequestBody CapacityIndicatorFormQueryDTO queryDTO) {
        return capacityIndicatorService.queryCapacityIndicatorForms(queryDTO);
    }

    @Operation(summary = "更新容量指标核查表", description = "按非空字段动态更新面积与备注")
    @PutMapping("/capacity-indicator-form/update")
    public AjaxJson updateCapacityIndicatorForm(
            @Parameter(description = "更新参数", required = true) @Valid @RequestBody CapacityIndicatorFormUpdateDTO updateDTO) {
        return capacityIndicatorService.updateCapacityIndicatorForm(updateDTO);
    }
}
