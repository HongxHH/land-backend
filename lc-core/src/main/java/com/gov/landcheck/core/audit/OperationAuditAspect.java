package com.gov.landcheck.core.audit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import com.gov.landcheck.core.bo.R.AjaxJson;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * 对带 @AuditOperation 的方法进行环绕处理，自动记录操作审计日志。
 */
@Slf4j
@Aspect
@Component
public class OperationAuditAspect {

    @Resource
    private MongoTemplate mongoTemplate;

    @Resource
    private OperationAuditService operationAuditService;

    @Resource
    private AuditMetadataRegistry auditMetadataRegistry;

    @Around("@annotation(audit)")
    public Object aroundAudited(ProceedingJoinPoint pjp, AuditOperation audit) throws Throwable {
        OperationType op = audit.operation();
        if (op == OperationType.CREATE) {
            Object result = pjp.proceed();
            recordCreateFromResult(audit, result);
            return result;
        }
        if (op != OperationType.UPDATE && op != OperationType.DELETE) {
            return pjp.proceed();
        }
        String idParam = audit.idParam();
        if (idParam == null || idParam.isBlank()) {
            return pjp.proceed();
        }
        Long targetIdLong = AuditParamResolver.resolveIdAsLong(pjp, idParam);
        if (targetIdLong == null) {
            return pjp.proceed();
        }
        String targetIdStr = String.valueOf(targetIdLong);
        Class<?> entityClass = auditMetadataRegistry.getEntityClass(audit.targetType());
        Object oldEntity = mongoTemplate.findById(targetIdLong, entityClass);
        Long projectId = audit.projectIdParam() != null && !audit.projectIdParam().isBlank()
                ? AuditParamResolver.resolveProjectId(pjp, audit.projectIdParam())
                : (oldEntity != null ? getEntityProjectId(oldEntity) : null);
        Long contractId = audit.contractIdParam() != null && !audit.contractIdParam().isBlank()
                ? AuditParamResolver.resolveContractId(pjp, audit.contractIdParam())
                : (oldEntity != null ? getEntityContractId(oldEntity) : null);

        Object result = pjp.proceed();

        try {
            if (op == OperationType.UPDATE) {
                Object newEntity = mongoTemplate.findById(targetIdLong, entityClass);
                String changeSummary = AuditDiffHelper.diff(oldEntity, newEntity, audit.targetType());
                if (!isEmptyChangeSummary(changeSummary)) {
                    operationAuditService.recordUpdate(audit.targetType().getValue(), targetIdStr, projectId,
                            contractId,
                            changeSummary, null);
                }
            } else if (oldEntity != null) {
                String changeSummary = AuditDiffHelper.summary(oldEntity, audit.targetType());
                operationAuditService.recordDelete(audit.targetType().getValue(), targetIdStr, projectId, contractId,
                        changeSummary, null);
            }
        } catch (Exception e) {
            log.warn("审计记录失败 {}: {}", op, audit.targetType(), e);
        }
        return result;
    }

    private void recordCreateFromResult(AuditOperation audit, Object result) {
        try {
            Object entity = extractEntityFromReturn(result);
            if (entity == null) {
                return;
            }
            Long targetIdLong = getEntityId(entity);
            String targetId = targetIdLong != null ? String.valueOf(targetIdLong) : null;
            Long projectId = getEntityProjectId(entity);
            Long contractId = getEntityContractId(entity);
            String changeSummary = AuditDiffHelper.summary(entity, audit.targetType());
            operationAuditService.recordCreate(audit.targetType().getValue(), targetId, projectId, contractId,
                    changeSummary, null);
        } catch (Exception e) {
            log.warn("审计记录失败 CREATE: {}", audit.targetType(), e);
        }
    }

    private static boolean isEmptyChangeSummary(String changeSummary) {
        if (changeSummary == null || changeSummary.isBlank()) {
            return true;
        }
        String trimmed = changeSummary.trim();
        return "[]".equals(trimmed) || "{}".equals(trimmed);
    }

    private static Object extractEntityFromReturn(Object result) {
        if (result == null) {
            return null;
        }
        if (result instanceof AjaxJson ajax) {
            if (ajax.getCode() != null && ajax.getCode() != AjaxJson.CODE_SUCCESS) {
                return null;
            }
            return ajax.getData();
        }
        return result;
    }

    private static Long getEntityId(Object entity) {
        if (entity == null) {
            return null;
        }
        try {
            java.lang.reflect.Method m = entity.getClass().getMethod("getId");
            Object id = m.invoke(entity);
            return id instanceof Long l ? l : (id != null ? Long.parseLong(String.valueOf(id)) : null);
        } catch (Exception e) {
            return null;
        }
    }

    private static Long getEntityProjectId(Object entity) {
        return getLongProperty(entity, "getProjectId");
    }

    private static Long getEntityContractId(Object entity) {
        return getLongProperty(entity, "getContractId");
    }

    private static Long getLongProperty(Object entity, String getterName) {
        if (entity == null) {
            return null;
        }
        try {
            java.lang.reflect.Method m = entity.getClass().getMethod(getterName);
            Object v = m.invoke(entity);
            return v instanceof Long l ? l : (v != null ? Long.parseLong(String.valueOf(v)) : null);
        } catch (Exception e) {
            return null;
        }
    }
}
