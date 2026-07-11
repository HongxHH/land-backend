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
import com.gov.landcheck.project.dto.PlanningReviewFormQueryDTO;
import com.gov.landcheck.project.dto.PlanningReviewFormUpdateDTO;
import com.gov.landcheck.project.dto.PlanningReviewRowCreateDTO;
import com.gov.landcheck.project.dto.PlanningReviewRowQueryDTO;
import com.gov.landcheck.project.dto.PlanningReviewRowUpdateDTO;
import com.gov.landcheck.project.service.PlanningReviewAndRowService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;

/**
 * 规划复核表主表与子行接口
 */
@Tag(name = "规划复核表")
@RestController
@RequestMapping("/project")
public class PlanningReviewAndRowController {

    @Resource
    private PlanningReviewAndRowService planningReviewAndRowService;

    @Operation(summary = "规划复核表主表通用查询", description = "多条件组合分页查询主表")
    @PostMapping("/planning-review-forms/query")
    public AjaxJson queryPlanningReviewForms(
            @Parameter(description = "查询条件") @RequestBody PlanningReviewFormQueryDTO queryDTO) {
        return planningReviewAndRowService.queryPlanningReviewForms(queryDTO);
    }

    @Operation(summary = "规划复核表行通用查询", description = "多条件组合分页查询子行")
    @PostMapping("/planning-review-rows/query")
    public AjaxJson queryPlanningReviewRows(
            @Parameter(description = "查询条件") @RequestBody PlanningReviewRowQueryDTO queryDTO) {
        return planningReviewAndRowService.queryPlanningReviewRows(queryDTO);
    }

    @Operation(summary = "按项目与主表查询全部子行", description = "返回指定主表下全部行（列表缓存）")
    @GetMapping("/{projectId}/planning-review-forms/{formId}/rows")
    public AjaxJson getPlanningReviewRowsByProjectAndFormId(
            @Parameter(description = "项目ID", required = true) @PathVariable Long projectId,
            @Parameter(description = "规划复核表主表ID", required = true) @PathVariable Long formId) {
        return planningReviewAndRowService.getPlanningReviewRowsByProjectAndFormId(projectId, formId);
    }

    @Operation(summary = "更新规划复核表主表", description = "按非空字段动态更新表头信息")
    @PutMapping("/planning-review-form/update")
    public AjaxJson updatePlanningReviewForm(
            @Parameter(description = "更新参数", required = true) @Valid @RequestBody PlanningReviewFormUpdateDTO updateDTO) {
        return planningReviewAndRowService.updatePlanningReviewForm(updateDTO);
    }

    @Operation(summary = "新增规划复核表行", description = "必填 projectId、fileRecordId、planningReviewFormId；须与主表项目及文件一致")
    @PostMapping("/planning-review-row/create")
    public AjaxJson createPlanningReviewRow(
            @Parameter(description = "创建参数", required = true) @Valid @RequestBody PlanningReviewRowCreateDTO createDTO) {
        return planningReviewAndRowService.createPlanningReviewRow(createDTO);
    }

    @Operation(summary = "更新规划复核表行", description = "按非空字段动态更新")
    @PutMapping("/planning-review-row/update")
    public AjaxJson updatePlanningReviewRow(
            @Parameter(description = "更新参数", required = true) @Valid @RequestBody PlanningReviewRowUpdateDTO updateDTO) {
        return planningReviewAndRowService.updatePlanningReviewRow(updateDTO);
    }

    @Operation(summary = "删除规划复核表行")
    @DeleteMapping("/planning-review-row/{rowId}")
    public AjaxJson deletePlanningReviewRow(
            @Parameter(description = "行ID", required = true) @PathVariable Long rowId) {
        return planningReviewAndRowService.deletePlanningReviewRow(rowId);
    }
}
