package com.gov.landcheck.project.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gov.landcheck.core.bo.R.AjaxJson;
import com.gov.landcheck.project.dto.ProjectPartySummaryFormQueryDTO;
import com.gov.landcheck.project.dto.ProjectPartySummaryFormUpdateDTO;
import com.gov.landcheck.project.service.ProjectPartySummaryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;

/**
 * 项目方实测汇总主表接口
 */
@Tag(name = "项目方实测汇总表")
@RestController
@RequestMapping("/project")
public class ProjectPartySummaryController {

    @Resource
    private ProjectPartySummaryService projectPartySummaryService;

    @Operation(summary = "项目方实测汇总主表通用查询", description = "多条件组合分页查询主表")
    @PostMapping("/project-party-summary-forms/query")
    public AjaxJson queryProjectPartySummaryForms(
            @Parameter(description = "查询条件") @RequestBody ProjectPartySummaryFormQueryDTO queryDTO) {
        return projectPartySummaryService.queryProjectPartySummaryForms(queryDTO);
    }

    @Operation(summary = "更新项目方实测汇总主表", description = "按非空字段动态更新")
    @PutMapping("/project-party-summary-form/update")
    public AjaxJson updateProjectPartySummaryForm(
            @Parameter(description = "更新参数", required = true) @Valid @RequestBody ProjectPartySummaryFormUpdateDTO updateDTO) {
        return projectPartySummaryService.updateProjectPartySummaryForm(updateDTO);
    }
}
