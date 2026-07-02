package com.gov.landcheck.core.audit;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gov.landcheck.core.bo.entity.CapacityIndicatorInfo;
import com.gov.landcheck.core.bo.entity.ContractInfo;
import com.gov.landcheck.core.bo.entity.FileArchive;
import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.LandParcel;
import com.gov.landcheck.core.bo.entity.PlanningReviewForm;
import com.gov.landcheck.core.bo.entity.PlanningReviewRow;
import com.gov.landcheck.core.bo.entity.Project;
import com.gov.landcheck.core.bo.entity.RoomInfo;
import com.gov.landcheck.core.bo.entity.SurveyReportInfo;
import com.gov.landcheck.core.bo.entity.UsageConfig;

import lombok.extern.slf4j.Slf4j;

/**
 * 对比两个实体的业务字段差异，生成用于审计的 change_summary JSON。
 */
@Slf4j
public final class AuditDiffHelper {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> SKIP_FIELDS = Set.of("id", "createTime", "updateTime", "isDeleted", "class");

    private AuditDiffHelper() {
    }

    /**
     * 计算旧实体与新实体的字段级 diff，返回 JSON 字符串。
     * 仅包含业务关键字段（与金额、面积、用途等相关）。
     */
    public static String diff(Object oldEntity, Object newEntity, TargetType targetType) {
        if (oldEntity == null && newEntity == null) {
            return "{}";
        }
        Class<?> entityClass = oldEntity != null ? oldEntity.getClass() : newEntity.getClass();
        Set<String> includeFields = getIncludeFields(targetType, entityClass);
        List<Map<String, Object>> changes = new ArrayList<>();
        for (Method m : entityClass.getMethods()) {
            String fieldName = getterToFieldName(m.getName());
            if (fieldName == null || SKIP_FIELDS.contains(fieldName) || !includeFields.contains(fieldName)) {
                continue;
            }
            if (m.getParameterCount() != 0 || m.getReturnType() == void.class) {
                continue;
            }
            Object oldVal = oldEntity != null ? invoke(m, oldEntity) : null;
            Object newVal = newEntity != null ? invoke(m, newEntity) : null;
            if (equals(oldVal, newVal)) {
                continue;
            }
            Map<String, Object> change = new LinkedHashMap<>();
            change.put("field", fieldName);
            change.put("oldValue", oldVal);
            change.put("newValue", newVal);
            changes.add(change);
        }
        try {
            return MAPPER.writeValueAsString(changes);
        } catch (JsonProcessingException e) {
            log.warn("Audit diff JSON serialize failed", e);
            return "[]";
        }
    }

    /**
     * 将实体关键字段转为摘要 JSON（用于 CREATE 或 DELETE 的 change_summary）
     */
    public static String summary(Object entity, TargetType targetType) {
        if (entity == null) {
            return "{}";
        }
        Class<?> entityClass = entity.getClass();
        Set<String> includeFields = getIncludeFields(targetType, entityClass);
        Map<String, Object> map = new LinkedHashMap<>();
        for (Method m : entityClass.getMethods()) {
            String fieldName = getterToFieldName(m.getName());
            if (fieldName == null || SKIP_FIELDS.contains(fieldName) || !includeFields.contains(fieldName)) {
                continue;
            }
            if (m.getParameterCount() != 0 || m.getReturnType() == void.class) {
                continue;
            }
            Object val = invoke(m, entity);
            if (val != null) {
                map.put(fieldName, val);
            }
        }
        try {
            return MAPPER.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.warn("Audit summary JSON serialize failed", e);
            return "{}";
        }
    }

    private static Set<String> getIncludeFields(TargetType targetType, Class<?> entityClass) {
        if (entityClass == Project.class) {
            return Set.of("projectName", "projectCode", "landArea", "plannedUse", "projectTime");
        }
        if (entityClass == ContractInfo.class) {
            return Set.of("projectId", "contractNumber", "contractType", "transferor", "transferee",
                    "totalArea", "residentialArea", "commercialArea", "remark");
        }
        if (entityClass == LandParcel.class) {
            return Set.of("contractId", "parcelCode", "parcelName", "plannedUse", "totalArea",
                    "residentialArea", "commercialArea", "floorAreaRatio", "commercialResidentialRatio", "remark");
        }
        if (entityClass == RoomInfo.class) {
            return Set.of("projectId", "surveyReportInfoId", "roomLevel", "roomNumber", "buildingArea",
                    "innerArea", "balconyArea", "sharedArea", "roomStructure", "roomUsage", "usageCategory",
                    "floorAreaType", "isCalculate", "remark");
        }
        if (entityClass == SurveyReportInfo.class) {
            return Set.of("projectId", "buildingName", "phase", "propertyCertificateNumber",
                    "actualTotalBuildingArea", "actualResidentialArea", "actualCommercialArea", "isParsed", "remark");
        }
        if (entityClass == FileArchive.class) {
            return Set.of("projectId", "name", "kind", "isDefault", "sortOrder");
        }
        if (entityClass == FileRecord.class) {
            return Set.of("projectId", "archiveId", "originalName", "fileType", "fileContextType", "fileState");
        }
        if (entityClass == UsageConfig.class) {
            return Set.of("usagePattern", "usageCategory", "floorAreaType", "isRegex", "priority", "status", "remark");
        }
        if (entityClass == PlanningReviewForm.class) {
            return Set.of("projectId", "fileRecordId", "isParsed", "projectName", "constructionUnit", "designUnit",
                    "constructionLocation", "landUseNature", "contactPerson", "contactPhone", "remarks");
        }
        if (entityClass == PlanningReviewRow.class) {
            return Set.of("projectId", "fileRecordId", "planningReviewFormId", "rowIndex", "engineeringProject",
                    "buildingNatureRaw", "areaCategory", "constructionNature", "buildingCount", "totalArea",
                    "aboveGroundArea", "belowGroundArea", "farAboveGround", "farBelowGround");
        }
        if (entityClass == CapacityIndicatorInfo.class) {
            return Set.of("projectId", "fileRecordId", "isParsed", "totalArea", "residentialArea", "commercialArea",
                    "remark");
        }
        return Set.of();
    }

    private static String getterToFieldName(String methodName) {
        if (methodName == null || methodName.length() < 4 || !methodName.startsWith("get")) {
            return null;
        }
        if (methodName.equals("getClass")) {
            return null;
        }
        String rest = methodName.substring(3);
        return rest.length() == 1 ? rest.toLowerCase() : Character.toLowerCase(rest.charAt(0)) + rest.substring(1);
    }

    private static Object invoke(Method m, Object target) {
        try {
            m.setAccessible(true);
            return m.invoke(target);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean equals(Object a, Object b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a instanceof BigDecimal && b instanceof BigDecimal) {
            return ((BigDecimal) a).compareTo((BigDecimal) b) == 0;
        }
        return a.equals(b);
    }
}
