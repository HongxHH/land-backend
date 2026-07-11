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
import com.gov.landcheck.core.ratelimit.UserRateLimit;
import com.gov.landcheck.project.dto.RoomInfoCreateDTO;
import com.gov.landcheck.project.dto.RoomInfoQueryDTO;
import com.gov.landcheck.project.dto.RoomInfoUpdateDTO;
import com.gov.landcheck.project.dto.SurveyReportInfoQueryDTO;
import com.gov.landcheck.project.dto.SurveyReportInfoUpdateDTO;
import com.gov.landcheck.project.service.SurveyReportAndRoomService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;

/**
 * 实测报告与户室面积对照表相关接口
 */
@Tag(name = "实测报告与户室面积对照表")
@RestController
@RequestMapping("/project")
public class SurveyReportAndRoomController {

    @Resource
    private SurveyReportAndRoomService surveyReportAndRoomService;

    @Operation(summary = "根据项目ID查询已经解析成功的实测报告信息", description = "获取指定项目下已经解析成功的实测报告信息，返回SurveyReportInfoVO对象列表")
    @UserRateLimit(value = "summary-refresh-parsed", maxRequests = 5, windowSeconds = 60, message = "刷新文件列表过于频繁，请稍后再试")
    @GetMapping("/{projectId}/survey-reports/parsed")
    public AjaxJson getParsedSurveyReportInfoByProjectId(
            @Parameter(description = "项目ID", required = true) @PathVariable Long projectId) {
        return surveyReportAndRoomService.getParsedSurveyReportInfoByProjectId(projectId);
    }

    @Operation(summary = "根据项目ID和实测报告ID查询该报告的户室面积对照表", description = "获取指定项目和实测报告下的户室面积对照表，返回RoomInfoVO对象列表")
    @GetMapping("/{projectId}/survey-reports/{surveyReportId}/room-info")
    public AjaxJson getRoomInfoByProjectIdAndSurveyReportId(
            @Parameter(description = "项目ID", required = true) @PathVariable Long projectId,
            @Parameter(description = "实测报告ID", required = true) @PathVariable Long surveyReportId) {
        return surveyReportAndRoomService.getRoomInfoByProjectIdAndSurveyReportId(projectId, surveyReportId);
    }

    @Operation(summary = "实测报告信息通用查询", description = "根据多个条件组合查询实测报告信息，支持分页和排序")
    @PostMapping("/survey-reports/query")
    public AjaxJson querySurveyReportInfos(
            @Parameter(description = "查询条件") @RequestBody SurveyReportInfoQueryDTO queryDTO) {
        return surveyReportAndRoomService.querySurveyReportInfos(queryDTO);
    }

    @Operation(summary = "户室信息通用查询", description = "根据多个条件组合查询户室信息，支持分页和排序")
    @PostMapping("/room-info/query")
    public AjaxJson queryRoomInfos(@Parameter(description = "查询条件") @RequestBody RoomInfoQueryDTO queryDTO) {
        return surveyReportAndRoomService.queryRoomInfos(queryDTO);
    }

    @Operation(summary = "实测报告信息通用更新", description = "根据提供的字段动态更新实测报告信息，支持选择性更新")
    @PutMapping("/survey-report-info/update")
    public AjaxJson updateSurveyReportInfo(
            @Parameter(description = "实测报告信息更新信息", required = true) @Valid @RequestBody SurveyReportInfoUpdateDTO updateDTO) {
        return surveyReportAndRoomService.updateSurveyReportInfo(updateDTO);
    }

    @Operation(summary = "新增户室信息", description = "创建户室信息，必填 projectId、fileRecordId、surveyReportInfoId、usageCategory；usageCategory 须为 UsageCategoryEnum 枚举值")
    @PostMapping("/room-info/create")
    public AjaxJson createRoomInfo(
            @Parameter(description = "户室创建信息", required = true) @Valid @RequestBody RoomInfoCreateDTO createDTO) {
        return surveyReportAndRoomService.createRoomInfo(createDTO);
    }

    @Operation(summary = "户室信息通用更新", description = "根据提供的字段动态更新户室信息，支持选择性更新")
    @PutMapping("/room-info/update")
    public AjaxJson updateRoomInfo(
            @Parameter(description = "户室信息更新信息", required = true) @Valid @RequestBody RoomInfoUpdateDTO updateDTO) {
        return surveyReportAndRoomService.updateRoomInfo(updateDTO);
    }

    @Operation(summary = "删除户室信息", description = "删除指定的户室信息")
    @DeleteMapping("/room-info/{roomId}")
    public AjaxJson deleteRoomInfo(@Parameter(description = "户室ID", required = true) @PathVariable Long roomId) {
        return surveyReportAndRoomService.deleteRoomInfo(roomId);
    }

    @Operation(summary = "刷新单个实测报告数据", description = "按实测报告ID重新计算该报告的用途统计字段")
    @UserRateLimit(value = "survey-report-refresh", maxRequests = 1, windowSeconds = 5, message = "刷新实测报告过于频繁，请稍后再试")
    @PostMapping("/survey-report/{surveyReportId}/refresh")
    public AjaxJson refreshSurveyReport(
            @Parameter(description = "实测报告ID", required = true) @PathVariable Long surveyReportId) {
        return surveyReportAndRoomService.refreshSurveyReport(surveyReportId);
    }
}
