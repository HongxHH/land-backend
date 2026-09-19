package com.gov.landcheck.file.task.processor.receiver;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import com.gov.landcheck.core.bo.entity.CapacityIndicatorInfo;
import com.gov.landcheck.core.bo.entity.ContractInfo;
import com.gov.landcheck.core.bo.entity.ParsedDataItem;
import com.gov.landcheck.core.bo.entity.PlanningReviewForm;
import com.gov.landcheck.core.bo.entity.PlanningReviewRow;
import com.gov.landcheck.core.bo.entity.ProjectPartySurveySummaryForm;
import com.gov.landcheck.core.bo.entity.RoomInfo;
import com.gov.landcheck.core.bo.entity.SurveyReportInfo;
import com.gov.landcheck.core.config.cache.event.ProjectDataChangedEvent;
import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.core.service.SurveyReportContractApprovalSyncService;
import com.gov.landcheck.file.service.parse.ParseArtifactCleanupService;
import com.gov.landcheck.file.task.base.TaskData;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * 数据回填处理器实现：将解析后的结构化数据回填到业务实体（合同/实测报告/房间等）中。
 */
@Slf4j
@Service
public class DataFillReceiver {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired(required = false)
    private ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    private SurveyReportContractApprovalSyncService surveyReportContractApprovalSyncService;

    private final Map<FileContextType, FillHandler> fillHandlers = new EnumMap<>(FileContextType.class);

    @FunctionalInterface
    private interface FillHandler {
        void fill(TaskData taskData) throws Exception;
    }

    @PostConstruct
    public void initHandlers() {
        fillHandlers.put(FileContextType.CONTRACT, this::fillContractData);
        fillHandlers.put(FileContextType.SURVEY_REPORT, this::fillSurveyData);
        fillHandlers.put(FileContextType.PLANNING_REVIEW, this::fillPlanningReviewData);
        fillHandlers.put(FileContextType.CAPACITY_INDICATOR, this::fillCapacityIndicatorData);
        fillHandlers.put(FileContextType.PROJECT_PARTY_SURVEY_SUMMARY, this::fillProjectPartySummaryData);
    }

    // 回填写库由 FillCommand 用短事务包住；本方法只做替换，不再单独开事务。
    public void fillData(TaskData taskData) throws Exception {
        if (taskData.getFileRecord().getFileContextType() != null) {
            FillHandler handler = fillHandlers.get(taskData.getFileRecord().getFileContextType());
            if (handler == null) {
                log.warn("数据回填跳过：未注册的文件类型 handler, fileRecordId={}, fileContextType={}",
                        taskData.getFileRecord().getId(), taskData.getFileRecord().getFileContextType());
                return;
            }
            handler.fill(taskData);
        }

    }

    private void fillContractData(TaskData taskData) throws Exception {
        log.info("开始【合同】数据回填: fileId={}", taskData.getFileRecord().getId());

        // 1. 查询并初始化合同信息
        Query query = new Query(Criteria.where("file_record_id").is(taskData.getFileRecord().getId()));
        ContractInfo existingContractInfo = mongoTemplate.findOne(query, ContractInfo.class);

        taskData.setPreFillContractExisted(existingContractInfo != null);
        if (existingContractInfo != null && taskData.getPreFillContractSnapshot() == null) {
            taskData.setPreFillContractSnapshot(ParseArtifactCleanupService.copyContractSnapshot(existingContractInfo));
        }

        ContractInfo contractInfo;
        boolean isUpdate;
        if (existingContractInfo != null) {
            contractInfo = existingContractInfo;
            isUpdate = true;
        } else {
            contractInfo = new ContractInfo();
            contractInfo.setProjectId(taskData.getFileRecord().getProjectId());
            contractInfo.setFileRecordId(taskData.getFileRecord().getId());
            isUpdate = false;
            taskData.setFillCreatedNewContract(true);
        }

        // 2. 回填合同信息
        List<ParsedDataItem> items = taskData.getParsedDataItems();
        if (items != null) {
            for (ParsedDataItem item : items) {
                String normalizedKey = item.getNormalizedKey();
                if (normalizedKey == null) {
                    continue;
                }
                switch (normalizedKey) {
                    case "contract_number" -> contractInfo.setContractNumber(item.getNormalizedValue());
                    case "transferor" -> contractInfo.setTransferor(item.getNormalizedValue());
                    case "transferee" -> contractInfo.setTransferee(item.getNormalizedValue());
                    case "total_area" -> {
                        if (item.getNormalizedNumber() != null) {
                            contractInfo.setTotalArea(item.getNormalizedNumber());
                        }
                    }
                }
            }
        }

        // 3. 保存合同信息
        if (isUpdate) {
            contractInfo.setUpdateTime(LocalDateTime.now());
            mongoTemplate.save(contractInfo);
        } else {
            contractInfo.preSave();
            mongoTemplate.save(contractInfo);
        }

        // 4. 设置合同信息
        taskData.setContractInfo(contractInfo);

        syncSurveyContractApproval(contractInfo.getProjectId());

        // 5. 发布合同变更事件
        publish(ProjectDataChangedEvent.contractChanged(contractInfo.getProjectId(), contractInfo.getId()));
    }

