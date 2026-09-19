package com.gov.landcheck.file.service.parse;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import com.gov.landcheck.core.bo.entity.CapacityIndicatorInfo;
import com.gov.landcheck.core.bo.entity.ContractInfo;
import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.ParseFillSnapshot;
import com.gov.landcheck.core.bo.entity.ParseJob;
import com.gov.landcheck.core.bo.entity.PlanningReviewForm;
import com.gov.landcheck.core.bo.entity.PlanningReviewRow;
import com.gov.landcheck.core.bo.entity.ProjectPartySurveySummaryForm;
import com.gov.landcheck.core.bo.entity.RoomInfo;
import com.gov.landcheck.core.bo.entity.SurveyReportInfo;
import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.file.task.base.TaskData;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * 回填前独立提交业务快照；失败/崩溃 restore，SUCCESS 后删除。
 */
@Slf4j
@Service
public class ParseFillSnapshotService {

    private static final int RESTORE_TX_TIMEOUT_SECONDS = 30;

    @Resource
    private MongoTemplate mongoTemplate;

    @Resource
    private PlatformTransactionManager transactionManager;

    @PostConstruct
    void ensureParseJobIdUnique() {
        mongoTemplate.indexOps(ParseFillSnapshot.class).createIndex(
                new Index().named("uk_parse_job_id").on("parse_job_id", Sort.Direction.ASC).unique());
    }

    public ParseFillSnapshot captureIfAbsent(TaskData taskData) {
        if (taskData == null || taskData.getParseJob() == null || taskData.getFileRecord() == null) {
            return null;
        }
        ParseJob parseJob = taskData.getParseJob();
        FileRecord fileRecord = taskData.getFileRecord();
        if (parseJob.getId() == null || fileRecord.getId() == null) {
            return null;
        }
        ParseFillSnapshot existing = findByParseJobId(parseJob.getId());
        if (existing != null) {
            hydrateTaskData(taskData, existing);
            log.info("回填快照已存在，跳过覆盖: parseJobId={}, fileRecordId={}", parseJob.getId(), fileRecord.getId());
            return existing;
        }
        dropStaleSnapshots(fileRecord.getId(), parseJob.getId());
        ParseFillSnapshot snapshot = buildSnapshot(parseJob, fileRecord);
        try {
            snapshot.preSave();
            mongoTemplate.insert(snapshot);
            log.info("已落盘回填快照: parseJobId={}, fileRecordId={}, context={}, hadExisting={}",
                    parseJob.getId(), fileRecord.getId(), snapshot.getFileContextType(), snapshot.getHadExisting());
        } catch (DuplicateKeyException ex) {
            snapshot = findByParseJobId(parseJob.getId());
            log.info("回填快照并发写入，使用已有文档: parseJobId={}", parseJob.getId());
        }
        if (snapshot != null) {
            hydrateTaskData(taskData, snapshot);
        }
        return snapshot;
    }

    public boolean restoreIfPresent(TaskData taskData) {
        if (taskData == null || taskData.getParseJob() == null || taskData.getParseJob().getId() == null) {
            return false;
        }
        ParseFillSnapshot snapshot = findByParseJobId(taskData.getParseJob().getId());
        if (snapshot == null) {
            return false;
        }
        hydrateTaskData(taskData, snapshot);
        runRestoreTransaction(() -> {
            restoreFromSnapshot(snapshot);
            deleteByParseJobId(snapshot.getParseJobId());
        });
        log.info("已从落盘快照恢复业务数据: parseJobId={}, fileRecordId={}, context={}, hadExisting={}",
                snapshot.getParseJobId(), snapshot.getFileRecordId(), snapshot.getFileContextType(),
                snapshot.getHadExisting());
        return true;
    }

    public void deleteByParseJobId(Long parseJobId) {
        if (parseJobId == null) {
            return;
        }
        long deleted = mongoTemplate.remove(byParseJobId(parseJobId), ParseFillSnapshot.class).getDeletedCount();
        if (deleted > 0) {
            log.info("已删除回填快照: parseJobId={}, deleted={}", parseJobId, deleted);
        }
    }

    private ParseFillSnapshot findByParseJobId(Long parseJobId) {
        if (parseJobId == null) {
            return null;
        }
        return mongoTemplate.findOne(byParseJobId(parseJobId), ParseFillSnapshot.class);
    }

    private static Query byParseJobId(Long parseJobId) {
        return new Query(Criteria.where("parse_job_id").is(parseJobId));
    }

    private void dropStaleSnapshots(Long fileRecordId, Long currentParseJobId) {
        Query stale = new Query(Criteria.where("file_record_id").is(fileRecordId)
                .and("parse_job_id").ne(currentParseJobId));
        long deleted = mongoTemplate.remove(stale, ParseFillSnapshot.class).getDeletedCount();
        if (deleted > 0) {
            log.info("已清理同文件残留回填快照: fileRecordId={}, currentParseJobId={}, deleted={}",
                    fileRecordId, currentParseJobId, deleted);
        }
    }

