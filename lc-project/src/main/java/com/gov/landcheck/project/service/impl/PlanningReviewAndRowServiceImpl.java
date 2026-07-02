package com.gov.landcheck.project.service.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.gov.landcheck.core.audit.AuditOperation;
import com.gov.landcheck.core.audit.OperationType;
import com.gov.landcheck.core.audit.TargetType;
import com.gov.landcheck.core.bo.R.AjaxJson;
import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.PlanningReviewForm;
import com.gov.landcheck.core.bo.entity.PlanningReviewRow;
import com.gov.landcheck.core.bo.entity.Project;
import com.gov.landcheck.core.config.cache.config.CacheProperties;
import com.gov.landcheck.core.config.cache.model.CacheLoadOptions;
import com.gov.landcheck.core.config.cache.service.CacheInvalidationService;
import com.gov.landcheck.core.config.cache.service.CacheOpsService;
import com.gov.landcheck.core.config.query.MongoQueryBuilder;
import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.project.cache.ProjectCacheKeys;
import com.gov.landcheck.project.dto.PlanningReviewFormQueryDTO;
import com.gov.landcheck.project.dto.PlanningReviewFormQueryResultDTO;
import com.gov.landcheck.project.dto.PlanningReviewFormUpdateDTO;
import com.gov.landcheck.project.dto.PlanningReviewRowCreateDTO;
import com.gov.landcheck.project.dto.PlanningReviewRowQueryDTO;
import com.gov.landcheck.project.dto.PlanningReviewRowQueryResultDTO;
import com.gov.landcheck.project.dto.PlanningReviewRowUpdateDTO;
import com.gov.landcheck.project.service.PlanningReviewAndRowService;
import com.gov.landcheck.project.utils.DynamicUpdateHelper;
import com.gov.landcheck.project.utils.PageSortSupport;

@Service
public class PlanningReviewAndRowServiceImpl implements PlanningReviewAndRowService {

    private static final long DOUBLE_DELETE_DELAY_MS = 500L;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private CacheOpsService cacheOpsService;

    @Autowired
    private CacheInvalidationService cacheInvalidationService;

    @Autowired
    private CacheProperties cacheProperties;

    @Autowired
    private ProjectCacheKeys projectCacheKeys;

    @Override
    public AjaxJson queryPlanningReviewForms(PlanningReviewFormQueryDTO queryDTO) {
        PlanningReviewFormQueryDTO normalized = queryDTO == null ? new PlanningReviewFormQueryDTO() : queryDTO;
        String key = projectCacheKeys.queryPlanningReviewForms(normalized);
        PlanningReviewFormQueryResultDTO result = cacheOpsService.getOrLoad(key, queryOptions(),
                () -> queryPlanningReviewFormsInternal(normalized));
        return AjaxJson.getSuccessData(result);
    }

    @Override
    public AjaxJson queryPlanningReviewRows(PlanningReviewRowQueryDTO queryDTO) {
        PlanningReviewRowQueryDTO normalized = queryDTO == null ? new PlanningReviewRowQueryDTO() : queryDTO;
        String key = projectCacheKeys.queryPlanningReviewRows(normalized);
        PlanningReviewRowQueryResultDTO result = cacheOpsService.getOrLoad(key, queryOptions(),
                () -> queryPlanningReviewRowsInternal(normalized));
        return AjaxJson.getSuccessData(result);
    }

    @Override
    public AjaxJson getPlanningReviewRowsByProjectAndFormId(Long projectId, Long formId) {
        if (projectId == null) {
            return AjaxJson.getError("项目ID不能为空");
        }
        if (formId == null) {
            return AjaxJson.getError("规划复核表主表ID不能为空");
        }
        Query formQ = new Query(Criteria.where("_id").is(formId));
        PlanningReviewForm form = mongoTemplate.findOne(formQ, PlanningReviewForm.class);
        if (form == null) {
            return AjaxJson.getError("规划复核表主表不存在");
        }
        if (!projectId.equals(form.getProjectId())) {
            return AjaxJson.getError("规划复核表不属于当前项目");
        }
        String cacheKey = projectCacheKeys.planningReviewRowsByProjectAndForm(projectId, formId);
        List<PlanningReviewRow> rows = cacheOpsService.getOrLoad(cacheKey, byRelationOptions(), () -> {
            Query q = new Query(Criteria.where("project_id").is(projectId).and("planning_review_form_id").is(formId));
            return mongoTemplate.find(q, PlanningReviewRow.class);
        });
        return AjaxJson.getSuccessData(rows);
    }

