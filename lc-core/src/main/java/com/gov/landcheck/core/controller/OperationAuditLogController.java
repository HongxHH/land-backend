package com.gov.landcheck.core.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gov.landcheck.core.audit.OperationAuditService;
import com.gov.landcheck.core.bo.R.AjaxJson;
import com.gov.landcheck.core.bo.dto.OperationAuditLogQueryDTO;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.gov.landcheck.core.common.UserTypeConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;

/**
 * 操作审计日志查询接口
 * 提供按项目、操作人、操作类型、目标类型、时间范围等的通用分页查询。
 */
@Tag(name = "操作审计日志", description = "操作溯源审计日志的查询接口")
@RestController
@RequestMapping("/operation-audit")
@SaCheckRole(value = { UserTypeConstants.SUPER_ADMIN, UserTypeConstants.DEVELOPER }, mode = SaMode.OR)
public class OperationAuditLogController {

    @Resource
    private OperationAuditService operationAuditService;

    @Operation(summary = "审计日志通用查询", description = "根据多个条件组合查询操作审计日志，支持分页和排序；可按操作人、操作类型、目标类型、项目ID、时间范围等筛选")
    @PostMapping("/query")
    public AjaxJson queryAuditLogs(@Parameter(description = "查询条件") @RequestBody OperationAuditLogQueryDTO queryDTO) {
        return operationAuditService.queryAuditLogs(queryDTO);
    }
}
