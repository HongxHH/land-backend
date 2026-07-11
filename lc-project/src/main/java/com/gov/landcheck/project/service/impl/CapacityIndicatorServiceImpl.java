package com.gov.landcheck.project.service.impl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

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

import com.gov.landcheck.core.audit.AuditOperation;
import com.gov.landcheck.core.audit.OperationType;
import com.gov.landcheck.core.audit.TargetType;
import com.gov.landcheck.core.bo.R.AjaxJson;
import com.gov.landcheck.core.bo.entity.CapacityIndicatorInfo;
import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.config.cache.model.CacheLoadOptionsFactory;
import com.gov.landcheck.core.config.cache.service.CacheInvalidationService;
import com.gov.landcheck.core.config.cache.service.CacheOpsService;
import com.gov.landcheck.core.config.query.MongoQueryBuilder;
import com.gov.landcheck.project.cache.ProjectCacheKeys;
import com.gov.landcheck.project.dto.CapacityIndicatorFormQueryDTO;
import com.gov.landcheck.project.dto.CapacityIndicatorFormQueryResultDTO;
import com.gov.landcheck.project.dto.CapacityIndicatorFormUpdateDTO;
import com.gov.landcheck.project.service.CapacityIndicatorService;
import com.gov.landcheck.project.utils.DynamicUpdateHelper;
import com.gov.landcheck.project.utils.PageSortSupport;
import com.gov.landcheck.project.vo.CapacityIndicatorFormVO;

@Service
@Slf4j
public class CapacityIndicatorServiceImpl implements CapacityIndicatorService {

    private static final long DOUBLE_DELETE_DELAY_MS = 500L;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private CacheOpsService cacheOpsService;

    @Autowired
    private CacheInvalidationService cacheInvalidationService;

    @Autowired
    private CacheLoadOptionsFactory cacheLoadOptionsFactory;

    @Autowired
    private ProjectCacheKeys projectCacheKeys;

    @Override
    public AjaxJson queryCapacityIndicatorForms(CapacityIndicatorFormQueryDTO queryDTO) {
        CapacityIndicatorFormQueryDTO normalized = queryDTO == null ? new CapacityIndicatorFormQueryDTO() : queryDTO;
        String key = projectCacheKeys.queryCapacityIndicatorForms(normalized);
        CapacityIndicatorFormQueryResultDTO result = cacheOpsService.getOrLoad(
                key, cacheLoadOptionsFactory.queryOptions(), () -> queryCapacityIndicatorFormsInternal(normalized));
        return AjaxJson.getSuccessData(result);
    }

    @Override
    @AuditOperation(operation = OperationType.UPDATE, targetType = TargetType.CAPACITY_INDICATOR_FORM, idParam = "p0.id")
    public AjaxJson updateCapacityIndicatorForm(CapacityIndicatorFormUpdateDTO updateDTO) {
        try {
            Objects.requireNonNull(updateDTO, "updateDTO不能为空");
            Query query = new Query(Criteria.where("_id").is(updateDTO.getId()));
            CapacityIndicatorInfo existing = mongoTemplate.findOne(query, CapacityIndicatorInfo.class);
            if (existing == null) {
                return AjaxJson.getError("容量指标核查表不存在");
            }
            Update update = DynamicUpdateHelper.buildDynamicUpdate(updateDTO);
            DynamicUpdateHelper.applyClearableString(update, "remark", updateDTO.getRemark());
            Set<String> directKeys = capacityIndicatorDirectKeys(existing.getProjectId());
            evictBeforeWrite(directKeys);
            mongoTemplate.updateFirst(query, update, CapacityIndicatorInfo.class);
            evictAfterWrite(directKeys);
            return AjaxJson.getSuccess("容量指标核查表更新成功");
        } catch (Exception e) {
            log.error("更新容量指标核查表失败", e);
            return AjaxJson.getError("更新容量指标核查表失败");
        }
    }

    private Set<String> capacityIndicatorDirectKeys(Long projectId) {
        Set<String> keys = new HashSet<>();
        if (projectId != null) {
            keys.add(projectCacheKeys.capacityIndicatorsByProject(projectId));
            keys.add(projectCacheKeys.areaComparisonByProject(projectId));
        }
        return keys;
    }

    private CapacityIndicatorFormQueryResultDTO queryCapacityIndicatorFormsInternal(
            CapacityIndicatorFormQueryDTO queryDTO) {
        Criteria criteria = MongoQueryBuilder.buildCriteria(queryDTO);
        Query query = new Query(criteria);
        int pageNum = PageSortSupport.resolvePageNum(queryDTO.getPageNum());
        int pageSize = PageSortSupport.resolvePageSize(queryDTO.getPageSize());
        Sort sort = PageSortSupport.resolveSort(queryDTO.getSortDirection(), queryDTO.getSortField(), "createTime");
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, sort);
        query.with(pageable);
        List<CapacityIndicatorInfo> records = mongoTemplate.find(query, CapacityIndicatorInfo.class);
        long total = mongoTemplate.count(query.skip(-1).limit(-1), CapacityIndicatorInfo.class);
        List<CapacityIndicatorFormVO> voRecords = records.stream().map(form -> {
            CapacityIndicatorFormVO vo = new CapacityIndicatorFormVO();
            BeanUtils.copyProperties(form, vo);
            return vo;
        }).collect(Collectors.toList());
        if (!CollectionUtils.isEmpty(voRecords)) {
            List<Long> fileRecordIds = voRecords.stream()
                    .map(CapacityIndicatorFormVO::getFileRecordId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            if (!CollectionUtils.isEmpty(fileRecordIds)) {
                Query frQuery = new Query(Criteria.where("_id").in(fileRecordIds));
                List<FileRecord> fileRecords = mongoTemplate.find(frQuery, FileRecord.class);
                Map<Long, String> idToName = new HashMap<>();
                for (FileRecord fr : fileRecords) {
                    if (fr.getId() != null) {
                        idToName.put(fr.getId(), fr.getOriginalName() != null ? fr.getOriginalName() : "");
                    }
                }
                for (CapacityIndicatorFormVO vo : voRecords) {
                    Long fid = vo.getFileRecordId();
                    if (fid != null) {
                        vo.setFileOriginalName(idToName.getOrDefault(fid, ""));
                    }
                }
            }
        }
        CapacityIndicatorFormQueryResultDTO result = new CapacityIndicatorFormQueryResultDTO();
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
}