    @Override
    @AuditOperation(operation = OperationType.UPDATE, targetType = TargetType.PLANNING_REVIEW_FORM, idParam = "p0.id")
    public AjaxJson updatePlanningReviewForm(PlanningReviewFormUpdateDTO updateDTO) {
        try {
        Objects.requireNonNull(updateDTO, "updateDTO不能为空");
            Query query = new Query(Criteria.where("_id").is(updateDTO.getId()));
            PlanningReviewForm existing = mongoTemplate.findOne(query, PlanningReviewForm.class);
            if (existing == null) {
                return AjaxJson.getError("规划复核表主表不存在");
            }
            Update update = DynamicUpdateHelper.buildDynamicUpdate(updateDTO);
            Set<String> directKeys = planningReviewDirectKeys(existing.getProjectId(), existing.getId());
            evictBeforeWrite(directKeys);
            mongoTemplate.updateFirst(query, update, PlanningReviewForm.class);
            evictAfterWrite(directKeys);
            return AjaxJson.getSuccess("规划复核表主表更新成功");
        } catch (Exception e) {
            return AjaxJson.getError("更新规划复核表主表失败: " + e.getMessage());
        }
    }

    @Override
    @AuditOperation(operation = OperationType.CREATE, targetType = TargetType.PLANNING_REVIEW_ROW)
    public AjaxJson createPlanningReviewRow(PlanningReviewRowCreateDTO createDTO) {
        try {
        Objects.requireNonNull(createDTO, "createDTO不能为空");
            AjaxJson vr = validateProject(createDTO.getProjectId());
            if (vr != null) {
                return vr;
            }
            vr = validatePlanningReviewFileRecord(createDTO.getProjectId(), createDTO.getFileRecordId());
            if (vr != null) {
                return vr;
            }
            PlanningReviewForm form = mongoTemplate.findOne(
                    new Query(Criteria.where("_id").is(createDTO.getPlanningReviewFormId())),
                    PlanningReviewForm.class);
            if (form == null) {
                return AjaxJson.getError("规划复核表主表不存在");
            }
            if (!createDTO.getProjectId().equals(form.getProjectId())) {
                return AjaxJson.getError("主表不属于当前项目");
            }
            if (!createDTO.getFileRecordId().equals(form.getFileRecordId())) {
                return AjaxJson.getError("文件记录与主表不一致");
            }
            Set<String> directKeys = planningReviewDirectKeys(createDTO.getProjectId(), createDTO.getPlanningReviewFormId());
            evictBeforeWrite(directKeys);
                    
            PlanningReviewRow row = new PlanningReviewRow();
            BeanUtils.copyProperties(createDTO, row);
            row.preSave();
            PlanningReviewRow saved = mongoTemplate.save(row);
            evictAfterWrite(directKeys);
            return AjaxJson.getSuccess("规划复核表行创建成功", saved);
        } catch (Exception e) {
            return AjaxJson.getError("创建规划复核表行失败: " + e.getMessage());
        }
    }

    @Override
    @AuditOperation(operation = OperationType.UPDATE, targetType = TargetType.PLANNING_REVIEW_ROW, idParam = "p0.id")
    public AjaxJson updatePlanningReviewRow(PlanningReviewRowUpdateDTO updateDTO) {
        try {
        Objects.requireNonNull(updateDTO, "updateDTO不能为空");
            Query query = new Query(Criteria.where("_id").is(updateDTO.getId()));
            PlanningReviewRow existing = mongoTemplate.findOne(query, PlanningReviewRow.class);
            if (existing == null) {
                return AjaxJson.getError("规划复核表行不存在");
            }
            Update update = DynamicUpdateHelper.buildDynamicUpdate(updateDTO);
            Set<String> directKeys = planningReviewDirectKeys(existing.getProjectId(), existing.getPlanningReviewFormId());
            evictBeforeWrite(directKeys);
                    
            mongoTemplate.updateFirst(query, update, PlanningReviewRow.class);
            evictAfterWrite(directKeys);
            return AjaxJson.getSuccess("规划复核表行更新成功");
        } catch (Exception e) {
            return AjaxJson.getError("更新规划复核表行失败: " + e.getMessage());
        }
    }

    @Override
    @AuditOperation(operation = OperationType.DELETE, targetType = TargetType.PLANNING_REVIEW_ROW, idParam = "p0")
    public AjaxJson deletePlanningReviewRow(Long rowId) {
        try {
            Query query = new Query(Criteria.where("_id").is(rowId));
            PlanningReviewRow existing = mongoTemplate.findOne(query, PlanningReviewRow.class);
            if (existing == null) {
                return AjaxJson.getError("规划复核表行不存在");
            }
            Set<String> directKeys = planningReviewDirectKeys(existing.getProjectId(), existing.getPlanningReviewFormId());
            evictBeforeWrite(directKeys);
                    
            mongoTemplate.remove(query, PlanningReviewRow.class);
            evictAfterWrite(directKeys);
            return AjaxJson.getSuccess("规划复核表行删除成功");
        } catch (Exception e) {
            return AjaxJson.getError("删除规划复核表行失败: " + e.getMessage());
        }
    }

    private AjaxJson validateProject(Long projectId) {
        if (projectId == null) {
            return AjaxJson.getError("项目ID不能为空");
        }
        Project p = mongoTemplate.findOne(new Query(Criteria.where("_id").is(projectId)), Project.class);
        if (p == null) {
            return AjaxJson.getError("项目不存在");
        }
        return null;
    }

