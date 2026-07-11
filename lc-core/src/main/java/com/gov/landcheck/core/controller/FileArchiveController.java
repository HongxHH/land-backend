package com.gov.landcheck.core.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gov.landcheck.core.bo.R.AjaxJson;
import com.gov.landcheck.core.bo.dto.CreateArchiveDTO;
import com.gov.landcheck.core.bo.dto.UpdateArchiveDTO;
import com.gov.landcheck.core.bo.entity.FileArchive;
import com.gov.landcheck.core.common.MessageConstant;
import com.gov.landcheck.core.service.IFileArchiveService;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.gov.landcheck.core.common.UserTypeConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * 归档夹管理接口
 * 归档夹归属「文件」域：用于对项目下的文件做文件夹式归类，与 FileRecord.archiveId 强关联，
 * 因此放在 file 模块。若需「项目视角」的入口，可在 project 模块做转发或聚合。
 * <p>
 * 路径：/file/project/{projectId}/archives
 */
@Tag(name = "归档夹管理")
@RestController
@RequestMapping("/file/project")
public class FileArchiveController {

    @Resource
    private IFileArchiveService fileArchiveService;

    @GetMapping("/{projectId}/archives")
    @Operation(summary = "获取项目下所有归档夹", description = "含默认目录（合同、实测报告、规划复核表、项目方实测汇总表、其他未归档文件）及用户自定义归档夹，按排序返回")
    public AjaxJson listArchives(@Parameter(description = "项目ID") @PathVariable Long projectId) {
        try {
            List<FileArchive> list = fileArchiveService.listByProjectId(projectId);
            return AjaxJson.getSuccess("获取归档夹列表成功").setData(list);
        } catch (Exception e) {
            return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "获取归档夹列表失败");
        }
    }

    @PostMapping("/{projectId}/archives")
    @Operation(summary = "在项目下新建归档夹", description = "新建用户自定义归档夹（如现场图片、红线文件），kind 为空，与系统默认目录区分")
    @SaCheckRole(value = { UserTypeConstants.SUPER_ADMIN, UserTypeConstants.DEVELOPER }, mode = SaMode.OR)
    public AjaxJson createArchive(
            @Valid @RequestBody CreateArchiveDTO dto) {
        return fileArchiveService.createArchive(dto);
    }

    @DeleteMapping("/delete-archives/{projectId}/{archiveId}")
    @Operation(summary = "删除归档夹", description = "仅支持删除用户自定义归档夹；若该归档夹下仍有文件，需先删除或移出所有文件后再删除")
    @SaCheckRole(value = { UserTypeConstants.SUPER_ADMIN, UserTypeConstants.DEVELOPER }, mode = SaMode.OR)
    public AjaxJson deleteArchive(
            @Parameter(description = "项目ID") @PathVariable @NotNull(message = "项目ID不能为空") Long projectId,
            @Parameter(description = "归档夹ID") @PathVariable @NotNull(message = "归档夹ID不能为空") Long archiveId) {
        return fileArchiveService.deleteArchive(projectId, archiveId);
    }

    @PostMapping("/update-archive")
    @Operation(summary = "更新归档夹", description = "仅可修改归档夹名称和排序值，其他字段不可修改")
    @SaCheckRole(value = { UserTypeConstants.SUPER_ADMIN, UserTypeConstants.DEVELOPER }, mode = SaMode.OR)
    public AjaxJson updateArchive(@RequestBody @Valid UpdateArchiveDTO dto) {
        return fileArchiveService.updateArchive(dto);
    }
}
