package com.gov.landcheck.project.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gov.landcheck.core.bo.R.AjaxJson;
import com.gov.landcheck.project.dto.ProjectQueryDTO;
import com.gov.landcheck.project.dto.ProjectUpdateDTO;
import com.gov.landcheck.project.service.ProjectService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * 项目相关接口（项目 CRUD、列表、查询、刷新实测报告、删除）
 */
@Tag(name = "项目")
@RestController
@RequestMapping("/project")
public class ProjectController {

    @Resource
    private ProjectService projectService;

    @Operation(summary = "根据项目ID查询项目信息", description = "通过项目ID获取项目详细信息，返回ProjectVO对象")
    @GetMapping("/{projectId}")
    public AjaxJson getProjectById(@Parameter(description = "项目ID", required = true) @PathVariable Long projectId) {
        return projectService.getProjectById(projectId);
    }

    @Operation(summary = "项目信息通用查询", description = "根据多个条件组合查询项目信息，支持分页和排序")
    @PostMapping("/projects/query")
    public AjaxJson queryProjects(@Parameter(description = "查询条件") @RequestBody ProjectQueryDTO queryDTO) {
        return projectService.queryProjects(queryDTO);
    }

    @Operation(summary = "项目信息详细查询", description = "根据多个条件组合查询项目信息详情，支持分页和排序（用于报表生成）")
    @PostMapping("/projects/query/detail")
    public AjaxJson queryProjectDetails(@Parameter(description = "查询条件") @RequestBody ProjectQueryDTO queryDTO) {
        // 手工验证要点（对应项目状态优先级与口径）：
        // 1) 同一项目存在解析失败（contract/survey 的
        // file_state=PARSE_FAIL/UNPARSEABLE）且同时存在校验失败：projectStatus 必须为 PARSE_FAILED
        // 2) 合同解析完成，实测已解析且校验失败（SurveyReportInfo.isParsed=1 且
        // isVerified=0）：projectStatus 必须为 SURVEY_VALIDATION_FAILED
        // 3) 合同/实测存在但尚未全部解析完成（file_state != PARSE_COMPLETE），且无解析失败：projectStatus 必须为
        // UNPARSED
        // 4) 多份合同 transferor/transferee 混合时：返回 VO 中任意一个非空值
        // 5) 合同面积汇总：contract_info 三个面积字段求和；若无非空贡献则返回 null
        return projectService.queryProjectDetails(queryDTO);
    }

    @Operation(summary = "创建项目", description = "创建新项目，传入项目名称和项目时间")
    @PostMapping("/create")
    public AjaxJson createProject(
            @Parameter(description = "项目名称", required = true) @RequestParam("projectName") String projectName,
            @Parameter(description = "项目时间（ISO yyyy-MM-dd 自然日，例如：2025-11-15）", required = true) @RequestParam("projectTime") String projectTime) {
        return projectService.createProject(projectName, projectTime);
    }

    @Operation(summary = "项目信息通用更新", description = "根据提供的字段动态更新项目信息，支持选择性更新")
    @PutMapping("/update")
    public AjaxJson updateProject(
            @Parameter(description = "项目更新信息", required = true) @Valid @RequestBody ProjectUpdateDTO updateDTO) {
        return projectService.updateProject(updateDTO);
    }

    @Operation(summary = "刷新项目实测报告数据", description = "重新计算项目中所有实测报告的用途统计字段，用于配置更新后重新计算")
    @PostMapping("/{projectId}/refresh-survey-reports")
    public AjaxJson refreshProjectSurveyReports(
            @Parameter(description = "项目ID", required = true) @PathVariable Long projectId) {
        return projectService.refreshProjectSurveyReports(projectId);
    }

    @Operation(summary = "查询项目三组来源面积对比", description = "返回系统计算、项目方声明、规划口径的建筑/商业/住宅三行对比")
    @GetMapping("/{projectId}/area-comparison/triple-lines")
    public AjaxJson queryAreaComparison(
            @Parameter(description = "项目ID", required = true) @PathVariable Long projectId) {
        return projectService.queryAreaComparison(projectId);
    }

    @Operation(summary = "删除项目", description = "删除指定项目，要求项目下无文件且无进行中的文件任务")
    @DeleteMapping("/{projectId}")
    public AjaxJson deleteProject(
            @Parameter(description = "项目ID", required = true) @NotNull(message = "项目ID不能为空") @PathVariable Long projectId) {
        return projectService.deleteProject(projectId);
    }
}