    private void runRestoreTransaction(Runnable action) {
        TransactionTemplate tpl = new TransactionTemplate(transactionManager);
        tpl.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        tpl.setTimeout(RESTORE_TX_TIMEOUT_SECONDS);
        try {
            tpl.executeWithoutResult(status -> action.run());
        } catch (TransactionException ex) {
            log.warn("快照恢复未能走 Mongo 事务，降级为顺序写入: {}", ex.getMessage());
            action.run();
        }
    }

    private ParseFillSnapshot buildSnapshot(ParseJob parseJob, FileRecord fileRecord) {
        ParseFillSnapshot snapshot = new ParseFillSnapshot();
        snapshot.setParseJobId(parseJob.getId());
        snapshot.setFileRecordId(fileRecord.getId());
        snapshot.setProjectId(fileRecord.getProjectId());
        FileContextType contextType = fileRecord.getFileContextType();
        if (contextType == null) {
            contextType = parseJob.getFileContextType();
        }
        snapshot.setFileContextType(contextType);
        snapshot.setHadExisting(false);
        if (contextType == null) {
            return snapshot;
        }
        Long fileRecordId = fileRecord.getId();
        Query byFile = new Query(Criteria.where("file_record_id").is(fileRecordId));
        switch (contextType) {
            case CONTRACT -> {
                ContractInfo existing = mongoTemplate.findOne(byFile, ContractInfo.class);
                snapshot.setHadExisting(existing != null);
                snapshot.setContractSnapshot(ParseArtifactCleanupService.copyContractSnapshot(existing));
            }
            case SURVEY_REPORT -> {
                SurveyReportInfo existing = mongoTemplate.findOne(byFile, SurveyReportInfo.class);
                snapshot.setHadExisting(existing != null);
                snapshot.setSurveyReportSnapshot(ParseArtifactCleanupService.copySurveyReportSnapshot(existing));
                List<RoomInfo> rooms = mongoTemplate.find(byFile, RoomInfo.class);
                snapshot.setRoomsSnapshot(ParseArtifactCleanupService.copyRoomSnapshots(rooms));
            }
            case PLANNING_REVIEW -> {
                PlanningReviewForm existing = mongoTemplate.findOne(byFile, PlanningReviewForm.class);
                snapshot.setHadExisting(existing != null);
                snapshot.setPlanningFormSnapshot(ParseArtifactCleanupService.copyPlanningFormSnapshot(existing));
                List<PlanningReviewRow> rows = mongoTemplate.find(byFile, PlanningReviewRow.class);
                snapshot.setPlanningRowsSnapshot(ParseArtifactCleanupService.copyPlanningRowSnapshots(rows));
            }
            case CAPACITY_INDICATOR -> {
                CapacityIndicatorInfo existing = mongoTemplate.findOne(byFile, CapacityIndicatorInfo.class);
                snapshot.setHadExisting(existing != null);
                snapshot.setCapacitySnapshot(ParseArtifactCleanupService.copyCapacitySnapshot(existing));
            }
            case PROJECT_PARTY_SURVEY_SUMMARY -> {
                ProjectPartySurveySummaryForm existing = mongoTemplate.findOne(byFile,
                        ProjectPartySurveySummaryForm.class);
                snapshot.setHadExisting(existing != null);
                snapshot.setPartySummarySnapshot(ParseArtifactCleanupService.copyPartySummarySnapshot(existing));
            }
            default -> {
            }
        }
        return snapshot;
    }

    void hydrateTaskData(TaskData taskData, ParseFillSnapshot snapshot) {
        if (taskData == null || snapshot == null) {
            return;
        }
        boolean hadExisting = Boolean.TRUE.equals(snapshot.getHadExisting());
        FileContextType contextType = snapshot.getFileContextType();
        if (contextType == null) {
            return;
        }
        switch (contextType) {
            case CONTRACT -> {
                taskData.setPreFillContractExisted(hadExisting);
                taskData.setPreFillContractSnapshot(
                        ParseArtifactCleanupService.copyContractSnapshot(snapshot.getContractSnapshot()));
                taskData.setFillCreatedNewContract(!hadExisting);
            }
            case SURVEY_REPORT -> {
                taskData.setPreFillSurveySnapshot(
                        ParseArtifactCleanupService.copySurveyReportSnapshot(snapshot.getSurveyReportSnapshot()));
                taskData.setPreFillRoomsSnapshot(
                        ParseArtifactCleanupService.copyRoomSnapshots(snapshot.getRoomsSnapshot()));
                taskData.setFillCreatedNewSurvey(!hadExisting);
            }
            case PLANNING_REVIEW -> {
                taskData.setPreFillPlanningFormExisted(hadExisting);
                taskData.setPreFillPlanningFormSnapshot(
                        ParseArtifactCleanupService.copyPlanningFormSnapshot(snapshot.getPlanningFormSnapshot()));
                taskData.setPreFillPlanningRowsSnapshot(
                        ParseArtifactCleanupService.copyPlanningRowSnapshots(snapshot.getPlanningRowsSnapshot()));
                taskData.setFillCreatedNewPlanningForm(!hadExisting);
            }
            case CAPACITY_INDICATOR -> {
                taskData.setPreFillCapacityExisted(hadExisting);
                taskData.setPreFillCapacitySnapshot(
                        ParseArtifactCleanupService.copyCapacitySnapshot(snapshot.getCapacitySnapshot()));
                taskData.setFillCreatedNewCapacity(!hadExisting);
            }
            case PROJECT_PARTY_SURVEY_SUMMARY -> {
                taskData.setPreFillPartySummaryExisted(hadExisting);
                taskData.setPreFillPartySummarySnapshot(
                        ParseArtifactCleanupService.copyPartySummarySnapshot(snapshot.getPartySummarySnapshot()));
                taskData.setFillCreatedNewPartySummary(!hadExisting);
            }
            default -> {
            }
        }
    }

