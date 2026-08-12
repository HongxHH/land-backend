package com.gov.landcheck.project.service.impl;

import java.util.ArrayList;
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
import com.gov.landcheck.core.bo.entity.RoomInfo;
import com.gov.landcheck.core.bo.entity.SurveyReportInfo;
import com.gov.landcheck.core.common.ValidationResult;
import com.gov.landcheck.core.config.cache.model.CacheLoadOptionsFactory;
import com.gov.landcheck.core.config.cache.service.CacheInvalidationService;
import com.gov.landcheck.core.config.cache.service.CacheOpsService;
import com.gov.landcheck.core.config.query.MongoQueryBuilder;
import com.gov.landcheck.core.enums.FloorAreaTypeEnum;
import com.gov.landcheck.core.enums.UsageCategoryEnum;
import com.gov.landcheck.core.service.SurveyReportCalculationService;
import com.gov.landcheck.core.util.RoomInfoValidator;
import com.gov.landcheck.project.cache.ProjectCacheKeys;
import com.gov.landcheck.project.dto.RoomInfoCreateDTO;
import com.gov.landcheck.project.dto.RoomInfoQueryDTO;
import com.gov.landcheck.project.dto.RoomInfoQueryResultDTO;
import com.gov.landcheck.project.dto.RoomInfoUpdateDTO;
import com.gov.landcheck.project.dto.SurveyReportInfoQueryDTO;
import com.gov.landcheck.project.dto.SurveyReportInfoQueryResultDTO;
import com.gov.landcheck.project.dto.SurveyReportInfoUpdateDTO;
import com.gov.landcheck.project.service.SurveyReportAndRoomService;
import com.gov.landcheck.project.utils.DynamicUpdateHelper;
import com.gov.landcheck.project.utils.PageSortSupport;
import com.gov.landcheck.project.vo.RoomInfoVO;
import com.gov.landcheck.project.vo.SurveyReportInfoVO;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class SurveyReportAndRoomServiceImpl implements SurveyReportAndRoomService {

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

    @Autowired
    private SurveyReportCalculationService calculationService;

    @Override
    public AjaxJson getParsedSurveyReportInfoByProjectId(Long projectId) {
        if (projectId == null) {
            return AjaxJson.getError("项目ID不能为空");
        }
        String key = projectCacheKeys.parsedReportsByProject(projectId);
        List<SurveyReportInfoVO> data = cacheOpsService.getOrLoad(key, cacheLoadOptionsFactory.byRelationOptions(),
                () -> {
                    Query query = new Query(Criteria.where("project_id").is(projectId).and("is_parsed").is(1));
                    List<SurveyReportInfo> reports = mongoTemplate.find(query, SurveyReportInfo.class);
                    if (CollectionUtils.isEmpty(reports)) {
                        return new ArrayList<>();
                    }
                    List<Long> fileRecordIds = reports.stream()
                            .map(SurveyReportInfo::getFileRecordId)
                            .filter(Objects::nonNull)
                            .distinct()
                            .collect(Collectors.toList());
                    Map<Long, String> fileRecordIdToOriginalName = buildFileRecordIdToOriginalNameMap(fileRecordIds);
                    return reports.stream().map(report -> toSurveyReportInfoVO(report, fileRecordIdToOriginalName))
                            .collect(Collectors.toList());
                });
        return AjaxJson.getSuccessData(data);
    }

    @Override
    public AjaxJson getRoomInfoByProjectIdAndSurveyReportId(Long projectId, Long surveyReportId) {
        if (projectId == null) {
            return AjaxJson.getError("项目ID不能为空");
        }
        if (surveyReportId == null) {
            return AjaxJson.getError("实测报告ID不能为空");
        }
        String key = projectCacheKeys.roomsByProjectAndSurveyReport(projectId, surveyReportId);
        List<RoomInfoVO> data = cacheOpsService.getOrLoad(key, cacheLoadOptionsFactory.byRelationOptions(), () -> {
            Query query = new Query(
                    Criteria.where("project_id").is(projectId).and("survey_report_info_id").is(surveyReportId));
            List<RoomInfo> roomInfos = mongoTemplate.find(query, RoomInfo.class);
            return roomInfos.stream().map(roomInfo -> {
                RoomInfoVO vo = new RoomInfoVO();
                BeanUtils.copyProperties(roomInfo, vo);
                return vo;
            }).collect(Collectors.toList());
        });
        return AjaxJson.getSuccessData(data);
    }

    @Override
    public AjaxJson querySurveyReportInfos(SurveyReportInfoQueryDTO queryDTO) {
        SurveyReportInfoQueryDTO normalized = queryDTO == null ? new SurveyReportInfoQueryDTO() : queryDTO;
        String key = projectCacheKeys.querySurveyReports(normalized);
        SurveyReportInfoQueryResultDTO result = cacheOpsService.getOrLoad(key, cacheLoadOptionsFactory.queryOptions(),
                () -> querySurveyReportInfosInternal(normalized));
        return AjaxJson.getSuccessData(result);
    }

    @Override
    public AjaxJson queryRoomInfos(RoomInfoQueryDTO queryDTO) {
        RoomInfoQueryDTO normalized = queryDTO == null ? new RoomInfoQueryDTO() : queryDTO;
        String key = projectCacheKeys.queryRooms(normalized);
        RoomInfoQueryResultDTO result = cacheOpsService.getOrLoad(key, cacheLoadOptionsFactory.queryOptions(),
                () -> queryRoomInfosInternal(normalized));
        return AjaxJson.getSuccessData(result);
    }

    @Override
    @AuditOperation(operation = OperationType.UPDATE, targetType = TargetType.SURVEY_REPORT, idParam = "p0.id")
    public AjaxJson updateSurveyReportInfo(SurveyReportInfoUpdateDTO updateDTO) {
        try {
            Objects.requireNonNull(updateDTO, "updateDTO不能为空");
            Query query = new Query(Criteria.where("_id").is(updateDTO.getId()));
            SurveyReportInfo existingReport = mongoTemplate.findOne(query, SurveyReportInfo.class);
            if (existingReport == null) {
                return AjaxJson.getError("Survey report not found");
            }
            Update update = DynamicUpdateHelper.buildDynamicUpdate(updateDTO);
            Set<String> directKeys = new HashSet<>();
            if (existingReport.getProjectId() != null) {
                directKeys.add(projectCacheKeys.parsedReportsByProject(existingReport.getProjectId()));
                directKeys.add(projectCacheKeys.roomsByProjectAndSurveyReport(existingReport.getProjectId(),
                        existingReport.getId()));
                directKeys.add(projectCacheKeys.areaComparisonByProject(existingReport.getProjectId()));
            }
            evictBeforeWrite(directKeys);
            mongoTemplate.updateFirst(query, update, SurveyReportInfo.class);
            if (hasOcrSumUpdate(updateDTO)) {
                SurveyReportInfo refreshed = mongoTemplate.findOne(query, SurveyReportInfo.class);
                if (refreshed != null) {
                    // OCR 合计变更只需重跑校验，无需重算用途并回写全部户室
                    ValidationResult result = calculationService.validateSurveyReport(refreshed);
                    refreshed.setIsVerified(result.isValid() ? 1 : 0);
                    refreshed.setVerificationErrorReason(result.getErrorMessage());
                    mongoTemplate.save(refreshed);
                }
            }
            evictAfterWrite(directKeys, projectCacheKeys.queryPattern());
            return AjaxJson.getSuccess("实测报告更新成功");
        } catch (Exception e) {
            log.error("Failed to update survey report", e);
            return AjaxJson.getError("Failed to update survey report");
        }
    }

    private static boolean hasOcrSumUpdate(SurveyReportInfoUpdateDTO updateDTO) {
        return updateDTO.getRoomInfoBuildingAreaSumFromOcr() != null
                || updateDTO.getRoomInfoInnerAreaSumFromOcr() != null
                || updateDTO.getRoomInfoBalconyAreaSumFromOcr() != null
                || updateDTO.getRoomInfoSharedAreaSumFromOcr() != null;
    }

    @Override
    @AuditOperation(operation = OperationType.CREATE, targetType = TargetType.ROOM_INFO)
    public AjaxJson createRoomInfo(RoomInfoCreateDTO createDTO) {
        try {
            Objects.requireNonNull(createDTO, "createDTO不能为空");
            Query projectQuery = new Query(Criteria.where("_id").is(createDTO.getProjectId()));
            com.gov.landcheck.core.bo.entity.Project project = mongoTemplate.findOne(projectQuery,
                    com.gov.landcheck.core.bo.entity.Project.class);
            if (project == null) {
                return AjaxJson.getError("项目不存在");
            }
            Query surveyQuery = new Query(Criteria.where("_id").is(createDTO.getSurveyReportInfoId()));
            SurveyReportInfo surveyReport = mongoTemplate.findOne(surveyQuery, SurveyReportInfo.class);
            if (surveyReport == null) {
                return AjaxJson.getError("实测报告不存在");
            }
            if (!createDTO.getProjectId().equals(surveyReport.getProjectId())) {
                return AjaxJson.getError("实测报告不属于当前项目");
            }
            String usageCategoryCode = createDTO.getUsageCategory() == null ? "" : createDTO.getUsageCategory().trim();
            UsageCategoryEnum usageCategoryEnum = null;
            for (UsageCategoryEnum e : UsageCategoryEnum.values()) {
                if (e.getCode().equals(usageCategoryCode)) {
                    usageCategoryEnum = e;
                    break;
                }
            }
            if (usageCategoryEnum == null) {
                return AjaxJson.getError(
                        "用途类别无效，可选：RESIDENTIAL、COMMERCIAL、MANAGEMENT、OTHER_BUILDABLE、COMMUNITY、OTHER_PUBLIC、UNKNOWN");
            }
            String roomLevel = RoomInfoValidator.normalizeKeyPart(createDTO.getRoomLevel());
            String roomNumber = RoomInfoValidator.normalizeKeyPart(createDTO.getRoomNumber());
            ValidationResult validation = RoomInfoValidator.validateIdentity(roomLevel, roomNumber);
            if (!validation.isValid()) {
                return AjaxJson.getError(validation.getErrorMessage());
            }
            if (hasDuplicateRoom(createDTO.getSurveyReportInfoId(), null, roomLevel, roomNumber)) {
                return AjaxJson.getError(String.format("该实测报告下已存在楼层「%s」房号「%s」的户室", roomLevel, roomNumber));
            }
            Set<String> directKeys = new HashSet<>();
            directKeys.add(projectCacheKeys.roomsByProjectAndSurveyReport(createDTO.getProjectId(),
                    createDTO.getSurveyReportInfoId()));
            directKeys.add(projectCacheKeys.parsedReportsByProject(createDTO.getProjectId()));
            directKeys.add(projectCacheKeys.areaComparisonByProject(createDTO.getProjectId()));
            evictBeforeWrite(directKeys);
            RoomInfo roomInfo = new RoomInfo();
            roomInfo.setProjectId(createDTO.getProjectId());
            roomInfo.setFileRecordId(createDTO.getFileRecordId());
            roomInfo.setSurveyReportInfoId(createDTO.getSurveyReportInfoId());
            roomInfo.setUsageCategory(usageCategoryEnum.getCode());
            roomInfo.setFloorAreaType(createDTO.getFloorAreaType() != null
                    ? createDTO.getFloorAreaType()
                    : FloorAreaTypeEnum.getByCode(usageCategoryEnum.getFloorAreaType()));
            roomInfo.setRoomLevel(roomLevel);
            roomInfo.setRoomNumber(roomNumber);
            roomInfo.setBuildingArea(createDTO.getBuildingArea());
            roomInfo.setInnerArea(createDTO.getInnerArea());
            roomInfo.setBalconyArea(createDTO.getBalconyArea());
            roomInfo.setSharedArea(createDTO.getSharedArea());
            roomInfo.setRoomStructure(createDTO.getRoomStructure());
            roomInfo.setRoomUsage(createDTO.getRoomUsage());
            roomInfo.setRemark(createDTO.getRemark());
            roomInfo.setIsCalculate(createDTO.getIsCalculate());
            roomInfo.preSave();
            RoomInfo saved = mongoTemplate.save(roomInfo);
            evictAfterWrite(directKeys, projectCacheKeys.queryPattern());
            return AjaxJson.getSuccess("户室信息创建成功", saved);
        } catch (Exception e) {
            log.error("Failed to create room info", e);
            return AjaxJson.getError("Failed to create room info");
        }
    }

    @Override
    @AuditOperation(operation = OperationType.UPDATE, targetType = TargetType.ROOM_INFO, idParam = "p0.id")
    public AjaxJson updateRoomInfo(RoomInfoUpdateDTO updateDTO) {
        try {
            Objects.requireNonNull(updateDTO, "updateDTO不能为空");
            Query query = new Query(Criteria.where("_id").is(updateDTO.getId()));
            RoomInfo existingRoom = mongoTemplate.findOne(query, RoomInfo.class);
            if (existingRoom == null) {
                return AjaxJson.getError("Room info not found");
            }
            String roomLevel = mergeRoomString(updateDTO.getRoomLevel(), existingRoom.getRoomLevel());
            String roomNumber = mergeRoomString(updateDTO.getRoomNumber(), existingRoom.getRoomNumber());
            ValidationResult validation = RoomInfoValidator.validateIdentity(roomLevel, roomNumber);
            if (!validation.isValid()) {
                return AjaxJson.getError(validation.getErrorMessage());
            }
            if (hasDuplicateRoom(existingRoom.getSurveyReportInfoId(), existingRoom.getId(), roomLevel,
                    roomNumber)) {
                return AjaxJson.getError(String.format("该实测报告下已存在楼层「%s」房号「%s」的户室", roomLevel, roomNumber));
            }
            Update update = DynamicUpdateHelper.buildDynamicUpdate(updateDTO);
            applyNormalizedRoomIdentity(update, updateDTO);
            Set<String> directKeys = new HashSet<>();
            if (existingRoom.getProjectId() != null && existingRoom.getSurveyReportInfoId() != null) {
                directKeys.add(projectCacheKeys.roomsByProjectAndSurveyReport(existingRoom.getProjectId(),
                        existingRoom.getSurveyReportInfoId()));
                directKeys.add(projectCacheKeys.areaComparisonByProject(existingRoom.getProjectId()));
            }
            evictBeforeWrite(directKeys);
            mongoTemplate.updateFirst(query, update, RoomInfo.class);
            evictAfterWrite(directKeys, projectCacheKeys.queryPattern());
            return AjaxJson.getSuccess("房间信息更新成功");
        } catch (Exception e) {
            log.error("Failed to update room info", e);
            return AjaxJson.getError("Failed to update room info");
        }
    }

    @Override
    @AuditOperation(operation = OperationType.DELETE, targetType = TargetType.ROOM_INFO, idParam = "p0")
    public AjaxJson deleteRoomInfo(Long roomId) {
        try {
            Query query = new Query(Criteria.where("_id").is(roomId));
            RoomInfo existingRoom = mongoTemplate.findOne(query, RoomInfo.class);
            if (existingRoom == null) {
                return AjaxJson.getError("Room info not found");
            }
            Set<String> directKeys = new HashSet<>();
            if (existingRoom.getProjectId() != null && existingRoom.getSurveyReportInfoId() != null) {
                directKeys.add(projectCacheKeys.roomsByProjectAndSurveyReport(existingRoom.getProjectId(),
                        existingRoom.getSurveyReportInfoId()));
                directKeys.add(projectCacheKeys.areaComparisonByProject(existingRoom.getProjectId()));
            }
            evictBeforeWrite(directKeys);
            mongoTemplate.remove(query, RoomInfo.class);
            evictAfterWrite(directKeys, projectCacheKeys.queryPattern());
            return AjaxJson.getSuccess("房间删除成功");
        } catch (Exception e) {
            log.error("Failed to delete room info", e);
            return AjaxJson.getError("Failed to delete room info");
        }
    }

    @Override
    public AjaxJson refreshSurveyReport(Long surveyReportId) {
        try {
            SurveyReportInfo surveyReport = mongoTemplate.findOne(
                    new Query(Criteria.where("_id").is(surveyReportId)), SurveyReportInfo.class);
            if (surveyReport == null) {
                return AjaxJson.getError("Survey report not found");
            }
            Long projectId = surveyReport.getProjectId();
            Set<String> directKeys = Set.of(
                    projectCacheKeys.parsedReportsByProject(projectId),
                    projectCacheKeys.roomsByProjectAndSurveyReport(projectId, surveyReportId),
                    projectCacheKeys.areaComparisonByProject(projectId));
            evictBeforeWrite(directKeys);
            applyCalculationToSurveyReport(surveyReport);
            evictAfterWrite(directKeys, projectCacheKeys.queryPattern());
            return AjaxJson.getSuccess("实测报告刷新完成");
        } catch (Exception e) {
            log.error("Failed to refresh survey report", e);
            return AjaxJson.getError("Failed to refresh survey report");
        }
    }

    private void applyCalculationToSurveyReport(SurveyReportInfo surveyReport) {
        Query roomQuery = new Query(Criteria.where("survey_report_info_id").is(surveyReport.getId()));
        List<RoomInfo> roomInfos = mongoTemplate.find(roomQuery, RoomInfo.class);
        if (roomInfos.isEmpty()) {
            return;
        }
        ValidationResult result = calculationService.calculateAndValidate(surveyReport, roomInfos);
        surveyReport.setIsVerified(result.isValid() ? 1 : 0);
        surveyReport.setVerificationErrorReason(result.getErrorMessage());
        mongoTemplate.save(surveyReport);
        for (RoomInfo roomInfo : roomInfos) {
            mongoTemplate.save(roomInfo);
        }
    }

    private Map<Long, String> buildFileRecordIdToOriginalNameMap(List<Long> fileRecordIds) {
        if (CollectionUtils.isEmpty(fileRecordIds)) {
            return Map.of();
        }
        Query query = new Query(Criteria.where("_id").in(fileRecordIds));
        List<FileRecord> fileRecords = mongoTemplate.find(query, FileRecord.class);
        return fileRecords.stream()
                .filter(fr -> fr.getId() != null)
                .collect(Collectors.toMap(FileRecord::getId,
                        fr -> fr.getOriginalName() != null ? fr.getOriginalName() : "", (a, b) -> a));
    }

    private SurveyReportInfoVO toSurveyReportInfoVO(SurveyReportInfo report,
            Map<Long, String> fileRecordIdToOriginalName) {
        SurveyReportInfoVO vo = new SurveyReportInfoVO();
        BeanUtils.copyProperties(report, vo);
        if (report.getFileRecordId() != null) {
            vo.setFileOriginalName(fileRecordIdToOriginalName.getOrDefault(report.getFileRecordId(), ""));
        }
        return vo;
    }

    private SurveyReportInfoQueryResultDTO querySurveyReportInfosInternal(SurveyReportInfoQueryDTO queryDTO) {
        Criteria criteria = MongoQueryBuilder.buildCriteria(queryDTO);
        Query query = new Query(criteria);
        int pageNum = PageSortSupport.resolvePageNum(queryDTO.getPageNum());
        int pageSize = PageSortSupport.resolvePageSize(queryDTO.getPageSize());
        Sort sort = PageSortSupport.resolveSort(queryDTO.getSortDirection(), queryDTO.getSortField(), "createTime");
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, sort);
        query.with(pageable);
        List<SurveyReportInfo> surveyReportInfos = mongoTemplate.find(query, SurveyReportInfo.class);
        long total = mongoTemplate.count(query.skip(-1).limit(-1), SurveyReportInfo.class);
        SurveyReportInfoQueryResultDTO result = new SurveyReportInfoQueryResultDTO();
        result.setRecords(surveyReportInfos);
        result.setCurrent(pageNum);
        result.setSize(pageSize);
        result.setTotal(total);
        result.setPages((int) Math.ceil((double) total / pageSize));
        return result;
    }

    private RoomInfoQueryResultDTO queryRoomInfosInternal(RoomInfoQueryDTO queryDTO) {
        Criteria criteria = MongoQueryBuilder.buildCriteria(queryDTO);
        Query query = new Query(criteria);
        int pageNum = PageSortSupport.resolvePageNum(queryDTO.getPageNum());
        int pageSize = PageSortSupport.resolvePageSize(queryDTO.getPageSize());
        Sort sort = PageSortSupport.resolveSort(queryDTO.getSortDirection(), queryDTO.getSortField(), "createTime");
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, sort);
        query.with(pageable);
        List<RoomInfo> roomInfos = mongoTemplate.find(query, RoomInfo.class);
        long total = mongoTemplate.count(query.skip(-1).limit(-1), RoomInfo.class);
        RoomInfoQueryResultDTO result = new RoomInfoQueryResultDTO();
        result.setRecords(roomInfos);
        result.setCurrent(pageNum);
        result.setSize(pageSize);
        result.setTotal(total);
        result.setPages((int) Math.ceil((double) total / pageSize));
        return result;
    }

    private boolean isText(String value) {
        return value != null && !value.isBlank();
    }

    private String mergeRoomString(String updated, String existing) {
        if (updated != null && StringUtils.hasText(updated)) {
            return RoomInfoValidator.normalizeKeyPart(updated);
        }
        return RoomInfoValidator.normalizeKeyPart(existing);
    }

    private void applyNormalizedRoomIdentity(Update update, RoomInfoUpdateDTO updateDTO) {
        if (updateDTO.getRoomLevel() != null && StringUtils.hasText(updateDTO.getRoomLevel())) {
            update.set("room_level", RoomInfoValidator.normalizeKeyPart(updateDTO.getRoomLevel()));
        }
        if (updateDTO.getRoomNumber() != null && StringUtils.hasText(updateDTO.getRoomNumber())) {
            update.set("room_number", RoomInfoValidator.normalizeKeyPart(updateDTO.getRoomNumber()));
        }
    }

    private boolean hasDuplicateRoom(Long surveyReportInfoId, Long excludeRoomId, String roomLevel,
            String roomNumber) {
        if (surveyReportInfoId == null) {
            return false;
        }
        String targetKey = RoomInfoValidator.buildIdentityKey(roomLevel, roomNumber);
        Query query = new Query(Criteria.where("survey_report_info_id").is(surveyReportInfoId));
        if (excludeRoomId != null) {
            query.addCriteria(Criteria.where("_id").ne(excludeRoomId));
        }
        List<RoomInfo> rooms = mongoTemplate.find(query, RoomInfo.class);
        for (RoomInfo room : rooms) {
            String key = RoomInfoValidator.buildIdentityKey(room.getRoomLevel(), room.getRoomNumber());
            if (targetKey.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private void evictBeforeWrite(Set<String> directKeys) {
        cacheInvalidationService.evictImmediately(directKeys);
    }

    private void evictAfterWrite(Set<String> directKeys, String queryPattern) {
        cacheInvalidationService.evictTwice(directKeys, DOUBLE_DELETE_DELAY_MS);
        cacheInvalidationService.evictByPatternTwice(Set.of(queryPattern), DOUBLE_DELETE_DELAY_MS);
    }
}
