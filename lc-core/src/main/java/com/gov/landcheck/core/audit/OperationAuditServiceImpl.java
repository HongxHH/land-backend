package com.gov.landcheck.core.audit;

import java.time.LocalDateTime;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.gov.landcheck.core.bo.R.AjaxJson;
import com.gov.landcheck.core.bo.dto.OperationAuditLogQueryDTO;
import com.gov.landcheck.core.bo.dto.OperationAuditLogQueryResultDTO;
import com.gov.landcheck.core.bo.entity.OperationAuditLog;
import com.gov.landcheck.core.config.query.MongoQueryBuilder;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class OperationAuditServiceImpl implements OperationAuditService {

    @Resource
    private MongoTemplate mongoTemplate;

    @Override
    public AjaxJson queryAuditLogs(OperationAuditLogQueryDTO queryDTO) {
        OperationAuditLogQueryDTO normalized = queryDTO == null ? new OperationAuditLogQueryDTO() : queryDTO;
        Criteria criteria = MongoQueryBuilder.buildCriteria(normalized);
        if (normalized.getOperateTimeStart() != null) {
            criteria = criteria.and("operate_time").gte(normalized.getOperateTimeStart());
        }
        if (normalized.getOperateTimeEnd() != null) {
            criteria = criteria.and("operate_time").lte(normalized.getOperateTimeEnd());
        }
        Query query = new Query(criteria);
        String sortField = normalized.getSortField() != null && !normalized.getSortField().isBlank()
                ? normalized.getSortField()
                : "operateTime";
        String sortDirection = normalized.getSortDirection() != null && !normalized.getSortDirection().isBlank()
                ? normalized.getSortDirection()
                : "desc";
        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection), sortField);
        int pageNum = normalized.getPageNum() != null && normalized.getPageNum() >= 1 ? normalized.getPageNum() : 1;
        int pageSize = normalized.getPageSize() != null && normalized.getPageSize() >= 1 ? normalized.getPageSize()
                : 20;
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, sort);
        query.with(pageable);
        java.util.List<OperationAuditLog> records = mongoTemplate.find(query, OperationAuditLog.class);
        long total = mongoTemplate.count(query.skip(-1).limit(-1), OperationAuditLog.class);
        OperationAuditLogQueryResultDTO result = new OperationAuditLogQueryResultDTO();
        result.setRecords(records);
        result.setCurrent(pageNum);
        result.setSize(pageSize);
        result.setTotal(total);
        result.setPages((int) Math.ceil((double) total / pageSize));
        return AjaxJson.getSuccessData(result);
    }

    @Override
    public void recordCreate(String targetType, String targetId, Long projectId, Long contractId,
            String changeSummary, Object extra) {
        saveLog("CREATE", targetType, targetId, projectId, contractId, changeSummary, extra);
    }

    @Override
    public void recordUpdate(String targetType, String targetId, Long projectId, Long contractId,
            String changeSummary, Object extra) {
        saveLog("UPDATE", targetType, targetId, projectId, contractId, changeSummary, extra);
    }

    @Override
    public void recordDelete(String targetType, String targetId, Long projectId, Long contractId,
            String changeSummary, Object extra) {
        saveLog("DELETE", targetType, targetId, projectId, contractId, changeSummary, extra);
    }

    @Override
    public void recordUploadOrMove(String operation, String targetType, String targetId, Long projectId,
            Long contractId,
            String changeSummary, Object extra) {
        saveLog(operation, targetType, targetId, projectId, contractId, changeSummary, extra, null, null);
    }

    @Override
    public void recordOperation(String operation, String targetType, String targetId, Long projectId, Long contractId,
            String changeSummary, Object extra, Long operatorId, String operatorName) {
        saveLog(operation, targetType, targetId, projectId, contractId, changeSummary, extra, operatorId, operatorName);
    }

    private void saveLog(String operation, String targetType, String targetId, Long projectId, Long contractId,
            String changeSummary, Object extra) {
        saveLog(operation, targetType, targetId, projectId, contractId, changeSummary, extra, null, null);
    }

    private void saveLog(String operation, String targetType, String targetId, Long projectId, Long contractId,
            String changeSummary, Object extra, Long operatorIdOverride, String operatorNameOverride) {
        try {
            OperationAuditLog logEntity = new OperationAuditLog();
            logEntity.setOperateTime(LocalDateTime.now());
            Long operatorId = operatorIdOverride != null ? operatorIdOverride : OperatorContext.getOperatorId();
            String operatorName = operatorNameOverride != null && !operatorNameOverride.isBlank()
                    ? operatorNameOverride
                    : OperatorContext.getOperatorName();
            logEntity.setOperatorId(operatorId);
            logEntity.setOperatorName(operatorName);
            logEntity.setOperation(operation);
            logEntity.setTargetType(targetType);
            logEntity.setTargetId(targetId);
            logEntity.setProjectId(projectId);
            logEntity.setContractId(contractId);
            logEntity.setChangeSummary(changeSummary);
            logEntity.setExtra(extra);
            logEntity.preSave();
            mongoTemplate.save(logEntity);
        } catch (Exception e) {
            log.warn("操作审计记录失败: operation={}, targetType={}, targetId={}", operation, targetType, targetId, e);
        }
    }
}