    private void fillSurveyData(TaskData taskData) throws Exception {
        log.debug("开始【实测报告】数据回填: fileId={}", taskData.getFileRecord().getId());

        // 1. 查询并初始化实测报告信息
        Query query = new Query(Criteria.where("file_record_id").is(taskData.getFileRecord().getId()));
        SurveyReportInfo existingInfo = mongoTemplate.findOne(query, SurveyReportInfo.class);
        SurveyReportInfo surveyReportInfo = existingInfo != null ? existingInfo : new SurveyReportInfo();

        if (existingInfo != null) {
            if (taskData.getPreFillSurveySnapshot() == null) {
                taskData.setPreFillSurveySnapshot(ParseArtifactCleanupService.copySurveyReportSnapshot(existingInfo));
            }
            if (taskData.getPreFillRoomsSnapshot() == null) {
                List<RoomInfo> existingRooms = mongoTemplate.find(query, RoomInfo.class);
                taskData.setPreFillRoomsSnapshot(ParseArtifactCleanupService.copyRoomSnapshots(existingRooms));
            }
        } else {
            taskData.setFillCreatedNewSurvey(true);
        }

        // 2. 若不存在则新建
        if (existingInfo == null) {
            surveyReportInfo.setProjectId(taskData.getFileRecord().getProjectId());
            surveyReportInfo.setFileRecordId(taskData.getFileRecord().getId());
            surveyReportInfo.setIsParsed(0);
            surveyReportInfo.setPhase(taskData.getFileRecord().getPhase());
        }

        // 2. 回填实测报告信息
        List<ParsedDataItem> dataItems = taskData.getParsedDataItems();
        if (dataItems != null) {
            for (ParsedDataItem item : dataItems) {
                String normalizedKey = item.getNormalizedKey();
                String value = item.getFieldValue();
                if (normalizedKey == null) {
                    continue;
                }
                switch (normalizedKey) {
                    case "building_name" -> surveyReportInfo.setBuildingName(value);
                    case "property_certificate" -> surveyReportInfo.setPropertyCertificateNumber(value);
                    case "real_estate_survey_report_number" ->
                        surveyReportInfo.setRealEstateSurveyReportNumber(value);
                    case "property_area_confirmation_notice_number" ->
                        surveyReportInfo.setPropertyAreaConfirmationNoticeNumber(value);
                    case "room_info_building_area_sum_from_ocr" ->
                        surveyReportInfo.setRoomInfoBuildingAreaSumFromOcr(item.getValueNumber());
                    case "room_info_inner_area_sum_from_ocr" ->
                        surveyReportInfo.setRoomInfoInnerAreaSumFromOcr(item.getValueNumber());
                    case "room_info_balcony_area_sum_from_ocr" ->
                        surveyReportInfo.setRoomInfoBalconyAreaSumFromOcr(item.getValueNumber());
                    case "room_info_shared_area_sum_from_ocr" ->
                        surveyReportInfo.setRoomInfoSharedAreaSumFromOcr(item.getValueNumber());
                }
            }
        }

        if (existingInfo == null) {
            surveyReportInfo.preSave();
        }
        mongoTemplate.save(surveyReportInfo);
        taskData.setSurveyReportInfo(surveyReportInfo);

        // 4. 回填房间信息
        List<RoomInfo> roomInfos = taskData.getRoomInfos();
        if (roomInfos == null || roomInfos.isEmpty()) {
            syncSurveyContractApproval(surveyReportInfo.getProjectId());
            publish(ProjectDataChangedEvent.surveyReportChanged(surveyReportInfo.getProjectId(),
                    surveyReportInfo.getId(), false));
            return;
        }

        // 4.1 清除旧的房间信息
        Query deleteQuery = new Query(Criteria.where("file_record_id").is(taskData.getFileRecord().getId()));
        mongoTemplate.remove(deleteQuery, RoomInfo.class);

        // 4.2 插入新的房间信息
        for (RoomInfo roomInfo : roomInfos) {
            roomInfo.setSurveyReportInfoId(surveyReportInfo.getId());
            roomInfo.preSave();
        }
        mongoTemplate.insertAll(roomInfos);

        syncSurveyContractApproval(surveyReportInfo.getProjectId());
        publish(ProjectDataChangedEvent.surveyReportChanged(surveyReportInfo.getProjectId(), surveyReportInfo.getId(),
                true));
    }