    private AjaxJson validatePlanningReviewFileRecord(Long projectId, Long fileRecordId) {
        if (fileRecordId == null) {
            return AjaxJson.getError("文件记录ID不能为空");
        }
        FileRecord fr = mongoTemplate.findOne(new Query(Criteria.where("_id").is(fileRecordId)), FileRecord.class);
        if (fr == null) {
            return AjaxJson.getError("文件记录不存在");
        }
        if (!projectId.equals(fr.getProjectId())) {
            return AjaxJson.getError("文件记录不属于当前项目");
        }
        if (fr.getFileContextType() != FileContextType.PLANNING_REVIEW) {
            return AjaxJson.getError("文件内容类型须为规划复核表（PLANNING_REVIEW）");
        }
        return null;
    }

    private Set<String> planningReviewDirectKeys(Long projectId, Long formId) {
        Set<String> keys = new HashSet<>();
        if (projectId != null) {
            keys.add(projectCacheKeys.planningReviewsByProject(projectId));
            keys.add(projectCacheKeys.areaComparisonByProject(projectId));
        }
        if (projectId != null && formId != null) {
            keys.add(projectCacheKeys.planningReviewRowsByProjectAndForm(projectId, formId));
        }
        return keys;
    }

    private PlanningReviewFormQueryResultDTO queryPlanningReviewFormsInternal(PlanningReviewFormQueryDTO queryDTO) {
        Criteria criteria = MongoQueryBuilder.buildCriteria(queryDTO);
        Query query = new Query(criteria);
        int pageNum = PageSortSupport.resolvePageNum(queryDTO.getPageNum());
        int pageSize = PageSortSupport.resolvePageSize(queryDTO.getPageSize());
        Sort sort = PageSortSupport.resolveSort(queryDTO.getSortDirection(), queryDTO.getSortField(), "createTime");
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, sort);
        query.with(pageable);
        List<PlanningReviewForm> records = mongoTemplate.find(query, PlanningReviewForm.class);
        long total = mongoTemplate.count(query.skip(-1).limit(-1), PlanningReviewForm.class);
        PlanningReviewFormQueryResultDTO result = new PlanningReviewFormQueryResultDTO();
        result.setRecords(records);
        result.setCurrent(pageNum);
        result.setSize(pageSize);
        result.setTotal(total);
        result.setPages((int) Math.ceil((double) total / pageSize));
        return result;
    }

    private PlanningReviewRowQueryResultDTO queryPlanningReviewRowsInternal(PlanningReviewRowQueryDTO queryDTO) {
        Criteria criteria = MongoQueryBuilder.buildCriteria(queryDTO);
        Query query = new Query(criteria);
        int pageNum = PageSortSupport.resolvePageNum(queryDTO.getPageNum());
        int pageSize = PageSortSupport.resolvePageSize(queryDTO.getPageSize());
        Sort sort = PageSortSupport.resolveSort(queryDTO.getSortDirection(), queryDTO.getSortField(), "createTime");
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, sort);
        query.with(pageable);
        List<PlanningReviewRow> records = mongoTemplate.find(query, PlanningReviewRow.class);
        long total = mongoTemplate.count(query.skip(-1).limit(-1), PlanningReviewRow.class);
        PlanningReviewRowQueryResultDTO result = new PlanningReviewRowQueryResultDTO();
        result.setRecords(records);
        result.setCurrent(pageNum);
        result.setSize(pageSize);
        result.setTotal(total);
        result.setPages((int) Math.ceil((double) total / pageSize));
        return result;
    }

    private void evictBeforeWrite(Set<String> directKeys) {
        cacheInvalidationService.evictImmediately(directKeys);
    }

    private void evictAfterWrite(Set<String> directKeys) {
        cacheInvalidationService.evictTwice(directKeys, DOUBLE_DELETE_DELAY_MS);
        cacheInvalidationService.evictByPatternTwice(Set.of(projectCacheKeys.queryPattern()), DOUBLE_DELETE_DELAY_MS);
    }

    private CacheLoadOptions byRelationOptions() {
        return CacheLoadOptions.builder()
                .ttlSeconds(cacheProperties.getTtl().getProject().getByRelation())
                .useLock(true)
                .cacheNullValue(false)
                .lockWaitMs(cacheProperties.getLock().getWaitMs())
                .lockLeaseMs(cacheProperties.getLock().getLeaseMs())
                .build();
    }

    private CacheLoadOptions queryOptions() {
        return CacheLoadOptions.builder()
                .ttlSeconds(cacheProperties.getTtl().getProject().getQuery())
                .useLock(true)
                .cacheNullValue(false)
                .lockWaitMs(cacheProperties.getLock().getWaitMs())
                .lockLeaseMs(cacheProperties.getLock().getLeaseMs())
                .build();
    }
}
