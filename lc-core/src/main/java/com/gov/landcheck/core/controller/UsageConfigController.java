package com.gov.landcheck.core.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
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
import com.gov.landcheck.core.bo.entity.UnknownUsageRecord;
import com.gov.landcheck.core.bo.entity.UsageConfig;
import com.gov.landcheck.core.bo.vo.UnknownUsageRecordVO;
import com.gov.landcheck.core.bo.dto.UsageConfigRelatedFileQueryResultDTO;
import com.gov.landcheck.core.service.UnknownUsageRecordService;
import com.gov.landcheck.core.service.UsageConfigService;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.gov.landcheck.core.common.UserTypeConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 用途配置管理控制器
 * 提供用途配置和未知用途记录的管理接口
 *
 * @author system
 * @date 2025/01/21
 */
@Tag(name = "用途配置管理", description = "用途配置和未知用途记录管理接口")
@RestController
@RequestMapping("/usage-config")
public class UsageConfigController {

    @Autowired
    private UsageConfigService usageConfigService;

    @Autowired
    private UnknownUsageRecordService unknownUsageRecordService;

    // ==================== 用途配置管理 ====================

    @Operation(summary = "获取所有启用的用途配置")
    @GetMapping("/list")
    public AjaxJson getAllEnabledConfigs() {
        List<UsageConfig> configs = usageConfigService.getAllEnabledConfigs();
        return AjaxJson.getSuccessData(configs);
    }

    @Operation(summary = "根据用途ID获取用途配置")
    @GetMapping("/{id}")
    public AjaxJson getById(@PathVariable Long id) {
        UsageConfig config = usageConfigService.getById(id);
        return AjaxJson.getSuccessData(config);
    }

    @Operation(summary = "根据用途类别获取配置")
    @GetMapping("/category/{usageCategory}")
    public AjaxJson getByUsageCategory(
            @Parameter(description = "用途类别(RESIDENTIAL/COMMERCIAL/MANAGEMENT/OTHER_BUILDABLE/COMMUNITY/OTHER_PUBLIC)") @PathVariable String usageCategory) {
        List<UsageConfig> configs = usageConfigService.getByUsageCategory(usageCategory);
        return AjaxJson.getSuccessData(configs);
    }

    @Operation(summary = "根据是否计容获取配置")
    @GetMapping("/area-type/{floorAreaType}")
    public AjaxJson getByFloorAreaType(
            @Parameter(description = "面积类型(BUILDABLE/NON_BUILDABLE)") @PathVariable String floorAreaType) {
        List<UsageConfig> configs = usageConfigService.getByFloorAreaType(floorAreaType);
        return AjaxJson.getSuccessData(configs);
    }

    @Operation(summary = "新增用途配置")
    @PostMapping
    @SaCheckRole(value = { UserTypeConstants.SUPER_ADMIN, UserTypeConstants.DEVELOPER }, mode = SaMode.OR)
    public AjaxJson create(@RequestBody UsageConfig usageConfig) {
        UsageConfig saved = usageConfigService.create(usageConfig);
        if (saved.getUsagePattern() != null && saved.getStatus() != null && saved.getStatus() == 1) {
            unknownUsageRecordService.closePendingIfUsageNameMatches(
                    saved.getUsagePattern(), "admin", "手动新增用途配置");
        }
        return AjaxJson.getSuccessData(saved);
    }

    @Operation(summary = "更新用途配置")
    @PutMapping("/{id}")
    @SaCheckRole(value = { UserTypeConstants.SUPER_ADMIN, UserTypeConstants.DEVELOPER }, mode = SaMode.OR)
    public AjaxJson update(@PathVariable Long id, @RequestBody UsageConfig usageConfig) {
        usageConfig.setId(id);
        UsageConfig saved = usageConfigService.update(usageConfig);
        if (saved.getUsagePattern() != null && saved.getStatus() != null && saved.getStatus() == 1) {
            unknownUsageRecordService.closePendingIfUsageNameMatches(
                    saved.getUsagePattern(), "admin", "手动更新用途配置");
        }
        return AjaxJson.getSuccessData(saved);
    }

    @Operation(summary = "删除用途配置")
    @DeleteMapping("/{id}")
    @SaCheckRole(value = { UserTypeConstants.SUPER_ADMIN, UserTypeConstants.DEVELOPER }, mode = SaMode.OR)
    public AjaxJson delete(@PathVariable Long id) {
        usageConfigService.deleteById(id);
        return AjaxJson.getSuccess();
    }

    @Operation(summary = "分页查询命中指定用途配置的文件列表")
    @GetMapping("/{id}/related-files")
    public AjaxJson listRelatedFiles(
            @Parameter(description = "用途配置ID") @PathVariable Long id,
            @Parameter(description = "页码，从 1 开始") @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页大小，最大 50") @RequestParam(required = false, defaultValue = "10") Integer pageSize,
            @Parameter(description = "按项目名/文件名模糊搜索") @RequestParam(required = false) String keyword) {
        UsageConfig config = usageConfigService.getById(id);
        if (config == null) {
            return AjaxJson.getError("用途配置不存在");
        }
        UsageConfigRelatedFileQueryResultDTO result = usageConfigService.listRelatedFiles(config, pageNum,
                pageSize, keyword);
        return AjaxJson.getSuccessData(result);
    }

    // ==================== 未知用途记录管理 ====================

    @Operation(summary = "获取所有待处理的未知用途记录（含最近来源文件名与项目名）")
    @GetMapping("/unknown/pending")
    public AjaxJson getPendingRecords() {
        List<UnknownUsageRecordVO> records = unknownUsageRecordService.listPendingRecordVos();
        return AjaxJson.getSuccessData(records);
    }

    @Operation(summary = "根据项目ID获取该项目未处理的未知用途记录（含最近来源文件名与项目名）")
    @GetMapping("/unknown/project/{projectId}")
    public AjaxJson getByProjectId(@PathVariable Long projectId) {
        List<UnknownUsageRecordVO> records = unknownUsageRecordService.listRecordVosByProjectId(projectId);
        return AjaxJson.getSuccessData(records);
    }

    @Operation(summary = "根据文件ID获取未知用途记录")
    @GetMapping("/unknown/file/{fileRecordId}")
    public AjaxJson getByFileRecordId(@PathVariable Long fileRecordId) {
        List<UnknownUsageRecord> records = unknownUsageRecordService.getByFileRecordId(fileRecordId);
        return AjaxJson.getSuccessData(records);
    }

    @Operation(summary = "基于未知用途创建新用途配置")
    @PostMapping("/create-from-unknown")
    @SaCheckRole(value = { UserTypeConstants.SUPER_ADMIN, UserTypeConstants.DEVELOPER }, mode = SaMode.OR)
    public AjaxJson createFromUnknownUsage(
            @Parameter(description = "未知用途记录ID") @RequestParam Long unknownUsageId,
            @Parameter(description = "用途类别(RESIDENTIAL/COMMERCIAL/MANAGEMENT/OTHER_BUILDABLE/COMMUNITY/OTHER_PUBLIC)") @RequestParam String usageCategory,
            @Parameter(description = "面积类型(BUILDABLE/NON_BUILDABLE)") @RequestParam String floorAreaType,
            @Parameter(description = "是否使用正则匹配(0否 1是)") @RequestParam(required = false, defaultValue = "0") Integer isRegex,
            @Parameter(description = "匹配优先级，数值越小优先级越高") @RequestParam(required = false, defaultValue = "1000") Integer priority) {

        return unknownUsageRecordService.createFromUnknownUsage(unknownUsageId, usageCategory, floorAreaType, isRegex,
                priority);

    }
}