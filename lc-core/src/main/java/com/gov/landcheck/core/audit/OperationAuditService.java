package com.gov.landcheck.core.audit;

import com.gov.landcheck.core.bo.R.AjaxJson;
import com.gov.landcheck.core.bo.dto.OperationAuditLogQueryDTO;

/**
 * 操作审计服务：记录操作类型、目标、变更摘要到 operation_audit_log；
 * 提供审计日志通用查询。
 * 操作人、操作时间由本服务从 OperatorContext 与当前时间填充。
 */
public interface OperationAuditService {

    /**
     * 通用查询审计日志（分页、排序、按操作人/操作类型/目标类型/项目/时间范围等筛选）
     */
    AjaxJson queryAuditLogs(OperationAuditLogQueryDTO queryDTO);

    /**
     * 记录创建类操作
     */
    void recordCreate(String targetType, String targetId, Long projectId, Long contractId,
            String changeSummary, Object extra);

    /**
     * 记录更新类操作
     */
    void recordUpdate(String targetType, String targetId, Long projectId, Long contractId,
            String changeSummary, Object extra);

    /**
     * 记录删除类操作
     */
    void recordDelete(String targetType, String targetId, Long projectId, Long contractId,
            String changeSummary, Object extra);

    /**
     * 记录上传/移动等操作（extra 可带 file 相关字段）
     */
    void recordUploadOrMove(String operation, String targetType, String targetId, Long projectId, Long contractId,
            String changeSummary, Object extra);

    /**
     * 记录任意操作；operatorId/operatorName 为空时从 OperatorContext 读取。
     */
    void recordOperation(String operation, String targetType, String targetId, Long projectId, Long contractId,
            String changeSummary, Object extra, Long operatorId, String operatorName);
}
