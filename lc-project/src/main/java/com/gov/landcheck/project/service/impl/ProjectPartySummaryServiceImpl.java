package com.gov.landcheck.project.service.impl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.gov.landcheck.core.audit.AuditOperation;
import com.gov.landcheck.core.audit.OperationType;
import com.gov.landcheck.core.audit.TargetType;
import com.gov.landcheck.core.bo.R.AjaxJson;
import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.ProjectPartySurveySummaryForm;
import com.gov.landcheck.core.config.cache.config.CacheProperties;
import com.gov.landcheck.core.config.cache.model.CacheLoadOptions;
import com.gov.landcheck.core.config.cache.service.CacheInvalidationService;
import com.gov.landcheck.core.config.cache.service.CacheOpsService;
import com.gov.landcheck.core.config.query.MongoQueryBuilder;
import com.gov.landcheck.core.enums.FileStateEnum;
import com.gov.landcheck.project.cache.ProjectCacheKeys;
import com.gov.landcheck.project.dto.ProjectPartySummaryFormQueryDTO;
import com.gov.landcheck.project.dto.ProjectPartySummaryFormQueryResultDTO;
import com.gov.landcheck.project.dto.ProjectPartySummaryFormUpdateDTO;
import com.gov.landcheck.project.service.ProjectPartySummaryService;
import com.gov.landcheck.project.utils.DynamicUpdateHelper;
import com.gov.landcheck.project.utils.PageSortSupport;
import com.gov.landcheck.project.vo.ProjectPartySummaryFormVO;

@Service
public class ProjectPartySummaryServiceImpl implements ProjectPartySummaryService {

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
    public AjaxJson queryProjectPartySummaryForms(ProjectPartySummaryFormQueryDTO queryDTO) {
        ProjectPartySummaryFormQueryDTO normalized = queryDTO == null ? new ProjectPartySummaryFormQueryDTO()
                : queryDTO;
        String key = projectCacheKeys.queryProjectPartySummaryForms(normalized);
        ProjectPartySummaryFormQueryResultDTO result = cacheOpsService.getOrLoad(
                key, queryOptions(), () -> queryProjectPartySummaryFormsInternal(normalized));
        return AjaxJson.getSuccessData(result);
    }

    @Override
    @AuditOperation(operation = OperationType.UPDATE, targetType = TargetType.PROJECT_PARTY_SUMMARY_FORM, idParam = "p0.id")
    public AjaxJson updateProjectPartySummaryForm(ProjectPartySummaryFormUpdateDTO updateDTO) {
        try {
            Objects.requireNonNull(updateDTO, "updateDTO不能为空");
            Query query = new Query(Criteria.where("_id").is(updateDTO.getId()));
            ProjectPartySurveySummaryForm existing = mongoTemplate.findOne(query, ProjectPartySurveySummaryForm.class);
            if (existing == null) {
                return AjaxJson.getError("项目方实测汇总主表不存在");
            }
            Update update = DynamicUpdateHelper.buildDynamicUpdate(updateDTO);
            Set<String> directKeys = projectPartySummaryDirectKeys(existing.getProjectId());
            evictBeforeWrite(directKeys);
            mongoTemplate.updateFirst(query, update, ProjectPartySurveySummaryForm.class);
            evictAfterWrite(directKeys);
            return AjaxJson.getSuccess("项目方实测汇总主表更新成功");
        } catch (Exception e) {
            return AjaxJson.getError("更新项目方实测汇总主表失败: " + e.getMessage());
        }
    }

    private Set<String> projectPartySummaryDirectKeys(Long projectId) {
        Set<String> keys = new HashSet<>();
        if (projectId != null) {
            keys.add(projectCacheKeys.projectPartySummariesByProject(projectId));
            keys.add(projectCacheKeys.areaComparisonByProject(projectId));
        }
        return keys;
    }

    private ProjectPartySummaryFormQueryResultDTO queryProjectPartySummaryFormsInternal(
            ProjectPartySummaryFormQueryDTO queryDTO) {
        Criteria criteria = MongoQueryBuilder.buildCriteria(queryDTO);
        Query query = new Query(criteria);
        int pageNum = PageSortSupport.resolvePageNum(queryDTO.getPageNum());
        int pageSize = PageSortSupport.resolvePageSize(queryDTO.getPageSize());
        Sort sort = PageSortSupport.resolveSort(queryDTO.getSortDirection(), queryDTO.getSortField(), "createTime");
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, sort);
        query.with(pageable);
        List<ProjectPartySurveySummaryForm> records = mongoTemplate.find(query, ProjectPartySurveySummaryForm.class);
        long total = mongoTemplate.count(query.skip(-1).limit(-1), ProjectPartySurveySummaryForm.class);
        List<ProjectPartySummaryFormVO> voRecords = records.stream().map(form -> {
            ProjectPartySummaryFormVO vo = new ProjectPartySummaryFormVO();
            BeanUtils.copyProperties(form, vo);
            return vo;
        }).collect(Collectors.toList());
        // 单次 FileRecord 查询：补全文件名，并与 file_state 对齐 parseStatus（避免与归档列表不一致）
        if (!CollectionUtils.isEmpty(voRecords)) {
            List<Long> fileRecordIds = voRecords.stream()
                    .map(ProjectPartySummaryFormVO::getFileRecordId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            if (!CollectionUtils.isEmpty(fileRecordIds)) {
                Query frQuery = new Query(Criteria.where("_id").in(fileRecordIds));
                List<FileRecord> fileRecords = mongoTemplate.find(frQuery, FileRecord.class);
                Map<Long, String> idToName = new HashMap<>();
                Map<Long, FileStateEnum> idToState = new HashMap<>();
                for (FileRecord fr : fileRecords) {
                    if (fr.getId() == null) {
                        continue;
                    }
                    idToName.put(fr.getId(), fr.getOriginalName() != null ? fr.getOriginalName() : "");
                    if (fr.getFileState() != null) {
                        idToState.put(fr.getId(), fr.getFileState());
                    }
                }
                for (ProjectPartySummaryFormVO vo : voRecords) {
                    Long fid = vo.getFileRecordId();
                    if (fid == null) {
                        continue;
                    }
                    vo.setFileOriginalName(idToName.getOrDefault(fid, ""));
                    FileStateEnum fileState = idToState.get(fid);
                    if (fileState == FileStateEnum.PARSE_FAIL || fileState == FileStateEnum.UNPARSEABLE) {
                        String ps = vo.getParseStatus();
                        if (!StringUtils.hasText(ps) || "PENDING".equalsIgnoreCase(ps)) {
                            vo.setParseStatus("FAILED");
                        }
                    }
                }
            }
        }
        ProjectPartySummaryFormQueryResultDTO result = new ProjectPartySummaryFormQueryResultDTO();
        result.setRecords(voRecords);
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
