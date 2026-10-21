package com.gov.landcheck.file.task.context;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.gov.landcheck.core.audit.TargetType;
import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.ParseFillSnapshot;
import com.gov.landcheck.core.bo.entity.ParsedDataHeader;
import com.gov.landcheck.core.bo.entity.ProjectPartyDeclaredTotals;
import com.gov.landcheck.core.bo.entity.ProjectPartySurveySummaryForm;
import com.gov.landcheck.core.config.cache.event.ProjectDataChangedEvent;
import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.file.dto.OCRPageResult;
import com.gov.landcheck.file.dto.ParseResult;
import com.gov.landcheck.file.service.parse.ParseArtifactCleanupService;
import com.gov.landcheck.file.task.base.TaskData;
import com.gov.landcheck.file.task.plan.ParsePipelinePlan;
import com.gov.landcheck.file.task.plan.ParsePipelinePlan.StageDef;
import com.gov.landcheck.file.task.processor.receiver.parse.ProjectPartySummaryExcelParser;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ProjectPartySummaryParseModule implements FileParseModule {

    private static final int FAIL_REMARK_MAX = 900;

    private final ProjectPartySummaryExcelParser projectPartySummaryExcelParser;
    private final MongoTemplate mongoTemplate;
    private final FillChangePublisher fillChangePublisher;

    public ProjectPartySummaryParseModule(
            ProjectPartySummaryExcelParser projectPartySummaryExcelParser,
            MongoTemplate mongoTemplate,
            FillChangePublisher fillChangePublisher) {
        this.projectPartySummaryExcelParser = projectPartySummaryExcelParser;
        this.mongoTemplate = mongoTemplate;
        this.fillChangePublisher = fillChangePublisher;
    }

    @Override
    public FileContextType type() {
        return FileContextType.PROJECT_PARTY_SURVEY_SUMMARY;
    }

    @Override
    public List<StageDef> stages() {
        return List.of(ParsePipelinePlan.PARSE, ParsePipelinePlan.FILL);
    }

    @Override
    public ParseResult parse(List<OCRPageResult> ocrResults, FileRecord fileRecord, ParsedDataHeader header)
            throws Exception {
        long startTime = System.currentTimeMillis();
        ParseResult summaryResult = projectPartySummaryExcelParser.parse(fileRecord, header);
        long processingTime = System.currentTimeMillis() - startTime;
        summaryResult.setProcessingTimeMs(processingTime);
        log.info("项目方汇总解析完成: fileId={}, parseEngine={}, processingTime={}ms",
                fileRecord.getId(),
                summaryResult.getParseEngine(),
                processingTime);
        return summaryResult;
    }

    @Override
    public void assertCriticalOutput(ParseResult result) {
        if (result == null) {
            return;
        }
        if (result.getProjectPartySummaryForm() == null) {
            throw new IllegalStateException("项目方汇总缺少主表数据，判定为不可恢复错误");
        }
        // 解析产物允许 declaredTotals 为 null（无汇总）；回填要求至少一字段非空
    }

    @Override
    public boolean skipFill(TaskData taskData) {
        if (!shouldSkipFillWithoutTotals(taskData)) {
            return false;
        }
        log.info("项目方汇总无可用汇总数据，跳过回填: fileId={}, remark={}",
                taskData.getFileRecord().getId(),
                taskData.getProjectPartySummaryForm().getRemark());
        return true;
    }

    /**
     * 解析器已判定为 PARTIAL 且无汇总数值时，跳过回填（与「无汇总则输出空、不编造」策略一致）。
     */
    static boolean shouldSkipFillWithoutTotals(TaskData taskData) {
        if (taskData == null || taskData.getFileRecord() == null) {
            return false;
        }
        ProjectPartySurveySummaryForm form = taskData.getProjectPartySummaryForm();
        if (form == null || !"PARTIAL".equals(form.getParseStatus())) {
            return false;
        }
        ProjectPartyDeclaredTotals totals = form.getDeclaredTotals();
        return totals == null || !totals.hasAnyDeclaredField();
    }

    @Override
    public void fill(TaskData taskData) {
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
        fillChangePublisher.publish(ProjectDataChangedEvent.projectPartySummaryChanged(projectId, target.getId()));
    }

    @Override
    public void captureFillSnapshot(ParseFillSnapshot snapshot, Long fileRecordId) {
        Query byFile = new Query(Criteria.where("file_record_id").is(fileRecordId));
        ProjectPartySurveySummaryForm existing = mongoTemplate.findOne(byFile, ProjectPartySurveySummaryForm.class);
        snapshot.setHadExisting(existing != null);
        snapshot.setPartySummarySnapshot(ParseArtifactCleanupService.copyPartySummarySnapshot(existing));
    }

    @Override
    public void hydrateTaskData(TaskData taskData, ParseFillSnapshot snapshot) {
        boolean hadExisting = Boolean.TRUE.equals(snapshot.getHadExisting());
        taskData.setPreFillPartySummaryExisted(hadExisting);
        taskData.setPreFillPartySummarySnapshot(
                ParseArtifactCleanupService.copyPartySummarySnapshot(snapshot.getPartySummarySnapshot()));
        taskData.setFillCreatedNewPartySummary(!hadExisting);
    }

    @Override
    public void restoreFillSnapshot(ParseFillSnapshot snapshot) {
        Long fileRecordId = snapshot.getFileRecordId();
        if (fileRecordId == null) {
            return;
        }
        boolean hadExisting = Boolean.TRUE.equals(snapshot.getHadExisting());
        Query byFile = new Query(Criteria.where("file_record_id").is(fileRecordId));
        if (hadExisting && snapshot.getPartySummarySnapshot() != null) {
            ProjectPartySurveySummaryForm copy = ParseArtifactCleanupService
                    .copyPartySummarySnapshot(snapshot.getPartySummarySnapshot());
            copy.setUpdateTime(LocalDateTime.now());
            mongoTemplate.save(copy);
        } else if (!hadExisting) {
            mongoTemplate.remove(byFile, ProjectPartySurveySummaryForm.class);
            mongoTemplate.remove(byFile, ProjectPartySurveySummaryForm.LEGACY_ROW_COLLECTION);
        }
    }

    @Override
    public void revertFill(FileRecord fileRecord, TaskData taskData) {
        Long fileRecordId = fileRecord.getId();
        Long projectId = fileRecord.getProjectId();
        Query query = new Query(Criteria.where("file_record_id").is(fileRecordId));
        if (taskData != null && taskData.getPreFillPartySummarySnapshot() != null) {
            ProjectPartySurveySummaryForm snapshot = taskData.getPreFillPartySummarySnapshot();
            snapshot.setUpdateTime(LocalDateTime.now());
            mongoTemplate.save(snapshot);
            if (snapshot.getProjectId() != null && snapshot.getId() != null) {
                fillChangePublisher.publishImmediately(ProjectDataChangedEvent.projectPartySummaryChanged(
                        snapshot.getProjectId(), snapshot.getId()));
            } else if (projectId != null) {
                fillChangePublisher.publishImmediately(
                        ProjectDataChangedEvent.projectPartySummaryChanged(projectId, null));
            }
            return;
        }
        if (taskData != null && taskData.isFillCreatedNewPartySummary()) {
            mongoTemplate.remove(query, ProjectPartySurveySummaryForm.class);
            if (projectId != null) {
                fillChangePublisher.publishImmediately(
                        ProjectDataChangedEvent.projectPartySummaryChanged(projectId, null));
            }
            return;
        }
        log.warn("项目方汇总回填回滚跳过（无快照且非本次新建）: fileRecordId={}", fileRecordId);
    }

    @Override
    public void deleteBusinessData(FileRecord fileRecord) {
        Long fileRecordId = fileRecord.getId();
        Query byFile = new Query(Criteria.where("file_record_id").is(fileRecordId));
        mongoTemplate.remove(byFile, ProjectPartySurveySummaryForm.LEGACY_ROW_COLLECTION);
        List<ProjectPartySurveySummaryForm> forms = mongoTemplate.find(byFile, ProjectPartySurveySummaryForm.class);
        mongoTemplate.remove(byFile, ProjectPartySurveySummaryForm.class);
        if (forms != null) {
            for (ProjectPartySurveySummaryForm form : forms) {
                if (form.getProjectId() != null) {
                    fillChangePublisher.publishImmediately(
                            ProjectDataChangedEvent.projectPartySummaryChanged(form.getProjectId(), form.getId()));
                }
            }
        }
        log.info("删除项目方实测汇总数据: fileRecordId={}", fileRecordId);
    }

    @Override
    public void publishFillRestored(FileRecord fileRecord, TaskData taskData) {
        ProjectPartySurveySummaryForm snapshot = taskData != null ? taskData.getPreFillPartySummarySnapshot() : null;
        if (snapshot != null && snapshot.getProjectId() != null && snapshot.getId() != null) {
            fillChangePublisher.publishImmediately(ProjectDataChangedEvent.projectPartySummaryChanged(
                    snapshot.getProjectId(), snapshot.getId()));
        } else if (fileRecord.getProjectId() != null) {
            fillChangePublisher.publishImmediately(
                    ProjectDataChangedEvent.projectPartySummaryChanged(fileRecord.getProjectId(), null));
        }
    }

    @Override
    public PlaceholderCreated createPlaceholder(FileRecord fileRecord, Integer phase) {
        Query query = new Query(Criteria.where("file_record_id").is(fileRecord.getId()));
        ProjectPartySurveySummaryForm existing = mongoTemplate.findOne(query, ProjectPartySurveySummaryForm.class);
        if (existing != null) {
            return null;
        }
        ProjectPartySurveySummaryForm form = new ProjectPartySurveySummaryForm();
        form.setProjectId(fileRecord.getProjectId());
        form.setFileRecordId(fileRecord.getId());
        form.setIsParsed(0);
        form.setParseStatus("PENDING");
        form.preSave();
        mongoTemplate.save(form);
        return new PlaceholderCreated(
                TargetType.PROJECT_PARTY_SUMMARY_FORM,
                form,
                ProjectDataChangedEvent.projectPartySummaryChanged(form.getProjectId(), form.getId()),
                false);
    }

    @Override
    public void onParseSucceeded(TaskData taskData) {
        ProjectPartySurveySummaryForm parsedForm = taskData.getProjectPartySummaryForm();
        if (parsedForm == null || taskData.getFileRecord() == null) {
            return;
        }
        Long fileRecordId = taskData.getFileRecord().getId();
        Query query = new Query(Criteria.where("file_record_id").is(fileRecordId));
        ProjectPartySurveySummaryForm existing = mongoTemplate.findOne(query, ProjectPartySurveySummaryForm.class);
        ProjectPartySurveySummaryForm target = existing != null ? existing : parsedForm;
        if (existing == null) {
            if (target.getProjectId() == null) {
                target.setProjectId(taskData.getFileRecord().getProjectId());
            }
            if (target.getFileRecordId() == null) {
                target.setFileRecordId(fileRecordId);
            }
        }
        target.setDeclaredTotals(parsedForm.getDeclaredTotals());
        target.setIsParsed(1);
        if (StringUtils.hasText(parsedForm.getParseStatus())) {
            target.setParseStatus(parsedForm.getParseStatus());
        } else if (!StringUtils.hasText(target.getParseStatus())) {
            target.setParseStatus("SUCCESS");
        }
        target.setRemark(parsedForm.getRemark());
        if (existing == null) {
            target.preSave();
        } else {
            target.setUpdateTime(LocalDateTime.now());
        }
        mongoTemplate.save(target);
        if (taskData.getFileRecord().getProjectId() != null && target.getId() != null) {
            fillChangePublisher.publishImmediately(ProjectDataChangedEvent.projectPartySummaryChanged(
                    taskData.getFileRecord().getProjectId(), target.getId()));
        }
    }

    @Override
    public void onParseFailed(FileRecord fileRecord, String errorMessage) {
        if (fileRecord == null || fileRecord.getId() == null || fileRecord.getProjectId() == null) {
            return;
        }
        Long fileRecordId = fileRecord.getId();
        Long projectId = fileRecord.getProjectId();
        String msg = StringUtils.hasText(errorMessage) ? errorMessage.trim() : "未知错误";
        if (msg.length() > FAIL_REMARK_MAX) {
            msg = msg.substring(0, FAIL_REMARK_MAX) + "…";
        }
        String remark = "解析失败: " + msg;
        Query query = new Query(Criteria.where("file_record_id").is(fileRecordId));
        ProjectPartySurveySummaryForm existing = mongoTemplate.findOne(query, ProjectPartySurveySummaryForm.class);
        Long formId;
        if (existing == null) {
            ProjectPartySurveySummaryForm inserted = new ProjectPartySurveySummaryForm();
            inserted.setProjectId(projectId);
            inserted.setFileRecordId(fileRecordId);
            inserted.setIsParsed(0);
            inserted.setParseStatus("FAILED");
            inserted.setDeclaredTotals(null);
            inserted.setRemark(remark);
            inserted.preSave();
            mongoTemplate.save(inserted);
            formId = inserted.getId();
        } else {
            Update update = new Update()
                    .set("is_parsed", 0)
                    .set("parse_status", "FAILED")
                    .set("remark", remark)
                    .set("update_time", LocalDateTime.now());
            mongoTemplate.updateFirst(query, update, ProjectPartySurveySummaryForm.class);
            formId = existing.getId();
        }
        if (formId != null) {
            fillChangePublisher.publishImmediately(
                    ProjectDataChangedEvent.projectPartySummaryChanged(projectId, formId));
        }
    }
}
