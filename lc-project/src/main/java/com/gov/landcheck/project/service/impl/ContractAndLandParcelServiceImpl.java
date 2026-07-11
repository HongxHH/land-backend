package com.gov.landcheck.project.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
import com.gov.landcheck.core.bo.entity.ContractInfo;
import com.gov.landcheck.core.bo.entity.LandParcel;
import com.gov.landcheck.core.bo.entity.Project;
import com.gov.landcheck.core.config.cache.model.CacheLoadOptionsFactory;
import com.gov.landcheck.core.config.cache.service.CacheInvalidationService;
import com.gov.landcheck.core.config.cache.service.CacheOpsService;
import com.gov.landcheck.core.config.query.MongoQueryBuilder;
import com.gov.landcheck.core.service.SurveyReportContractApprovalSyncService;
import com.gov.landcheck.project.cache.ProjectCacheKeys;
import com.gov.landcheck.project.dto.ContractInfoQueryDTO;
import com.gov.landcheck.project.dto.ContractInfoQueryResultDTO;
import com.gov.landcheck.project.dto.ContractInfoUpdateDTO;
import com.gov.landcheck.project.dto.ContractWithParcelsDTO;
import com.gov.landcheck.project.dto.ContractWithParcelsDTO.ContractSummary;
import com.gov.landcheck.project.dto.LandParcelCreateDTO;
import com.gov.landcheck.project.dto.LandParcelUpdateDTO;
import com.gov.landcheck.project.service.ContractAndLandParcelService;
import com.gov.landcheck.project.utils.DynamicUpdateHelper;
import com.gov.landcheck.project.utils.LandParcelAreaCalculator;
import com.gov.landcheck.project.utils.PageSortSupport;
import com.gov.landcheck.project.utils.LandParcelAreaCalculator.AreaResult;
import com.gov.landcheck.project.vo.ContractProjectStatsVO;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ContractAndLandParcelServiceImpl implements ContractAndLandParcelService {

    private static final long DOUBLE_DELETE_DELAY_MS = 500L;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private CacheInvalidationService cacheInvalidationService;

    @Autowired
    private CacheOpsService cacheOpsService;

    @Autowired
    private CacheLoadOptionsFactory cacheLoadOptionsFactory;

    @Autowired
    private ProjectCacheKeys projectCacheKeys;

    @Autowired
    private SurveyReportContractApprovalSyncService surveyReportContractApprovalSyncService;

    @Override
    public AjaxJson queryContractInfos(ContractInfoQueryDTO queryDTO) {
        ContractInfoQueryDTO normalized = queryDTO == null ? new ContractInfoQueryDTO() : queryDTO;
        String key = projectCacheKeys.queryContracts(normalized);
        ContractInfoQueryResultDTO result = cacheOpsService.getOrLoad(key, cacheLoadOptionsFactory.queryOptions(),
                () -> queryContractInfosInternal(normalized));
        return AjaxJson.getSuccessData(result);
    }

    @Override
    @AuditOperation(operation = OperationType.UPDATE, targetType = TargetType.CONTRACT, idParam = "p0.id")
    public AjaxJson updateContractInfo(ContractInfoUpdateDTO updateDTO) {
        try {
            Objects.requireNonNull(updateDTO, "updateDTO不能为空");
            Query query = new Query(Criteria.where("_id").is(updateDTO.getId()));
            ContractInfo existingContract = mongoTemplate.findOne(query, ContractInfo.class);
            if (existingContract == null) {
                return AjaxJson.getError("Contract not found");
            }
            var update = DynamicUpdateHelper.buildDynamicUpdate(updateDTO);
            Set<String> directKeys = new HashSet<>();
            directKeys.add(projectCacheKeys.contractWithParcels(updateDTO.getId()));
            if (existingContract.getProjectId() != null) {
                directKeys.add(projectCacheKeys.contractsByProject(existingContract.getProjectId()));
                directKeys.add(projectCacheKeys.areaComparisonByProject(existingContract.getProjectId()));
                directKeys.add(projectCacheKeys.parsedReportsByProject(existingContract.getProjectId()));
            }
            evictBeforeWrite(directKeys);
            mongoTemplate.updateFirst(query, update, ContractInfo.class);
            if (existingContract.getProjectId() != null) {
                try {
                    surveyReportContractApprovalSyncService
                            .syncAllSurveyReportsInProject(existingContract.getProjectId());
                } catch (Exception ex) {
                    log.warn("同步实测报告合同/批文编号失败 projectId={}", existingContract.getProjectId(), ex);
                }
            }
            evictAfterWrite(directKeys, projectCacheKeys.queryPattern());
            return AjaxJson.getSuccess("合同信息更新成功");
        } catch (Exception e) {
            log.error("Failed to update contract", e);
            return AjaxJson.getError("Failed to update contract");
        }
    }

    @Override
    public AjaxJson getContractWithParcels(Long contractId) {
        if (contractId == null) {
            return AjaxJson.getError("合同ID不能为空");
        }
        String key = projectCacheKeys.contractWithParcels(contractId);
        ContractWithParcelsDTO data = cacheOpsService.getOrLoad(key, cacheLoadOptionsFactory.byRelationOptions(),
                () -> {
                    Query contractQuery = new Query(Criteria.where("_id").is(contractId));
                    ContractInfo contractInfo = mongoTemplate.findOne(contractQuery, ContractInfo.class);
                    if (contractInfo == null) {
                        return null;
                    }
                    Query parcelsQuery = new Query(Criteria.where("contract_id").is(contractId));
                    List<LandParcel> parcels = mongoTemplate.find(parcelsQuery, LandParcel.class);
                    ContractSummary summary = calculateContractSummary(parcels);
                    ContractWithParcelsDTO result = new ContractWithParcelsDTO();
                    result.setContractInfo(contractInfo);
                    result.setParcels(parcels != null ? parcels : new ArrayList<>());
                    result.setSummary(summary);
                    return result;
                });
        if (data == null) {
            return AjaxJson.getError("Contract not found");
        }
        return AjaxJson.getSuccess("获取合同及地块信息成功", data);
    }

    @Override
    @AuditOperation(operation = OperationType.CREATE, targetType = TargetType.LAND_PARCEL)
    public AjaxJson createLandParcel(LandParcelCreateDTO createDTO) {
        try {
            Objects.requireNonNull(createDTO, "createDTO不能为空");
            if (!StringUtils.hasText(createDTO.getParcelCode())) {
                return AjaxJson.getError("地块编码不能为空");
            }
            if (!StringUtils.hasText(createDTO.getPlannedUse())) {
                return AjaxJson.getError("规划用途不能为空");
            }
            Query contractQuery = new Query(Criteria.where("_id").is(createDTO.getContractId()));
            ContractInfo existingContract = mongoTemplate.findOne(contractQuery, ContractInfo.class);
            if (existingContract == null) {
                return AjaxJson.getError("Contract not found");
            }
            Query parcelQuery = Query.query(Criteria.where("contract_id").is(createDTO.getContractId())
                    .and("parcel_code").is(createDTO.getParcelCode().trim()));
            if (mongoTemplate.exists(parcelQuery, LandParcel.class)) {
                return AjaxJson.getError("地块编码已存在");
            }
            if (createDTO.getCommercialResidentialRatio() == null) {
                return AjaxJson.getError("商住比不能为空（范围 0~1，0.6 表示商业占比60%）");
            }
            AreaResult areas = LandParcelAreaCalculator.compute(createDTO.getTotalArea(),
                    createDTO.getCommercialResidentialRatio());
            Set<String> directKeys = new HashSet<>();
            directKeys.add(projectCacheKeys.contractWithParcels(createDTO.getContractId()));
            if (existingContract.getProjectId() != null) {
                directKeys.add(projectCacheKeys.contractsByProject(existingContract.getProjectId()));
                directKeys.add(projectCacheKeys.areaComparisonByProject(existingContract.getProjectId()));
            }
            evictBeforeWrite(directKeys);
            LandParcel landParcel = new LandParcel();
            landParcel.setContractId(createDTO.getContractId());
            landParcel.setParcelCode(createDTO.getParcelCode().trim());
            landParcel.setParcelName(createDTO.getParcelName());
            landParcel.setPlannedUse(createDTO.getPlannedUse().trim());
            landParcel.setTotalArea(createDTO.getTotalArea());
            landParcel.setFloorAreaRatio(createDTO.getFloorAreaRatio());
            landParcel.setResidentialArea(areas.getResidentialArea());
            landParcel.setCommercialArea(areas.getCommercialArea());
            landParcel.setCommercialResidentialRatio(createDTO.getCommercialResidentialRatio());
            landParcel.setRemark(createDTO.getRemark());
            landParcel.preSave();
            LandParcel savedParcel = mongoTemplate.save(landParcel);
            updateContractAreaSummary(createDTO.getContractId());
            evictAfterWrite(directKeys, projectCacheKeys.queryPattern());
            return AjaxJson.getSuccess("地块创建成功", savedParcel);
        } catch (Exception e) {
            log.error("Failed to create land parcel", e);
            return AjaxJson.getError("Failed to create land parcel");
        }
    }

    @Override
    @AuditOperation(operation = OperationType.UPDATE, targetType = TargetType.LAND_PARCEL, idParam = "p0.id")
    public AjaxJson updateLandParcel(LandParcelUpdateDTO updateDTO) {
        try {
            Objects.requireNonNull(updateDTO, "updateDTO不能为空");
            Query query = new Query(Criteria.where("_id").is(updateDTO.getId()));
            LandParcel existingParcel = mongoTemplate.findOne(query, LandParcel.class);
            if (existingParcel == null) {
                return AjaxJson.getError("Land parcel not found");
            }
            if (updateDTO.getParcelCode() != null && !updateDTO.getParcelCode().trim().isEmpty()) {
                Query codeQuery = Query.query(Criteria.where("contract_id").is(existingParcel.getContractId())
                        .and("parcel_code").is(updateDTO.getParcelCode().trim())
                        .and("_id").ne(updateDTO.getId()));
                if (mongoTemplate.exists(codeQuery, LandParcel.class)) {
                    return AjaxJson.getError("地块编码已存在");
                }
            }
            Set<String> directKeys = new HashSet<>();
            directKeys.add(projectCacheKeys.contractWithParcels(existingParcel.getContractId()));
            ContractInfo contract = mongoTemplate.findById(existingParcel.getContractId(), ContractInfo.class);
            if (contract != null && contract.getProjectId() != null) {
                directKeys.add(projectCacheKeys.contractsByProject(contract.getProjectId()));
                directKeys.add(projectCacheKeys.areaComparisonByProject(contract.getProjectId()));
            }
            evictBeforeWrite(directKeys);
            Update update = DynamicUpdateHelper.buildDynamicUpdate(updateDTO);
            BigDecimal effectiveTotal = updateDTO.getTotalArea() != null ? updateDTO.getTotalArea()
                    : existingParcel.getTotalArea();
            BigDecimal effectiveCommercialRatio = updateDTO.getCommercialResidentialRatio() != null
                    ? updateDTO.getCommercialResidentialRatio()
                    : existingParcel.getCommercialResidentialRatio();
            if (effectiveTotal != null && effectiveCommercialRatio != null) {
                AreaResult areas = LandParcelAreaCalculator.compute(effectiveTotal, effectiveCommercialRatio);
                update.set("residential_area", areas.getResidentialArea());
                update.set("commercial_area", areas.getCommercialArea());
            }
            mongoTemplate.updateFirst(query, update, LandParcel.class);
            updateContractAreaSummary(existingParcel.getContractId());
            evictAfterWrite(directKeys, projectCacheKeys.queryPattern());
            return AjaxJson.getSuccess("地块信息更新成功");
        } catch (Exception e) {
            log.error("Failed to update land parcel", e);
            return AjaxJson.getError("Failed to update land parcel");
        }
    }

    @Override
    @AuditOperation(operation = OperationType.DELETE, targetType = TargetType.LAND_PARCEL, idParam = "p0")
    public AjaxJson deleteLandParcel(Long parcelId) {
        try {
            Query query = new Query(Criteria.where("_id").is(parcelId));
            LandParcel existingParcel = mongoTemplate.findOne(query, LandParcel.class);
            if (existingParcel == null) {
                return AjaxJson.getError("Land parcel not found");
            }
            Set<String> directKeys = new HashSet<>();
            directKeys.add(projectCacheKeys.contractWithParcels(existingParcel.getContractId()));
            ContractInfo contract = mongoTemplate.findById(existingParcel.getContractId(), ContractInfo.class);
            if (contract != null && contract.getProjectId() != null) {
                directKeys.add(projectCacheKeys.contractsByProject(contract.getProjectId()));
                directKeys.add(projectCacheKeys.areaComparisonByProject(contract.getProjectId()));
            }
            evictBeforeWrite(directKeys);
            Long contractId = existingParcel.getContractId();
            mongoTemplate.remove(query, LandParcel.class);
            updateContractAreaSummary(contractId);
            evictAfterWrite(directKeys, projectCacheKeys.queryPattern());
            return AjaxJson.getSuccess("地块删除成功");
        } catch (Exception e) {
            log.error("Failed to delete land parcel", e);
            return AjaxJson.getError("Failed to delete land parcel");
        }
    }

    private void updateContractAreaSummary(Long contractId) {
        try {
            Query parcelsQuery = new Query(Criteria.where("contract_id").is(contractId));
            List<LandParcel> parcels = mongoTemplate.find(parcelsQuery, LandParcel.class);
            BigDecimal residentialArea = BigDecimal.ZERO;
            BigDecimal commercialArea = BigDecimal.ZERO;
            if (!CollectionUtils.isEmpty(parcels)) {
                for (LandParcel parcel : parcels) {
                    if (parcel.getResidentialArea() != null) {
                        residentialArea = residentialArea.add(parcel.getResidentialArea());
                    }
                    if (parcel.getCommercialArea() != null) {
                        commercialArea = commercialArea.add(parcel.getCommercialArea());
                    }
                }
            }
            BigDecimal totalArea = residentialArea.add(commercialArea);
            Query contractQuery = new Query(Criteria.where("_id").is(contractId));
            Update update = new Update();
            update.set("residential_area", residentialArea);
            update.set("commercial_area", commercialArea);
            update.set("total_area", totalArea);
            mongoTemplate.updateFirst(contractQuery, update, ContractInfo.class);
        } catch (Exception e) {
            log.error("更新合同面积汇总失败, contractId={}", contractId, e);
            throw new IllegalStateException("更新合同面积汇总失败: contractId=" + contractId, e);
        }
    }

    private ContractSummary calculateContractSummary(List<LandParcel> parcels) {
        ContractSummary summary = new ContractSummary();
        if (CollectionUtils.isEmpty(parcels)) {
            summary.setTotalParcels(0);
            summary.setTotalArea(BigDecimal.ZERO);
            summary.setTotalResidentialArea(BigDecimal.ZERO);
            summary.setTotalCommercialArea(BigDecimal.ZERO);
            summary.setAverageFloorAreaRatio(BigDecimal.ZERO);
            return summary;
        }
        summary.setTotalParcels(parcels.size());
        BigDecimal totalArea = BigDecimal.ZERO;
        BigDecimal totalResidentialArea = BigDecimal.ZERO;
        BigDecimal totalCommercialArea = BigDecimal.ZERO;
        BigDecimal totalFloorAreaRatio = BigDecimal.ZERO;
        int floorAreaRatioCount = 0;
        for (LandParcel parcel : parcels) {
            if (parcel.getTotalArea() != null) {
                totalArea = totalArea.add(parcel.getTotalArea());
            }
            if (parcel.getResidentialArea() != null) {
                totalResidentialArea = totalResidentialArea.add(parcel.getResidentialArea());
            }
            if (parcel.getCommercialArea() != null) {
                totalCommercialArea = totalCommercialArea.add(parcel.getCommercialArea());
            }
            if (parcel.getFloorAreaRatio() != null) {
                totalFloorAreaRatio = totalFloorAreaRatio.add(parcel.getFloorAreaRatio());
                floorAreaRatioCount++;
            }
        }
        summary.setTotalArea(totalArea);
        summary.setTotalResidentialArea(totalResidentialArea);
        summary.setTotalCommercialArea(totalCommercialArea);
        if (floorAreaRatioCount > 0) {
            summary.setAverageFloorAreaRatio(
                    totalFloorAreaRatio.divide(BigDecimal.valueOf(floorAreaRatioCount), 2, RoundingMode.HALF_UP));
        } else {
            summary.setAverageFloorAreaRatio(BigDecimal.ZERO);
        }
        return summary;
    }

    private void evictBeforeWrite(Set<String> directKeys) {
        cacheInvalidationService.evictImmediately(directKeys);
    }

    private void evictAfterWrite(Set<String> directKeys, String queryPattern) {
        cacheInvalidationService.evictTwice(directKeys, DOUBLE_DELETE_DELAY_MS);
        cacheInvalidationService.evictByPatternTwice(Set.of(queryPattern), DOUBLE_DELETE_DELAY_MS);
    }

    private ContractInfoQueryResultDTO queryContractInfosInternal(ContractInfoQueryDTO queryDTO) {
        Criteria criteria = MongoQueryBuilder.buildCriteria(queryDTO);
        Query query = new Query(criteria);
        int pageNum = PageSortSupport.resolvePageNum(queryDTO.getPageNum());
        int pageSize = PageSortSupport.resolvePageSize(queryDTO.getPageSize());
        Sort sort = PageSortSupport.resolveSort(queryDTO.getSortDirection(), queryDTO.getSortField(), "createTime");
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, sort);
        query.with(pageable);
        List<ContractInfo> contractInfos = mongoTemplate.find(query, ContractInfo.class);
        long total = mongoTemplate.count(query.skip(-1).limit(-1), ContractInfo.class);
        ContractInfoQueryResultDTO result = new ContractInfoQueryResultDTO();
        result.setRecords(contractInfos);
        result.setCurrent(pageNum);
        result.setSize(pageSize);
        result.setTotal(total);
        result.setPages((int) Math.ceil((double) total / pageSize));
        return result;
    }

    @Override
    public ContractProjectStatsVO getContractProjectStatsByProjectId(Long projectId) {
        if (projectId == null) {
            return null;
        }
        return batchGetContractProjectStatsByProjectIds(List.of(projectId)).get(projectId);
    }

    @Override
    public java.util.Map<Long, ContractProjectStatsVO> batchGetContractProjectStatsByProjectIds(List<Long> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            return java.util.Map.of();
        }

        Query contractQuery = new Query(Criteria.where("project_id").in(projectIds));
        // 仅投影统计字段，避免反序列化无关内容
        contractQuery.fields()
                .include("project_id")
                .include("total_area")
                .include("residential_area")
                .include("commercial_area")
                .include("transferor")
                .include("transferee");

        List<ContractInfo> contracts = mongoTemplate.find(contractQuery, ContractInfo.class);
        if (contracts == null || contracts.isEmpty()) {
            return java.util.Map.of();
        }

        class Acc {
            BigDecimal totalSum = BigDecimal.ZERO;
            boolean hasTotal = false;
            BigDecimal residentialSum = BigDecimal.ZERO;
            boolean hasResidential = false;
            BigDecimal commercialSum = BigDecimal.ZERO;
            boolean hasCommercial = false;
            String transferor;
            String transferee;
        }

        java.util.Map<Long, Acc> accMap = new java.util.HashMap<>();
        for (ContractInfo c : contracts) {
            if (c == null || c.getProjectId() == null) {
                continue;
            }
            Acc acc = accMap.computeIfAbsent(c.getProjectId(), k -> new Acc());
            if (c.getTotalArea() != null) {
                acc.totalSum = acc.totalSum.add(c.getTotalArea());
                acc.hasTotal = true;
            }
            if (c.getResidentialArea() != null) {
                acc.residentialSum = acc.residentialSum.add(c.getResidentialArea());
                acc.hasResidential = true;
            }
            if (c.getCommercialArea() != null) {
                acc.commercialSum = acc.commercialSum.add(c.getCommercialArea());
                acc.hasCommercial = true;
            }
            if (acc.transferor == null && StringUtils.hasText(c.getTransferor())) {
                acc.transferor = c.getTransferor();
            }
            if (acc.transferee == null && StringUtils.hasText(c.getTransferee())) {
                acc.transferee = c.getTransferee();
            }
        }

        java.util.Map<Long, ContractProjectStatsVO> result = new java.util.HashMap<>();
        for (var entry : accMap.entrySet()) {
            Long pid = entry.getKey();
            Acc acc = entry.getValue();
            ContractProjectStatsVO vo = new ContractProjectStatsVO();
            vo.setTotalArea(acc.hasTotal ? acc.totalSum : null);
            vo.setResidentialArea(acc.hasResidential ? acc.residentialSum : null);
            vo.setCommercialArea(acc.hasCommercial ? acc.commercialSum : null);
            vo.setTransferor(acc.transferor);
            vo.setTransferee(acc.transferee);
            result.put(pid, vo);
        }
        return result;
    }

    private boolean isText(String value) {
        return value != null && !value.isBlank();
    }
}
