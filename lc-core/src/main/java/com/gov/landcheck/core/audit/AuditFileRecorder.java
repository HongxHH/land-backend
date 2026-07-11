package com.gov.landcheck.core.audit;

import com.gov.landcheck.core.bo.entity.FileRecord;

/**
 * 文件相关审计记录辅助类。
 */
public final class AuditFileRecorder {

    private AuditFileRecorder() {
    }

    public static void recordFileOperation(OperationAuditService auditService, String operation, FileRecord fileRecord,
            Object extra, Long operatorId, String operatorName) {
        if (auditService == null || fileRecord == null || fileRecord.getId() == null) {
            return;
        }
        try {
            String changeSummary = AuditDiffHelper.summary(fileRecord, TargetType.FILE);
            auditService.recordOperation(operation, TargetType.FILE.getValue(), String.valueOf(fileRecord.getId()),
                    fileRecord.getProjectId(), null, changeSummary, extra, operatorId, operatorName);
        } catch (Exception ignored) {
            // 审计失败不影响主流程
        }
    }

    public static void recordEntityCreate(OperationAuditService auditService, TargetType targetType, Object entity,
            Long operatorId, String operatorName) {
        if (auditService == null || entity == null || targetType == null) {
            return;
        }
        try {
            Long targetIdLong = resolveEntityId(entity);
            String targetId = targetIdLong != null ? String.valueOf(targetIdLong) : null;
            Long projectId = resolveLongProperty(entity, "getProjectId");
            Long contractId = resolveLongProperty(entity, "getContractId");
            String changeSummary = AuditDiffHelper.summary(entity, targetType);
            auditService.recordOperation(OperationType.CREATE.name(), targetType.getValue(), targetId, projectId,
                    contractId, changeSummary, null, operatorId, operatorName);
        } catch (Exception ignored) {
            // 审计失败不影响主流程
        }
    }

    private static Long resolveEntityId(Object entity) {
        return resolveLongProperty(entity, "getId");
    }

    private static Long resolveLongProperty(Object entity, String getterName) {
        try {
            java.lang.reflect.Method m = entity.getClass().getMethod(getterName);
            Object v = m.invoke(entity);
            if (v instanceof Long l) {
                return l;
            }
            return v != null ? Long.parseLong(String.valueOf(v)) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