    private void restoreFromSnapshot(ParseFillSnapshot snapshot) {
        FileContextType contextType = snapshot.getFileContextType();
        Long fileRecordId = snapshot.getFileRecordId();
        if (contextType == null || fileRecordId == null) {
            return;
        }
        boolean hadExisting = Boolean.TRUE.equals(snapshot.getHadExisting());
        Query byFile = new Query(Criteria.where("file_record_id").is(fileRecordId));
        LocalDateTime now = LocalDateTime.now();
        switch (contextType) {
            case CONTRACT -> {
                if (hadExisting && snapshot.getContractSnapshot() != null) {
                    ContractInfo copy = ParseArtifactCleanupService.copyContractSnapshot(snapshot.getContractSnapshot());
                    copy.setUpdateTime(now);
                    mongoTemplate.save(copy);
                } else if (!hadExisting) {
                    mongoTemplate.remove(byFile, ContractInfo.class);
                }
            }
            case SURVEY_REPORT -> {
                if (hadExisting && snapshot.getSurveyReportSnapshot() != null) {
                    SurveyReportInfo copy = ParseArtifactCleanupService
                            .copySurveyReportSnapshot(snapshot.getSurveyReportSnapshot());
                    copy.setUpdateTime(now);
                    mongoTemplate.save(copy);
                    replaceRooms(fileRecordId, snapshot.getRoomsSnapshot());
                } else if (!hadExisting) {
                    mongoTemplate.remove(byFile, SurveyReportInfo.class);
                    mongoTemplate.remove(byFile, RoomInfo.class);
                }
            }
            case PLANNING_REVIEW -> {
                if (hadExisting && snapshot.getPlanningFormSnapshot() != null) {
                    PlanningReviewForm copy = ParseArtifactCleanupService
                            .copyPlanningFormSnapshot(snapshot.getPlanningFormSnapshot());
                    copy.setUpdateTime(now);
                    mongoTemplate.save(copy);
                    replacePlanningRows(fileRecordId, snapshot.getPlanningRowsSnapshot());
                } else if (!hadExisting) {
                    mongoTemplate.remove(byFile, PlanningReviewRow.class);
                    mongoTemplate.remove(byFile, PlanningReviewForm.class);
                }
            }
            case CAPACITY_INDICATOR -> {
                if (hadExisting && snapshot.getCapacitySnapshot() != null) {
                    CapacityIndicatorInfo copy = ParseArtifactCleanupService
                            .copyCapacitySnapshot(snapshot.getCapacitySnapshot());
                    copy.setUpdateTime(now);
                    mongoTemplate.save(copy);
                } else if (!hadExisting) {
                    mongoTemplate.remove(byFile, CapacityIndicatorInfo.class);
                }
            }
            case PROJECT_PARTY_SURVEY_SUMMARY -> {
                if (hadExisting && snapshot.getPartySummarySnapshot() != null) {
                    ProjectPartySurveySummaryForm copy = ParseArtifactCleanupService
                            .copyPartySummarySnapshot(snapshot.getPartySummarySnapshot());
                    copy.setUpdateTime(now);
                    mongoTemplate.save(copy);
                } else if (!hadExisting) {
                    mongoTemplate.remove(byFile, ProjectPartySurveySummaryForm.class);
                    mongoTemplate.remove(byFile, ProjectPartySurveySummaryForm.LEGACY_ROW_COLLECTION);
                }
            }
            default -> {
            }
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

    private void replacePlanningRows(Long fileRecordId, List<PlanningReviewRow> rowsSnapshot) {
        Query byFile = new Query(Criteria.where("file_record_id").is(fileRecordId));
        mongoTemplate.remove(byFile, PlanningReviewRow.class);
        List<PlanningReviewRow> copies = ParseArtifactCleanupService.copyPlanningRowSnapshots(rowsSnapshot);
        if (!copies.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            for (PlanningReviewRow row : copies) {
                row.setUpdateTime(now);
            }
            mongoTemplate.insertAll(copies);
        }
    }
}