    private void syncSurveyContractApproval(Long projectId) {
        if (projectId == null) {
            return;
        }
        runAfterCommit(() -> {
            try {
                surveyReportContractApprovalSyncService.syncAllSurveyReportsInProject(projectId);
            } catch (Exception ex) {
                log.warn("同步实测报告合同/批文编号失败 projectId={}", projectId, ex);
            }
        });
    }

    private void fillPlanningReviewData(TaskData taskData) {
        Long fileRecordId = taskData.getFileRecord().getId();
        Long projectId = taskData.getFileRecord().getProjectId();

        log.debug("开始【规划复核表】数据回填: fileId={}", fileRecordId);

        // 2. 查询并初始化规划复核表信息
        Query query = new Query(Criteria.where("file_record_id").is(taskData.getFileRecord().getId()));
        PlanningReviewForm existing = mongoTemplate.findOne(query, PlanningReviewForm.class);
        taskData.setPreFillPlanningFormExisted(existing != null);
        if (existing != null) {
            if (taskData.getPreFillPlanningFormSnapshot() == null) {
                taskData.setPreFillPlanningFormSnapshot(ParseArtifactCleanupService.copyPlanningFormSnapshot(existing));
            }
            if (taskData.getPreFillPlanningRowsSnapshot() == null) {
                List<PlanningReviewRow> existingRows = mongoTemplate.find(
                        new Query(Criteria.where("file_record_id").is(fileRecordId)), PlanningReviewRow.class);
                taskData.setPreFillPlanningRowsSnapshot(
                        ParseArtifactCleanupService.copyPlanningRowSnapshots(existingRows));
            }
        }

        PlanningReviewForm target = existing != null ? existing : new PlanningReviewForm();
        if (existing == null) {
            target.setProjectId(projectId);
            target.setFileRecordId(fileRecordId);
            target.setIsParsed(0);
            taskData.setFillCreatedNewPlanningForm(true);
        }

        PlanningReviewForm parsed = taskData.getPlanningReviewForm();
        mergePlanningHeader(target, parsed);

        target.setIsParsed(1);
        if (existing == null) {
            target.preSave();
        } else {
            target.setUpdateTime(LocalDateTime.now());
        }
        mongoTemplate.save(target);
        mongoTemplate.remove(new Query(Criteria.where("file_record_id").is(fileRecordId)), PlanningReviewRow.class);

        List<PlanningReviewRow> rows = taskData.getPlanningReviewRows();
        if (rows != null && !rows.isEmpty()) {
            for (PlanningReviewRow row : rows) {
                row.setId(null);
                row.setPlanningReviewFormId(target.getId());
                row.setFileRecordId(fileRecordId);
                row.setProjectId(projectId);
                row.preSave();
            }
            mongoTemplate.insertAll(rows);
        }

        publish(ProjectDataChangedEvent.planningReviewChanged(projectId));

    }

    private void fillCapacityIndicatorData(TaskData taskData) {
        Long fileRecordId = taskData.getFileRecord().getId();
        Long projectId = taskData.getFileRecord().getProjectId();

        log.debug("开始【容量指标核查表】数据回填: fileId={}", fileRecordId);

        Query query = new Query(Criteria.where("file_record_id").is(fileRecordId));
        CapacityIndicatorInfo existing = mongoTemplate.findOne(query, CapacityIndicatorInfo.class);
        taskData.setPreFillCapacityExisted(existing != null);
        if (existing != null && taskData.getPreFillCapacitySnapshot() == null) {
            taskData.setPreFillCapacitySnapshot(ParseArtifactCleanupService.copyCapacitySnapshot(existing));
        }

        CapacityIndicatorInfo target = existing != null ? existing : new CapacityIndicatorInfo();
        if (existing == null) {
            target.setProjectId(projectId);
            target.setFileRecordId(fileRecordId);
            target.setIsParsed(0);
            taskData.setFillCreatedNewCapacity(true);
        }

        CapacityIndicatorInfo parsed = taskData.getCapacityIndicatorInfo();
        mergeCapacityIndicatorAreas(target, parsed);

        target.setIsParsed(1);
        if (existing == null) {
            target.preSave();
        } else {
            target.setUpdateTime(LocalDateTime.now());
        }
        mongoTemplate.save(target);
        taskData.setCapacityIndicatorInfo(target);

        publish(ProjectDataChangedEvent.capacityIndicatorChanged(projectId));
    }

