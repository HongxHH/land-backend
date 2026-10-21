package com.gov.landcheck.file.task.context;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import com.gov.landcheck.core.audit.TargetType;
import com.gov.landcheck.core.bo.entity.CapacityIndicatorInfo;
import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.ParseFillSnapshot;
import com.gov.landcheck.core.bo.entity.ParsedDataHeader;
import com.gov.landcheck.core.config.cache.event.ProjectDataChangedEvent;
import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.file.dto.OCRPageResult;
import com.gov.landcheck.file.dto.ParseResult;
import com.gov.landcheck.file.service.parse.ParseArtifactCleanupService;
import com.gov.landcheck.file.task.base.TaskData;
import com.gov.landcheck.file.task.plan.ParsePipelinePlan;
import com.gov.landcheck.file.task.plan.ParsePipelinePlan.StageDef;
import com.gov.landcheck.file.task.processor.receiver.parse.CapacityIndicatorTableParser;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CapacityIndicatorParseModule implements FileParseModule {

    private final CapacityIndicatorTableParser capacityIndicatorTableParser;
    private final MongoTemplate mongoTemplate;
    private final FillChangePublisher fillChangePublisher;

    public CapacityIndicatorParseModule(
            CapacityIndicatorTableParser capacityIndicatorTableParser,
            MongoTemplate mongoTemplate,
            FillChangePublisher fillChangePublisher) {
        this.capacityIndicatorTableParser = capacityIndicatorTableParser;
        this.mongoTemplate = mongoTemplate;
        this.fillChangePublisher = fillChangePublisher;
    }

    @Override
    public FileContextType type() {
        return FileContextType.CAPACITY_INDICATOR;
    }

    @Override
    public List<StageDef> stages() {
        return List.of(ParsePipelinePlan.OCR, ParsePipelinePlan.PARSE, ParsePipelinePlan.FILL);
    }

    @Override
    public ParseResult parse(List<OCRPageResult> ocrResults, FileRecord fileRecord, ParsedDataHeader header)
            throws Exception {
        long startTime = System.currentTimeMillis();
        ParseResult pr = capacityIndicatorTableParser.parse(ocrResults, header);
        long processingTime = System.currentTimeMillis() - startTime;
        pr.setProcessingTimeMs(processingTime);
        log.info("容量指标核查解析完成: fileId={}, processingTime={}ms",
                fileRecord.getId(), processingTime);
        return pr;
    }

    @Override
    public void assertCriticalOutput(ParseResult result) {
        if (result == null) {
            return;
        }
        if (result.getCapacityIndicatorInfo() == null
                || (result.getCapacityIndicatorInfo().getTotalArea() == null
                        && result.getCapacityIndicatorInfo().getCommercialArea() == null
                        && result.getCapacityIndicatorInfo().getResidentialArea() == null)) {
            throw new IllegalStateException("容量指标核查解析结果为空，判定为不可恢复错误");
        }
    }

    @Override
    public void fill(TaskData taskData) {
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

        fillChangePublisher.publish(ProjectDataChangedEvent.capacityIndicatorChanged(projectId));
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

    @Override
    public void captureFillSnapshot(ParseFillSnapshot snapshot, Long fileRecordId) {
        Query byFile = new Query(Criteria.where("file_record_id").is(fileRecordId));
        CapacityIndicatorInfo existing = mongoTemplate.findOne(byFile, CapacityIndicatorInfo.class);
        snapshot.setHadExisting(existing != null);
        snapshot.setCapacitySnapshot(ParseArtifactCleanupService.copyCapacitySnapshot(existing));
    }

    @Override
    public void hydrateTaskData(TaskData taskData, ParseFillSnapshot snapshot) {
        boolean hadExisting = Boolean.TRUE.equals(snapshot.getHadExisting());
        taskData.setPreFillCapacityExisted(hadExisting);
        taskData.setPreFillCapacitySnapshot(
                ParseArtifactCleanupService.copyCapacitySnapshot(snapshot.getCapacitySnapshot()));
        taskData.setFillCreatedNewCapacity(!hadExisting);
    }

    @Override
    public void restoreFillSnapshot(ParseFillSnapshot snapshot) {
        Long fileRecordId = snapshot.getFileRecordId();
        if (fileRecordId == null) {
            return;
        }
        boolean hadExisting = Boolean.TRUE.equals(snapshot.getHadExisting());
        Query byFile = new Query(Criteria.where("file_record_id").is(fileRecordId));
        if (hadExisting && snapshot.getCapacitySnapshot() != null) {
            CapacityIndicatorInfo copy = ParseArtifactCleanupService
                    .copyCapacitySnapshot(snapshot.getCapacitySnapshot());
            copy.setUpdateTime(LocalDateTime.now());
            mongoTemplate.save(copy);
        } else if (!hadExisting) {
            mongoTemplate.remove(byFile, CapacityIndicatorInfo.class);
        }
    }

    @Override
    public void revertFill(FileRecord fileRecord, TaskData taskData) {
        Long fileRecordId = fileRecord.getId();
        Long projectId = fileRecord.getProjectId();
        Query query = new Query(Criteria.where("file_record_id").is(fileRecordId));
        if (taskData != null && taskData.getPreFillCapacitySnapshot() != null) {
            CapacityIndicatorInfo snapshot = taskData.getPreFillCapacitySnapshot();
            snapshot.setUpdateTime(LocalDateTime.now());
            mongoTemplate.save(snapshot);
            if (projectId != null) {
                fillChangePublisher.publishImmediately(ProjectDataChangedEvent.capacityIndicatorChanged(projectId));
            }
            return;
        }
        if (taskData != null && taskData.isFillCreatedNewCapacity()) {
            mongoTemplate.remove(query, CapacityIndicatorInfo.class);
            if (projectId != null) {
                fillChangePublisher.publishImmediately(ProjectDataChangedEvent.capacityIndicatorChanged(projectId));
            }
            return;
        }
        log.warn("容量指标回填回滚跳过（无快照且非本次新建）: fileRecordId={}", fileRecordId);
    }

    @Override
    public void deleteBusinessData(FileRecord fileRecord) {
        Long fileRecordId = fileRecord.getId();
        Query query = new Query(Criteria.where("file_record_id").is(fileRecordId));
        List<CapacityIndicatorInfo> records = mongoTemplate.find(query, CapacityIndicatorInfo.class);
        mongoTemplate.remove(query, CapacityIndicatorInfo.class);
        if (records != null) {
            for (CapacityIndicatorInfo info : records) {
                if (info.getProjectId() != null) {
                    fillChangePublisher.publishImmediately(
                            ProjectDataChangedEvent.capacityIndicatorChanged(info.getProjectId()));
                }
            }
        }
        log.info("删除容量指标核查表数据: fileRecordId={}", fileRecordId);
    }

    @Override
    public void publishFillRestored(FileRecord fileRecord, TaskData taskData) {
        if (fileRecord.getProjectId() != null) {
            fillChangePublisher.publishImmediately(
                    ProjectDataChangedEvent.capacityIndicatorChanged(fileRecord.getProjectId()));
        }
    }

    @Override
    public PlaceholderCreated createPlaceholder(FileRecord fileRecord, Integer phase) {
        Query query = new Query(Criteria.where("file_record_id").is(fileRecord.getId()));
        CapacityIndicatorInfo existing = mongoTemplate.findOne(query, CapacityIndicatorInfo.class);
        if (existing != null) {
            return null;
        }
        CapacityIndicatorInfo info = new CapacityIndicatorInfo();
        info.setProjectId(fileRecord.getProjectId());
        info.setFileRecordId(fileRecord.getId());
        info.setIsParsed(0);
        info.preSave();
        mongoTemplate.save(info);
        return new PlaceholderCreated(
                TargetType.CAPACITY_INDICATOR_FORM,
                info,
                ProjectDataChangedEvent.capacityIndicatorChanged(info.getProjectId()),
                false);
    }
}
