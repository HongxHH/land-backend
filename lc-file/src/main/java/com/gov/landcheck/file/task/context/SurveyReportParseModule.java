package com.gov.landcheck.file.task.context;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import com.gov.landcheck.core.audit.TargetType;
import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.ParseFillSnapshot;
import com.gov.landcheck.core.bo.entity.ParsedDataHeader;
import com.gov.landcheck.core.bo.entity.ParsedDataItem;
import com.gov.landcheck.core.bo.entity.RoomInfo;
import com.gov.landcheck.core.bo.entity.SurveyReportInfo;
import com.gov.landcheck.core.config.cache.event.ProjectDataChangedEvent;
import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.core.service.SurveyReportContractApprovalSyncService;
import com.gov.landcheck.core.service.UnknownUsageRecordService;
import com.gov.landcheck.file.dto.OCRPageResult;
import com.gov.landcheck.file.dto.ParseResult;
import com.gov.landcheck.file.dto.RoomTableParseResult;
import com.gov.landcheck.file.service.parse.ParseArtifactCleanupService;
import com.gov.landcheck.file.service.parse.SurveyReportFieldUpdates;
import com.gov.landcheck.file.task.base.TaskData;
import com.gov.landcheck.file.task.plan.ParsePipelinePlan;
import com.gov.landcheck.file.task.plan.ParsePipelinePlan.StageDef;
import com.gov.landcheck.file.task.processor.receiver.DataValidReceiver;
import com.gov.landcheck.file.task.processor.receiver.parse.RoomTableParser;
import com.gov.landcheck.file.task.processor.receiver.parse.SurveyDataParser;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SurveyReportParseModule implements FileParseModule {

    private final SurveyDataParser surveyDataParser;
    private final RoomTableParser roomTableParser;
    private final MongoTemplate mongoTemplate;
    private final FillChangePublisher fillChangePublisher;
    private final SurveyReportContractApprovalSyncService surveyReportContractApprovalSyncService;
    private final UnknownUsageRecordService unknownUsageRecordService;
    private final DataValidReceiver dataValidReceiver;

    public SurveyReportParseModule(
            SurveyDataParser surveyDataParser,
            RoomTableParser roomTableParser,
            MongoTemplate mongoTemplate,
            FillChangePublisher fillChangePublisher,
            SurveyReportContractApprovalSyncService surveyReportContractApprovalSyncService,
            UnknownUsageRecordService unknownUsageRecordService,
            DataValidReceiver dataValidReceiver) {
        this.surveyDataParser = surveyDataParser;
        this.roomTableParser = roomTableParser;
        this.mongoTemplate = mongoTemplate;
        this.fillChangePublisher = fillChangePublisher;
        this.surveyReportContractApprovalSyncService = surveyReportContractApprovalSyncService;
        this.unknownUsageRecordService = unknownUsageRecordService;
        this.dataValidReceiver = dataValidReceiver;
    }

    @Override
    public FileContextType type() {
        return FileContextType.SURVEY_REPORT;
    }

    @Override
    public List<StageDef> stages() {
        return List.of(
                ParsePipelinePlan.PREPROCESS,
                ParsePipelinePlan.OCR,
                ParsePipelinePlan.PARSE,
                ParsePipelinePlan.FILL,
                ParsePipelinePlan.VALIDATE);
    }

    @Override
    public ParseResult parse(List<OCRPageResult> ocrResults, FileRecord fileRecord, ParsedDataHeader header)
            throws Exception {
        long startTime = System.currentTimeMillis();
        List<ParsedDataItem> dataItems = new ArrayList<>(surveyDataParser.parseSurveyData(ocrResults, header));
        RoomTableParseResult roomTableResult = roomTableParser.parseRoomTable(ocrResults, fileRecord, header);
        List<RoomInfo> roomInfos = new ArrayList<>(roomTableResult.getRoomInfos());
        dataItems.addAll(roomTableResult.getTotalItems());
        String parseEngine = buildSurveyParseEngine(roomTableResult);
        long processingTime = System.currentTimeMillis() - startTime;
        ParseResult result = new ParseResult(dataItems, roomInfos);
        result.setProcessingTimeMs(processingTime);
        result.setParseEngine(parseEngine);
        log.info("数据解析完成: fileId={}, items={}, roomInfos={}, processingTime={}ms",
                fileRecord.getId(), dataItems.size(), roomInfos.size(), processingTime);
        return result;
    }

    @Override
    public void assertCriticalOutput(ParseResult result) {
        if (result == null) {
            return;
        }
        if (result.getRoomInfos() == null || result.getRoomInfos().isEmpty()) {
            throw new IllegalStateException("实测报告未解析到户室面积对照表明细");
        }
    }

    @Override
    public void fill(TaskData taskData) throws Exception {
        log.debug("开始【实测报告】数据回填: fileId={}", taskData.getFileRecord().getId());

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

        if (existingInfo == null) {
            surveyReportInfo.setProjectId(taskData.getFileRecord().getProjectId());
            surveyReportInfo.setFileRecordId(taskData.getFileRecord().getId());
            surveyReportInfo.setIsParsed(0);
            surveyReportInfo.setPhase(taskData.getFileRecord().getPhase());
        }

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

        if (existingInfo != null) {
            SurveyReportFieldUpdates.applyComputedStatsReset(surveyReportInfo);
        }

        if (existingInfo == null) {
            surveyReportInfo.preSave();
        }
        mongoTemplate.save(surveyReportInfo);
        taskData.setSurveyReportInfo(surveyReportInfo);

        if (existingInfo != null) {
            unknownUsageRecordService.deleteByFileRecordId(taskData.getFileRecord().getId());
        }

        List<RoomInfo> roomInfos = taskData.getRoomInfos();
        if (roomInfos == null || roomInfos.isEmpty()) {
            syncSurveyContractApproval(surveyReportInfo.getProjectId());
            fillChangePublisher.publish(ProjectDataChangedEvent.surveyReportChanged(surveyReportInfo.getProjectId(),
                    surveyReportInfo.getId(), false));
            return;
        }

        Query deleteQuery = new Query(Criteria.where("file_record_id").is(taskData.getFileRecord().getId()));
        mongoTemplate.remove(deleteQuery, RoomInfo.class);

        for (RoomInfo roomInfo : roomInfos) {
            roomInfo.setSurveyReportInfoId(surveyReportInfo.getId());
            roomInfo.preSave();
        }
        mongoTemplate.insertAll(roomInfos);

        syncSurveyContractApproval(surveyReportInfo.getProjectId());
        fillChangePublisher.publish(ProjectDataChangedEvent.surveyReportChanged(surveyReportInfo.getProjectId(),
                surveyReportInfo.getId(), true));
    }

    @Override
    public void validate(TaskData taskData) throws Exception {
        Long fileRecordId = taskData.getFileRecord().getId();
        clearUnknownUsageRecordsForFile(fileRecordId);

        Query query = new Query(Criteria.where("file_record_id").is(fileRecordId));
        SurveyReportInfo surveyReportInfo = mongoTemplate.findOne(query, SurveyReportInfo.class);

        if (surveyReportInfo != null) {
            dataValidReceiver.validateSurveyData(surveyReportInfo, taskData.getFileRecord());
            taskData.setSurveyReportInfo(surveyReportInfo);
        }
    }

    private String buildSurveyParseEngine(RoomTableParseResult roomTableResult) {
        StringBuilder engine = new StringBuilder("REGEX");
        if (roomTableResult.isParseUsedLlm()) {
            engine.append("+LLM(户室");
            if (roomTableResult.isRoomTablePartialLlm()) {
                engine.append(",部分");
            }
            engine.append(")");
        }
        if (roomTableResult.isUsageFilledByLlm()) {
            engine.append("+LLM(用途)");
        }
        return engine.toString();
    }

    private void syncSurveyContractApproval(Long projectId) {
        if (projectId == null) {
            return;
        }
        fillChangePublisher.runAfterCommit(() -> {
            try {
                surveyReportContractApprovalSyncService.syncAllSurveyReportsInProject(projectId);
            } catch (Exception ex) {
                log.warn("同步实测报告合同/批文编号失败 projectId={}", projectId, ex);
            }
        });
    }

    private void clearUnknownUsageRecordsForFile(Long fileRecordId) {
        if (fileRecordId == null) {
            return;
        }
        try {
            long deleted = unknownUsageRecordService.deleteByFileRecordId(fileRecordId);
            if (deleted > 0) {
                log.info("已清理本文件未知用途记录, fileRecordId={}, deleted={}", fileRecordId, deleted);
            }
        } catch (Exception ex) {
            log.warn("清理未知用途记录失败: fileRecordId={}, error={}", fileRecordId, ex.getMessage());
        }
    }

    @Override
    public void captureFillSnapshot(ParseFillSnapshot snapshot, Long fileRecordId) {
        Query byFile = new Query(Criteria.where("file_record_id").is(fileRecordId));
        SurveyReportInfo existing = mongoTemplate.findOne(byFile, SurveyReportInfo.class);
        snapshot.setHadExisting(existing != null);
        snapshot.setSurveyReportSnapshot(ParseArtifactCleanupService.copySurveyReportSnapshot(existing));
        List<RoomInfo> rooms = mongoTemplate.find(byFile, RoomInfo.class);
        snapshot.setRoomsSnapshot(ParseArtifactCleanupService.copyRoomSnapshots(rooms));
    }

    @Override
    public void hydrateTaskData(TaskData taskData, ParseFillSnapshot snapshot) {
        boolean hadExisting = Boolean.TRUE.equals(snapshot.getHadExisting());
        taskData.setPreFillSurveySnapshot(
                ParseArtifactCleanupService.copySurveyReportSnapshot(snapshot.getSurveyReportSnapshot()));
        taskData.setPreFillRoomsSnapshot(ParseArtifactCleanupService.copyRoomSnapshots(snapshot.getRoomsSnapshot()));
        taskData.setFillCreatedNewSurvey(!hadExisting);
    }

    @Override
    public void restoreFillSnapshot(ParseFillSnapshot snapshot) {
        Long fileRecordId = snapshot.getFileRecordId();
        if (fileRecordId == null) {
            return;
        }
        boolean hadExisting = Boolean.TRUE.equals(snapshot.getHadExisting());
        Query byFile = new Query(Criteria.where("file_record_id").is(fileRecordId));
        if (hadExisting && snapshot.getSurveyReportSnapshot() != null) {
            SurveyReportInfo copy = ParseArtifactCleanupService
                    .copySurveyReportSnapshot(snapshot.getSurveyReportSnapshot());
            copy.setUpdateTime(LocalDateTime.now());
            mongoTemplate.save(copy);
            replaceRooms(fileRecordId, snapshot.getRoomsSnapshot());
        } else if (!hadExisting) {
            mongoTemplate.remove(byFile, SurveyReportInfo.class);
            mongoTemplate.remove(byFile, RoomInfo.class);
        }
    }

    @Override
    public void revertFill(FileRecord fileRecord, TaskData taskData) {
        Long fileRecordId = fileRecord.getId();
        Query query = new Query(Criteria.where("file_record_id").is(fileRecordId));
        if (taskData != null && taskData.getPreFillSurveySnapshot() != null) {
            SurveyReportInfo snapshot = taskData.getPreFillSurveySnapshot();
            snapshot.setUpdateTime(LocalDateTime.now());
            mongoTemplate.save(snapshot);
            if (taskData.getPreFillRoomsSnapshot() != null) {
                mongoTemplate.remove(query, RoomInfo.class);
                List<RoomInfo> roomSnapshots = taskData.getPreFillRoomsSnapshot();
                if (!roomSnapshots.isEmpty()) {
                    List<RoomInfo> copies = ParseArtifactCleanupService.copyRoomSnapshots(roomSnapshots);
                    LocalDateTime now = LocalDateTime.now();
                    for (RoomInfo room : copies) {
                        room.setUpdateTime(now);
                    }
                    mongoTemplate.insertAll(copies);
                }
            }
            if (snapshot.getProjectId() != null && snapshot.getId() != null) {
                fillChangePublisher.publishImmediately(ProjectDataChangedEvent.surveyReportChanged(
                        snapshot.getProjectId(), snapshot.getId(), true));
            }
            return;
        }
        if (taskData != null && taskData.isFillCreatedNewSurvey()) {
            List<SurveyReportInfo> removed = mongoTemplate.find(query, SurveyReportInfo.class);
            mongoTemplate.remove(query, RoomInfo.class);
            mongoTemplate.remove(query, SurveyReportInfo.class);
            for (SurveyReportInfo report : removed) {
                if (report.getProjectId() != null && report.getId() != null) {
                    fillChangePublisher.publishImmediately(ProjectDataChangedEvent.surveyReportChanged(
                            report.getProjectId(), report.getId(), true));
                }
            }
            return;
        }
        log.warn("实测回填回滚跳过（无快照且非本次新建，避免离线误删户室）: fileRecordId={}", fileRecordId);
    }

    @Override
    public void deleteBusinessData(FileRecord fileRecord) {
        Long fileRecordId = fileRecord.getId();
        Query surveyQuery = new Query(Criteria.where("file_record_id").is(fileRecordId));
        List<SurveyReportInfo> surveyReports = mongoTemplate.find(surveyQuery, SurveyReportInfo.class);
        long surveyDeletedCount = mongoTemplate.remove(surveyQuery, SurveyReportInfo.class).getDeletedCount();
        log.info("删除实测报告信息: fileRecordId={}, deletedCount={}", fileRecordId, surveyDeletedCount);
        if (!surveyReports.isEmpty()) {
            List<Long> surveyReportIds = surveyReports.stream()
                    .map(SurveyReportInfo::getId)
                    .toList();
            Query roomQuery = new Query(Criteria.where("survey_report_info_id").in(surveyReportIds));
            long roomDeletedCount = mongoTemplate.remove(roomQuery, RoomInfo.class).getDeletedCount();
            for (SurveyReportInfo report : surveyReports) {
                fillChangePublisher.publishImmediately(ProjectDataChangedEvent.surveyReportChanged(
                        report.getProjectId(), report.getId(), true));
            }
            log.info("删除房间信息: surveyReportIds={}, deletedCount={}", surveyReportIds, roomDeletedCount);
        }
    }

    @Override
    public void publishFillRestored(FileRecord fileRecord, TaskData taskData) {
        SurveyReportInfo snapshot = taskData != null ? taskData.getPreFillSurveySnapshot() : null;
        if (snapshot != null && snapshot.getProjectId() != null && snapshot.getId() != null) {
            fillChangePublisher.publishImmediately(ProjectDataChangedEvent.surveyReportChanged(
                    snapshot.getProjectId(), snapshot.getId(), true));
        } else if (fileRecord.getProjectId() != null) {
            fillChangePublisher.publishImmediately(
                    ProjectDataChangedEvent.surveyReportChanged(fileRecord.getProjectId(), null, true));
        }
    }

    private void replaceRooms(Long fileRecordId, List<RoomInfo> roomsSnapshot) {
        if (roomsSnapshot == null) {
            return;
        }
        Query byFile = new Query(Criteria.where("file_record_id").is(fileRecordId));
        mongoTemplate.remove(byFile, RoomInfo.class);
        List<RoomInfo> copies = ParseArtifactCleanupService.copyRoomSnapshots(roomsSnapshot);
        if (!copies.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            for (RoomInfo room : copies) {
                room.setUpdateTime(now);
            }
            mongoTemplate.insertAll(copies);
        }
    }

    @Override
    public PlaceholderCreated createPlaceholder(FileRecord fileRecord, Integer phase) {
        Query query = new Query(Criteria.where("file_record_id").is(fileRecord.getId()));
        SurveyReportInfo existing = mongoTemplate.findOne(query, SurveyReportInfo.class);
        if (existing != null) {
            return null;
        }
        SurveyReportInfo surveyReportInfo = new SurveyReportInfo();
        surveyReportInfo.setProjectId(fileRecord.getProjectId());
        surveyReportInfo.setFileRecordId(fileRecord.getId());
        surveyReportInfo.setPhase(phase);
        surveyReportInfo.setIsParsed(0);
        surveyReportInfo.preSave();
        mongoTemplate.save(surveyReportInfo);
        return new PlaceholderCreated(
                TargetType.SURVEY_REPORT,
                surveyReportInfo,
                ProjectDataChangedEvent.surveyReportChanged(surveyReportInfo.getProjectId(), surveyReportInfo.getId(),
                        false),
                true);
    }

    @Override
    public void onParseSucceeded(TaskData taskData) {
        SurveyReportInfo surveyReportInfo = taskData.getSurveyReportInfo();
        if (surveyReportInfo == null) {
            return;
        }
        surveyReportInfo.setIsParsed(1);
        mongoTemplate.save(surveyReportInfo);
    }
}