    private static void mergeCapacityIndicatorAreas(CapacityIndicatorInfo target, CapacityIndicatorInfo src) {
        if (src == null) {
            return;
        }
        if (src.getTotalArea() != null) {
            target.setTotalArea(src.getTotalArea());
        }
        if (src.getCommercialArea() != null) {
            target.setCommercialArea(src.getCommercialArea());
        }
        if (src.getResidentialArea() != null) {
            target.setResidentialArea(src.getResidentialArea());
        }
    }

    private static void mergePlanningHeader(PlanningReviewForm target, PlanningReviewForm src) {
        if (src == null) {
            return;
        }
        if (StringUtils.hasText(src.getProjectName())) {
            target.setProjectName(src.getProjectName());
        }
        if (StringUtils.hasText(src.getConstructionUnit())) {
            target.setConstructionUnit(src.getConstructionUnit());
        }
        if (StringUtils.hasText(src.getDesignUnit())) {
            target.setDesignUnit(src.getDesignUnit());
        }
        if (StringUtils.hasText(src.getConstructionLocation())) {
            target.setConstructionLocation(src.getConstructionLocation());
        }
        if (StringUtils.hasText(src.getLandUseNature())) {
            target.setLandUseNature(src.getLandUseNature());
        }
        if (StringUtils.hasText(src.getContactPerson())) {
            target.setContactPerson(src.getContactPerson());
        }
        if (StringUtils.hasText(src.getContactPhone())) {
            target.setContactPhone(src.getContactPhone());
        }
        if (StringUtils.hasText(src.getRemarks())) {
            target.setRemarks(src.getRemarks());
        }
    }

    private void fillProjectPartySummaryData(TaskData taskData) {
        Long fileRecordId = taskData.getFileRecord().getId();
        Long projectId = taskData.getFileRecord().getProjectId();
        log.info("开始项目方汇总回填: fileId={}", fileRecordId);

        ProjectPartySurveySummaryForm parsed = taskData.getProjectPartySummaryForm();
        if (parsed == null) {
            throw new IllegalStateException("项目方汇总回填失败：解析主表为空");
        }
        if (parsed.getDeclaredTotals() == null || !parsed.getDeclaredTotals().hasAnyDeclaredField()) {
            throw new IllegalStateException("项目方汇总回填失败：解析汇总数据为空");
        }

        Query query = new Query(Criteria.where("file_record_id").is(fileRecordId));
        ProjectPartySurveySummaryForm existing = mongoTemplate.findOne(query, ProjectPartySurveySummaryForm.class);
        taskData.setPreFillPartySummaryExisted(existing != null);
        if (existing != null && taskData.getPreFillPartySummarySnapshot() == null) {
            taskData.setPreFillPartySummarySnapshot(ParseArtifactCleanupService.copyPartySummarySnapshot(existing));
        }

        ProjectPartySurveySummaryForm target = existing != null ? existing : new ProjectPartySurveySummaryForm();
        if (existing == null) {
            target.setProjectId(projectId);
            target.setFileRecordId(fileRecordId);
            target.setIsParsed(0);
            taskData.setFillCreatedNewPartySummary(true);
        }

        target.setDeclaredTotals(parsed.getDeclaredTotals());
        target.setParseStatus(parsed.getParseStatus());
        if ("PARTIAL".equals(parsed.getParseStatus())) {
            target.setRemark(StringUtils.hasText(parsed.getRemark()) ? parsed.getRemark() : "解析部分成功：存在缺失字段");
        } else {
            target.setRemark(parsed.getRemark());
        }
        if (!StringUtils.hasText(target.getParseStatus())) {
            target.setParseStatus("SUCCESS");
        }
        target.setIsParsed(1);
        if (existing == null) {
            target.preSave();
        } else {
            target.setUpdateTime(LocalDateTime.now());
        }
        mongoTemplate.save(target);

        mongoTemplate.remove(new Query(Criteria.where("file_record_id").is(fileRecordId)),
                ProjectPartySurveySummaryForm.LEGACY_ROW_COLLECTION);

        taskData.setProjectPartySummaryForm(target);
        publish(ProjectDataChangedEvent.projectPartySummaryChanged(projectId, target.getId()));
    }

    private void publish(ProjectDataChangedEvent event) {
        if (applicationEventPublisher == null || event == null || !event.hasAnyId()) {
            return;
        }
        runAfterCommit(() -> doPublish(event));
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }

    private void doPublish(ProjectDataChangedEvent event) {
        try {
            applicationEventPublisher.publishEvent(event);
        } catch (Exception ex) {
            log.warn("Publish ProjectDataChangedEvent failed, event={}", event, ex);
        }
    }
}
