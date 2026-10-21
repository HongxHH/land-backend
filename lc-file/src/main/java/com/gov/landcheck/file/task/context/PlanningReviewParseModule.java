package com.gov.landcheck.file.task.context;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.gov.landcheck.core.audit.TargetType;
import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.ParseFillSnapshot;
import com.gov.landcheck.core.bo.entity.ParsedDataHeader;
import com.gov.landcheck.core.bo.entity.PlanningReviewForm;
import com.gov.landcheck.core.bo.entity.PlanningReviewRow;
import com.gov.landcheck.core.config.cache.event.ProjectDataChangedEvent;
import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.file.dto.OCRPageResult;
import com.gov.landcheck.file.dto.ParseResult;
import com.gov.landcheck.file.service.parse.ParseArtifactCleanupService;
import com.gov.landcheck.file.task.base.TaskData;
import com.gov.landcheck.file.task.plan.ParsePipelinePlan;
import com.gov.landcheck.file.task.plan.ParsePipelinePlan.StageDef;
import com.gov.landcheck.file.task.processor.receiver.parse.PlanningReviewTableParser;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class PlanningReviewParseModule implements FileParseModule {

    private final PlanningReviewTableParser planningReviewTableParser;
    private final MongoTemplate mongoTemplate;
    private final FillChangePublisher fillChangePublisher;

    public PlanningReviewParseModule(
            PlanningReviewTableParser planningReviewTableParser,
            MongoTemplate mongoTemplate,
            FillChangePublisher fillChangePublisher) {
        this.planningReviewTableParser = planningReviewTableParser;
        this.mongoTemplate = mongoTemplate;
        this.fillChangePublisher = fillChangePublisher;
    }

    @Override
    public FileContextType type() {
        return FileContextType.PLANNING_REVIEW;
    }

    @Override
    public List<StageDef> stages() {
        return ParsePipelinePlan.defaultStages();
    }

    @Override
    public ParseResult parse(List<OCRPageResult> ocrResults, FileRecord fileRecord, ParsedDataHeader header)
            throws Exception {
        long startTime = System.currentTimeMillis();
        ParseResult pr = planningReviewTableParser.parse(ocrResults, header);
        long processingTime = System.currentTimeMillis() - startTime;
        pr.setProcessingTimeMs(processingTime);
        log.info("数据解析完成: fileId={}, planningRows={}, processingTime={}ms",
                fileRecord.getId(),
                pr.getPlanningReviewRows() != null ? pr.getPlanningReviewRows().size() : 0,
                processingTime);
        return pr;
    }

    @Override
    public void assertCriticalOutput(ParseResult result) {
        // 规划复核暂维持原容错策略
    }

    @Override
    public void fill(TaskData taskData) {
        Long fileRecordId = taskData.getFileRecord().getId();
        Long projectId = taskData.getFileRecord().getProjectId();

        log.debug("开始【规划复核表】数据回填: fileId={}", fileRecordId);

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

        fillChangePublisher.publish(ProjectDataChangedEvent.planningReviewChanged(projectId));
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

    @Override
    public void captureFillSnapshot(ParseFillSnapshot snapshot, Long fileRecordId) {
        Query byFile = new Query(Criteria.where("file_record_id").is(fileRecordId));
        PlanningReviewForm existing = mongoTemplate.findOne(byFile, PlanningReviewForm.class);
        snapshot.setHadExisting(existing != null);
        snapshot.setPlanningFormSnapshot(ParseArtifactCleanupService.copyPlanningFormSnapshot(existing));
        List<PlanningReviewRow> rows = mongoTemplate.find(byFile, PlanningReviewRow.class);
        snapshot.setPlanningRowsSnapshot(ParseArtifactCleanupService.copyPlanningRowSnapshots(rows));
    }

    @Override
    public void hydrateTaskData(TaskData taskData, ParseFillSnapshot snapshot) {
        boolean hadExisting = Boolean.TRUE.equals(snapshot.getHadExisting());
        taskData.setPreFillPlanningFormExisted(hadExisting);
        taskData.setPreFillPlanningFormSnapshot(
                ParseArtifactCleanupService.copyPlanningFormSnapshot(snapshot.getPlanningFormSnapshot()));
        taskData.setPreFillPlanningRowsSnapshot(
                ParseArtifactCleanupService.copyPlanningRowSnapshots(snapshot.getPlanningRowsSnapshot()));
        taskData.setFillCreatedNewPlanningForm(!hadExisting);
    }

    @Override
    public void restoreFillSnapshot(ParseFillSnapshot snapshot) {
        Long fileRecordId = snapshot.getFileRecordId();
        if (fileRecordId == null) {
            return;
        }
        boolean hadExisting = Boolean.TRUE.equals(snapshot.getHadExisting());
        Query byFile = new Query(Criteria.where("file_record_id").is(fileRecordId));
        if (hadExisting && snapshot.getPlanningFormSnapshot() != null) {
            PlanningReviewForm copy = ParseArtifactCleanupService
                    .copyPlanningFormSnapshot(snapshot.getPlanningFormSnapshot());
            copy.setUpdateTime(LocalDateTime.now());
            mongoTemplate.save(copy);
            replacePlanningRows(fileRecordId, snapshot.getPlanningRowsSnapshot());
        } else if (!hadExisting) {
            mongoTemplate.remove(byFile, PlanningReviewRow.class);
            mongoTemplate.remove(byFile, PlanningReviewForm.class);
        }
    }

    @Override
    public void revertFill(FileRecord fileRecord, TaskData taskData) {
        Long fileRecordId = fileRecord.getId();
        Long projectId = fileRecord.getProjectId();
        Query query = new Query(Criteria.where("file_record_id").is(fileRecordId));
        Query rowQuery = new Query(Criteria.where("file_record_id").is(fileRecordId));
        if (taskData != null && taskData.getPreFillPlanningFormSnapshot() != null) {
            PlanningReviewForm snapshot = taskData.getPreFillPlanningFormSnapshot();
            snapshot.setUpdateTime(LocalDateTime.now());
            mongoTemplate.save(snapshot);
            mongoTemplate.remove(rowQuery, PlanningReviewRow.class);
            List<PlanningReviewRow> rowSnapshots = taskData.getPreFillPlanningRowsSnapshot();
            if (rowSnapshots != null && !rowSnapshots.isEmpty()) {
                List<PlanningReviewRow> copies = ParseArtifactCleanupService.copyPlanningRowSnapshots(rowSnapshots);
                LocalDateTime now = LocalDateTime.now();
                for (PlanningReviewRow row : copies) {
                    row.setUpdateTime(now);
                }
                mongoTemplate.insertAll(copies);
            }
            if (projectId != null) {
                fillChangePublisher.publishImmediately(ProjectDataChangedEvent.planningReviewChanged(projectId));
            }
            return;
        }
        if (taskData != null && taskData.isFillCreatedNewPlanningForm()) {
            mongoTemplate.remove(rowQuery, PlanningReviewRow.class);
            mongoTemplate.remove(query, PlanningReviewForm.class);
            if (projectId != null) {
                fillChangePublisher.publishImmediately(ProjectDataChangedEvent.planningReviewChanged(projectId));
            }
            return;
        }
        log.warn("规划复核回填回滚跳过（无快照且非本次新建）: fileRecordId={}", fileRecordId);
    }

    @Override
    public void deleteBusinessData(FileRecord fileRecord) {
        Long fileRecordId = fileRecord.getId();
        Query rowQuery = new Query(Criteria.where("file_record_id").is(fileRecordId));
        mongoTemplate.remove(rowQuery, PlanningReviewRow.class);
        Query formQuery = new Query(Criteria.where("file_record_id").is(fileRecordId));
        List<PlanningReviewForm> forms = mongoTemplate.find(formQuery, PlanningReviewForm.class);
        mongoTemplate.remove(formQuery, PlanningReviewForm.class);
        if (forms != null) {
            for (PlanningReviewForm form : forms) {
                if (form.getProjectId() != null) {
                    fillChangePublisher.publishImmediately(
                            ProjectDataChangedEvent.planningReviewChanged(form.getProjectId()));
                }
            }
        }
        log.info("删除规划复核表数据: fileRecordId={}", fileRecordId);
    }

    @Override
    public void publishFillRestored(FileRecord fileRecord, TaskData taskData) {
        if (fileRecord.getProjectId() != null) {
            fillChangePublisher.publishImmediately(
                    ProjectDataChangedEvent.planningReviewChanged(fileRecord.getProjectId()));
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

    @Override
    public PlaceholderCreated createPlaceholder(FileRecord fileRecord, Integer phase) {
        Query query = new Query(Criteria.where("file_record_id").is(fileRecord.getId()));
        PlanningReviewForm existing = mongoTemplate.findOne(query, PlanningReviewForm.class);
        if (existing != null) {
            return null;
        }
        PlanningReviewForm form = new PlanningReviewForm();
        form.setProjectId(fileRecord.getProjectId());
        form.setFileRecordId(fileRecord.getId());
        form.setIsParsed(0);
        form.preSave();
        mongoTemplate.save(form);
        return new PlaceholderCreated(
                TargetType.PLANNING_REVIEW_FORM,
                form,
                ProjectDataChangedEvent.planningReviewChanged(form.getProjectId()),
                false);
    }
}
