package com.gov.landcheck.file.service.parse;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.gov.landcheck.core.bo.entity.CapacityIndicatorInfo;
import com.gov.landcheck.core.bo.entity.ContractInfo;
import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.OCRExecutionResult;
import com.gov.landcheck.core.bo.entity.ParseJob;
import com.gov.landcheck.core.bo.entity.ParsedDataHeader;
import com.gov.landcheck.core.bo.entity.ParsedDataItem;
import com.gov.landcheck.core.bo.entity.PlanningReviewForm;
import com.gov.landcheck.core.bo.entity.PlanningReviewRow;
import com.gov.landcheck.core.bo.entity.ProjectPartyDeclaredTotals;
import com.gov.landcheck.core.bo.entity.ProjectPartySurveySummaryForm;
import com.gov.landcheck.core.bo.entity.RoomInfo;
import com.gov.landcheck.core.bo.entity.SurveyReportInfo;
import com.gov.landcheck.core.config.cache.event.ProjectDataChangedEvent;
import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.core.enums.ParseJobStateEnum;
import com.gov.landcheck.core.service.UnknownUsageRecordService;
import com.gov.landcheck.file.task.base.TaskData;
import com.gov.landcheck.file.task.processor.receiver.PdfPreReceiver;
import com.gov.landcheck.file.utils.GridFSUtils;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * 解析失败 / 取消 / 崩溃恢复时的中间产物与半回填业务数据统一清理。
 */
@Slf4j
@Service
public class ParseArtifactCleanupService {

    @Resource
    private MongoTemplate mongoTemplate;

    @Resource
    private GridFSUtils gridFSUtils;

    @Resource
    private PdfPreReceiver pdfPreprocessor;

    @Resource
    private UnknownUsageRecordService unknownUsageRecordService;

    @Autowired(required = false)
    private ApplicationEventPublisher applicationEventPublisher;

    @Resource
    private ParseFillSnapshotService parseFillSnapshotService;

    public void cleanupBeforeMarkFailed(List<ParseJob> hangingJobs) {
        if (hangingJobs == null || hangingJobs.isEmpty()) {
            return;
        }
        for (ParseJob parseJob : hangingJobs) {
            if (parseJob == null || parseJob.getFileRecordId() == null) {
                continue;
            }
            FileRecord fileRecord = mongoTemplate.findById(parseJob.getFileRecordId(), FileRecord.class);
            if (fileRecord == null) {
                log.warn("崩溃恢复跳过清理：文件记录不存在, parseJobId={}, fileRecordId={}",
                        parseJob.getId(), parseJob.getFileRecordId());
                continue;
            }
            TaskData taskData = ParseJobStageHelper.taskDataForOfflineRollback(parseJob, fileRecord);
            ParseRollbackSummary summary = rollbackAllStages(parseJob, fileRecord, taskData);
            if (summary.hasFailures()) {
                log.warn("崩溃恢复清理存在失败项: parseJobId={}, fileRecordId={}, summary={}",
                        parseJob.getId(), fileRecord.getId(), summary);
            } else {
                log.info("崩溃恢复已清理中间产物: parseJobId={}, fileRecordId={}, summary={}",
                        parseJob.getId(), fileRecord.getId(), summary);
            }
        }
    }

    public ParseRollbackSummary rollbackAllStages(ParseJob parseJob, FileRecord fileRecord, TaskData taskData) {
        ParseRollbackSummary summary = new ParseRollbackSummary();
        if (parseJob == null || fileRecord == null) {
            return summary;
        }
        FileContextType contextType = fileRecord.getFileContextType();
        if (contextType == null) {
            contextType = parseJob.getFileContextType();
        }

        if (contextType == FileContextType.SURVEY_REPORT) {
            summary.merge(rollbackValidate(taskData));
        }
        summary.merge(rollbackFill(taskData));
        summary.merge(rollbackParse(taskData));
        if (contextType != FileContextType.PROJECT_PARTY_SURVEY_SUMMARY) {
            summary.merge(rollbackOcr(taskData));
            summary.merge(rollbackPreprocess(taskData));
        }

        clearIntermediateReferences(parseJob, fileRecord, taskData, summary);
        return summary;
    }

    public ParseRollbackSummary rollbackPreprocess(TaskData taskData) {
        ParseRollbackSummary summary = new ParseRollbackSummary();
        if (taskData == null || taskData.getFileRecord() == null) {
            return summary;
        }
        FileRecord fileRecord = taskData.getFileRecord();
        try {
            String preprocessGridfsId = taskData.getPreprocessGridfsId();
            if (!StringUtils.hasText(preprocessGridfsId)) {
                preprocessGridfsId = fileRecord.getPreprocessGridfsId();
            }
            ParseJob parseJob = taskData.getParseJob();
            if (!StringUtils.hasText(preprocessGridfsId) && parseJob != null) {
                preprocessGridfsId = parseJob.getPreprocessGridfsId();
            }
            if (!StringUtils.hasText(preprocessGridfsId)) {
                summary.recordSuccess("PREPROCESS: 无需清理");
                return summary;
            }
            pdfPreprocessor.deleteExistingPreprocessResult(preprocessGridfsId);
            fileRecord.setPreprocessGridfsId(null);
            taskData.setPreprocessGridfsId(null);
            mongoTemplate.save(fileRecord);
            summary.recordSuccess("PREPROCESS: 已删除预处理 GridFS");
        } catch (Exception ex) {
            summary.recordFailure("PREPROCESS", ex.getMessage());
            log.warn("预处理回滚失败: fileId={}, error={}", fileRecord.getId(), ex.getMessage(), ex);
        }
        return summary;
    }

    public ParseRollbackSummary rollbackOcr(TaskData taskData) {
        ParseRollbackSummary summary = new ParseRollbackSummary();
        if (taskData == null || taskData.getFileRecord() == null || taskData.getParseJob() == null) {
            return summary;
        }
        Long fileId = taskData.getFileRecord().getId();
        Long parseJobId = taskData.getParseJob().getId();
        try {
            Query query = new Query(Criteria.where("parse_job_id").is(parseJobId));
            List<OCRExecutionResult> results = mongoTemplate.find(query, OCRExecutionResult.class);
            if (results == null || results.isEmpty()) {
                summary.recordSuccess("OCR: 无 OCRExecutionResult");
            } else {
                for (OCRExecutionResult executionResult : results) {
                    safeDeleteGridFs(executionResult.getOcrResultJsonGridfsId(), summary, "OCR_JSON");
                    safeDeleteGridFs(executionResult.getMarkdownFileGridfsId(), summary, "OCR_MD");
                    mongoTemplate.remove(executionResult);
                }
                summary.recordSuccess("OCR: 已删除 " + results.size() + " 条 OCRExecutionResult");
            }
        } catch (Exception ex) {
            summary.recordFailure("OCR", ex.getMessage());
            log.warn("OCR 回滚失败: fileId={}, parseJobId={}, error={}", fileId, parseJobId, ex.getMessage(), ex);
        }
        taskData.setOcrProcessResult(null);
        return summary;
    }

    public ParseRollbackSummary rollbackParse(TaskData taskData) {
        ParseRollbackSummary summary = new ParseRollbackSummary();
        if (taskData == null || taskData.getParseJob() == null || taskData.getFileRecord() == null) {
            return summary;
        }
        Long parseJobId = taskData.getParseJob().getId();
        Long fileRecordId = taskData.getFileRecord().getId();
        try {
            Query headerQuery = new Query(Criteria.where("parse_job_id").is(parseJobId));
            List<ParsedDataHeader> headers = mongoTemplate.find(headerQuery, ParsedDataHeader.class);
            if (headers == null || headers.isEmpty()) {
                summary.recordSuccess("PARSE: 无 ParsedDataHeader");
            } else {
                long totalItems = 0;
                for (ParsedDataHeader header : headers) {
                    deleteHeaderGridFsRefs(header, summary);
                    Long headerId = header.getId();
                    if (headerId != null) {
                        Query itemQuery = new Query(Criteria.where("header_id").is(headerId));
                        totalItems += mongoTemplate.remove(itemQuery, ParsedDataItem.class).getDeletedCount();
                    }
                    mongoTemplate.remove(header);
                }
                summary.recordSuccess("PARSE: headers=" + headers.size() + ", items=" + totalItems);
            }
        } catch (Exception ex) {
            summary.recordFailure("PARSE", ex.getMessage());
            log.warn("解析回滚失败: fileRecordId={}, parseJobId={}, error={}",
                    fileRecordId, parseJobId, ex.getMessage(), ex);
        }
        taskData.setParsedDataHeader(null);
        taskData.setParsedDataItems(null);
        taskData.setRoomInfos(null);
        taskData.setPlanningReviewForm(null);
        taskData.setPlanningReviewRows(null);
        taskData.setProjectPartySummaryForm(null);
        taskData.setCapacityIndicatorInfo(null);
        return summary;
    }

    public ParseRollbackSummary rollbackFill(TaskData taskData) {
        ParseRollbackSummary summary = new ParseRollbackSummary();
        if (taskData == null || taskData.getFileRecord() == null || taskData.getParseJob() == null) {
            return summary;
        }
        if (!ParseJobStageHelper.hasFillStageStarted(taskData.getParseJob(), taskData)) {
            summary.recordSuccess("FILL: 未进入回填阶段，跳过");
            return summary;
        }
        try {
            if (parseFillSnapshotService.restoreIfPresent(taskData)) {
                publishFillRestoredEvents(taskData.getFileRecord(), taskData);
                summary.recordSuccess("FILL: 已从落盘快照恢复业务数据");
                return summary;
            }
            revertFillBusinessState(taskData.getFileRecord(), taskData);
            summary.recordSuccess("FILL: 已恢复业务回填状态");
        } catch (Exception ex) {
            summary.recordFailure("FILL", ex.getMessage());
            log.warn("回填回滚失败: fileRecordId={}, error={}",
                    taskData.getFileRecord().getId(), ex.getMessage(), ex);
        }
        return summary;
    }

    public ParseRollbackSummary rollbackValidate(TaskData taskData) {
        ParseRollbackSummary summary = new ParseRollbackSummary();
        if (taskData == null || taskData.getFileRecord() == null) {
            return summary;
        }
        if (!ParseJobStageHelper.hasValidateStageStarted(taskData.getParseJob(), taskData)) {
            summary.recordSuccess("VALIDATE: 未进入校验阶段，跳过");
            return summary;
        }
        try {
            revertValidateBusinessState(taskData.getFileRecord());
            summary.recordSuccess("VALIDATE: 已恢复校验写入");
        } catch (Exception ex) {
            summary.recordFailure("VALIDATE", ex.getMessage());
            log.warn("校验回滚失败: fileRecordId={}, error={}",
                    taskData.getFileRecord().getId(), ex.getMessage(), ex);
        }
        return summary;
    }

    public void revertFillBusinessState(FileRecord fileRecord, TaskData taskData) {
        revertFillBusinessStateForContext(fileRecord, taskData);
    }

    private void revertFillBusinessStateForContext(FileRecord fileRecord, TaskData taskData) {
        FileContextType contextType = fileRecord.getFileContextType();
        if (contextType == null) {
            return;
        }
        Long fileRecordId = fileRecord.getId();
        switch (contextType) {
            case CONTRACT -> revertContractFill(fileRecordId, taskData);
            case SURVEY_REPORT -> revertSurveyReportFill(fileRecordId, taskData);
            case PLANNING_REVIEW -> revertPlanningReviewFill(fileRecordId, fileRecord.getProjectId(), taskData);
            case CAPACITY_INDICATOR -> revertCapacityIndicatorFill(fileRecordId, fileRecord.getProjectId(), taskData);
            case PROJECT_PARTY_SURVEY_SUMMARY -> revertProjectPartySummaryFill(fileRecordId, fileRecord.getProjectId(),
                    taskData);
            case DATA_FILE, OTHER -> {
            }
            default -> {
            }
        }
    }

    private void revertContractFill(Long fileRecordId, TaskData taskData) {
        Query query = new Query(Criteria.where("file_record_id").is(fileRecordId));
        if (taskData != null && taskData.getPreFillContractSnapshot() != null) {
            ContractInfo snapshot = taskData.getPreFillContractSnapshot();
            snapshot.setUpdateTime(LocalDateTime.now());
            mongoTemplate.save(snapshot);
            if (snapshot.getProjectId() != null && snapshot.getId() != null) {
                publish(ProjectDataChangedEvent.contractChanged(snapshot.getProjectId(), snapshot.getId()));
            }
            return;
        }
        if (taskData != null && taskData.isFillCreatedNewContract()) {
            List<ContractInfo> removed = mongoTemplate.find(query, ContractInfo.class);
            mongoTemplate.remove(query, ContractInfo.class);
            for (ContractInfo c : removed) {
                if (c.getProjectId() != null && c.getId() != null) {
                    publish(ProjectDataChangedEvent.contractChanged(c.getProjectId(), c.getId()));
                }
            }
            return;
        }
        log.warn("合同回填回滚跳过（无快照且非本次新建，避免离线误删）: fileRecordId={}", fileRecordId);
    }

    private void revertSurveyReportFill(Long fileRecordId, TaskData taskData) {
        Query query = new Query(Criteria.where("file_record_id").is(fileRecordId));
        if (taskData != null && taskData.getPreFillSurveySnapshot() != null) {
            SurveyReportInfo snapshot = taskData.getPreFillSurveySnapshot();
            snapshot.setUpdateTime(LocalDateTime.now());
            mongoTemplate.save(snapshot);
            if (taskData.getPreFillRoomsSnapshot() != null) {
                mongoTemplate.remove(query, RoomInfo.class);
                List<RoomInfo> roomSnapshots = taskData.getPreFillRoomsSnapshot();
                if (!roomSnapshots.isEmpty()) {
                    List<RoomInfo> copies = copyRoomSnapshots(roomSnapshots);
                    LocalDateTime now = LocalDateTime.now();
                    for (RoomInfo room : copies) {
                        room.setUpdateTime(now);
                    }
                    mongoTemplate.insertAll(copies);
                }
            }
            if (snapshot.getProjectId() != null && snapshot.getId() != null) {
                publish(ProjectDataChangedEvent.surveyReportChanged(snapshot.getProjectId(), snapshot.getId(), true));
            }
            return;
        }
        if (taskData != null && taskData.isFillCreatedNewSurvey()) {
            List<SurveyReportInfo> removed = mongoTemplate.find(query, SurveyReportInfo.class);
            mongoTemplate.remove(query, RoomInfo.class);
            mongoTemplate.remove(query, SurveyReportInfo.class);
            for (SurveyReportInfo sr : removed) {
                if (sr.getProjectId() != null && sr.getId() != null) {
                    publish(ProjectDataChangedEvent.surveyReportChanged(sr.getProjectId(), sr.getId(), true));
                }
            }
            return;
        }
        log.warn("实测回填回滚跳过（无快照且非本次新建，避免离线误删户室）: fileRecordId={}", fileRecordId);
    }

    private void revertPlanningReviewFill(Long fileRecordId, Long projectId, TaskData taskData) {
        Query query = new Query(Criteria.where("file_record_id").is(fileRecordId));
        Query rowQuery = new Query(Criteria.where("file_record_id").is(fileRecordId));

        if (taskData != null && taskData.getPreFillPlanningFormSnapshot() != null) {
            PlanningReviewForm snapshot = taskData.getPreFillPlanningFormSnapshot();
            snapshot.setUpdateTime(LocalDateTime.now());
            mongoTemplate.save(snapshot);
            mongoTemplate.remove(rowQuery, PlanningReviewRow.class);
            List<PlanningReviewRow> rowSnapshots = taskData.getPreFillPlanningRowsSnapshot();
            if (rowSnapshots != null && !rowSnapshots.isEmpty()) {
                List<PlanningReviewRow> copies = copyPlanningRowSnapshots(rowSnapshots);
                LocalDateTime now = LocalDateTime.now();
                for (PlanningReviewRow row : copies) {
                    row.setUpdateTime(now);
                }
                mongoTemplate.insertAll(copies);
            }
            if (projectId != null) {
                publish(ProjectDataChangedEvent.planningReviewChanged(projectId));
            }
            return;
        }
        if (taskData != null && taskData.isFillCreatedNewPlanningForm()) {
            mongoTemplate.remove(rowQuery, PlanningReviewRow.class);
            mongoTemplate.remove(query, PlanningReviewForm.class);
            if (projectId != null) {
                publish(ProjectDataChangedEvent.planningReviewChanged(projectId));
            }
            return;
        }
        log.warn("规划复核回填回滚跳过（无快照且非本次新建）: fileRecordId={}", fileRecordId);
    }

    private void revertCapacityIndicatorFill(Long fileRecordId, Long projectId, TaskData taskData) {
        Query query = new Query(Criteria.where("file_record_id").is(fileRecordId));

        if (taskData != null && taskData.getPreFillCapacitySnapshot() != null) {
            CapacityIndicatorInfo snapshot = taskData.getPreFillCapacitySnapshot();
            snapshot.setUpdateTime(LocalDateTime.now());
            mongoTemplate.save(snapshot);
            if (projectId != null) {
                publish(ProjectDataChangedEvent.capacityIndicatorChanged(projectId));
            }
            return;
        }
        if (taskData != null && taskData.isFillCreatedNewCapacity()) {
            mongoTemplate.remove(query, CapacityIndicatorInfo.class);
            if (projectId != null) {
                publish(ProjectDataChangedEvent.capacityIndicatorChanged(projectId));
            }
            return;
        }
        log.warn("容量指标回填回滚跳过（无快照且非本次新建）: fileRecordId={}", fileRecordId);
    }

    private void revertProjectPartySummaryFill(Long fileRecordId, Long projectId, TaskData taskData) {
        Query query = new Query(Criteria.where("file_record_id").is(fileRecordId));

        if (taskData != null && taskData.getPreFillPartySummarySnapshot() != null) {
            ProjectPartySurveySummaryForm snapshot = taskData.getPreFillPartySummarySnapshot();
            snapshot.setUpdateTime(LocalDateTime.now());
            mongoTemplate.save(snapshot);
            if (snapshot.getProjectId() != null && snapshot.getId() != null) {
                publish(ProjectDataChangedEvent.projectPartySummaryChanged(snapshot.getProjectId(), snapshot.getId()));
            } else if (projectId != null) {
                publish(ProjectDataChangedEvent.projectPartySummaryChanged(projectId, null));
            }
            return;
        }
        if (taskData != null && taskData.isFillCreatedNewPartySummary()) {
            mongoTemplate.remove(query, ProjectPartySurveySummaryForm.class);
            if (projectId != null) {
                publish(ProjectDataChangedEvent.projectPartySummaryChanged(projectId, null));
            }
            return;
        }
        log.warn("项目方汇总回填回滚跳过（无快照且非本次新建）: fileRecordId={}", fileRecordId);
    }

    public void revertValidateBusinessState(FileRecord fileRecord) {
        if (fileRecord == null || fileRecord.getId() == null) {
            return;
        }
        Long fileRecordId = fileRecord.getId();
        unknownUsageRecordService.deleteByFileRecordId(fileRecordId);

        Query surveyQuery = new Query(Criteria.where("file_record_id").is(fileRecordId));
        Update surveyUpdate = SurveyReportFieldUpdates.reparseReset();
        mongoTemplate.updateFirst(surveyQuery, surveyUpdate, SurveyReportInfo.class);

        LocalDateTime now = LocalDateTime.now();
        Query roomQuery = new Query(Criteria.where("file_record_id").is(fileRecordId));
        Update roomUpdate = new Update()
                .unset("is_calculate")
                .unset("usage_category")
                .unset("floor_area_type")
                .set("update_time", now);
        mongoTemplate.updateMulti(roomQuery, roomUpdate, RoomInfo.class);

        SurveyReportInfo sr = mongoTemplate.findOne(surveyQuery, SurveyReportInfo.class);
        if (sr != null && sr.getProjectId() != null && sr.getId() != null) {
            publish(ProjectDataChangedEvent.surveyReportChanged(sr.getProjectId(), sr.getId(), true));
        }
    }

    public void clearIntermediateReferences(ParseJob parseJob, FileRecord fileRecord, TaskData taskData,
            ParseRollbackSummary summary) {
        if (parseJob == null || parseJob.getId() == null) {
            return;
        }
        try {
            Update jobUpdate = new Update()
                    .unset("preprocess_gridfs_id")
                    .unset("ocr_result_path")
                    .unset("llm_result_path")
                    .set("update_time", LocalDateTime.now());
            mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(parseJob.getId())), jobUpdate, ParseJob.class);
            parseJob.setPreprocessGridfsId(null);
            parseJob.setOcrResultPath(null);
            parseJob.setLlmResultPath(null);

            if (fileRecord != null && fileRecord.getId() != null) {
                fileRecord.setPreprocessGridfsId(null);
                if (taskData != null) {
                    taskData.setPreprocessGridfsId(null);
                }
                mongoTemplate.save(fileRecord);
            }
            if (summary != null) {
                summary.recordSuccess("REFS: 已清理 ParseJob/FileRecord 中间引用");
            }
        } catch (Exception ex) {
            if (summary != null) {
                summary.recordFailure("REFS", ex.getMessage());
            }
            log.warn("清理中间引用失败: parseJobId={}, error={}", parseJob.getId(), ex.getMessage(), ex);
        }
    }

    public List<ParseJob> findHangingParseJobs() {
        Query query = new Query(Criteria.where("job_status").in(
                ParseJobStateEnum.PENDING.getCode(),
                ParseJobStateEnum.RUNNING.getCode()));
        return mongoTemplate.find(query, ParseJob.class);
    }

    private void deleteHeaderGridFsRefs(ParsedDataHeader header, ParseRollbackSummary summary) {
        if (header == null) {
            return;
        }
        safeDeleteGridFs(header.getLlmRawDataPath(), summary, "HEADER_LLM");
        safeDeleteGridFs(header.getPreprocessGridfsId(), summary, "HEADER_PREPROCESS");
        safeDeleteGridFs(header.getOcrRawDataPath(), summary, "HEADER_OCR");
        safeDeleteGridFs(header.getMarkdownDataPath(), summary, "HEADER_MD");
    }

    private void safeDeleteGridFs(String ref, ParseRollbackSummary summary, String label) {
        if (!StringUtils.hasText(ref)) {
            return;
        }
        try {
            gridFSUtils.deleteById(ref.trim());
        } catch (Exception ex) {
            if (summary != null) {
                summary.recordFailure(label, ex.getMessage());
            }
            log.warn("删除 GridFS 失败: ref={}, error={}", ref, ex.getMessage());
        }
    }

    private void publish(ProjectDataChangedEvent event) {
        if (applicationEventPublisher == null || event == null || !event.hasAnyId()) {
            return;
        }
        try {
            applicationEventPublisher.publishEvent(event);
        } catch (Exception ex) {
            log.warn("Publish ProjectDataChangedEvent failed, event={}", event, ex);
        }
    }

    public static ContractInfo copyContractSnapshot(ContractInfo source) {
        if (source == null) {
            return null;
        }
        ContractInfo copy = new ContractInfo();
        copy.setId(source.getId());
        copy.setProjectId(source.getProjectId());
        copy.setFileRecordId(source.getFileRecordId());
        copy.setContractNumber(source.getContractNumber());
        copy.setTransferor(source.getTransferor());
        copy.setTransferee(source.getTransferee());
        copy.setTotalArea(source.getTotalArea());
        copy.setResidentialArea(source.getResidentialArea());
        copy.setCommercialArea(source.getCommercialArea());
        copy.setRemark(source.getRemark());
        copy.setCreateTime(source.getCreateTime());
        copy.setUpdateTime(source.getUpdateTime());
        return copy;
    }

    public static SurveyReportInfo copySurveyReportSnapshot(SurveyReportInfo source) {
        if (source == null) {
            return null;
        }
        SurveyReportInfo copy = new SurveyReportInfo();
        copy.setId(source.getId());
        copy.setProjectId(source.getProjectId());
        copy.setFileRecordId(source.getFileRecordId());
        copy.setBuildingName(source.getBuildingName());
        copy.setPhase(source.getPhase());
        copy.setPropertyCertificateNumber(source.getPropertyCertificateNumber());
        copy.setRealEstateSurveyReportNumber(source.getRealEstateSurveyReportNumber());
        copy.setPropertyAreaConfirmationNoticeNumber(source.getPropertyAreaConfirmationNoticeNumber());
        copy.setContractApprovalNumber(source.getContractApprovalNumber());
        copy.setActualTotalBuildingArea(source.getActualTotalBuildingArea());
        copy.setActualResidentialArea(source.getActualResidentialArea());
        copy.setActualCommercialArea(source.getActualCommercialArea());
        copy.setActualManagementRoomArea(source.getActualManagementRoomArea());
        copy.setActualOtherBuildableArea(source.getActualOtherBuildableArea());
        copy.setActualCommunityArea(source.getActualCommunityArea());
        copy.setActualOtherPublicArea(source.getActualOtherPublicArea());
        copy.setTotalBuildableArea(source.getTotalBuildableArea());
        copy.setTotalNonBuildableArea(source.getTotalNonBuildableArea());
        copy.setPendingConfirmArea(source.getPendingConfirmArea());
        copy.setHasUnknownUsage(source.getHasUnknownUsage());
        copy.setUnknownUsages(source.getUnknownUsages());
        copy.setUnknownUsageCount(source.getUnknownUsageCount());
        copy.setRoomInfoBuildingAreaSum(source.getRoomInfoBuildingAreaSum());
        copy.setRoomInfoInnerAreaSum(source.getRoomInfoInnerAreaSum());
        copy.setRoomInfoBalconyAreaSum(source.getRoomInfoBalconyAreaSum());
        copy.setRoomInfoSharedAreaSum(source.getRoomInfoSharedAreaSum());
        copy.setRoomInfoBuildingAreaSumFromOcr(source.getRoomInfoBuildingAreaSumFromOcr());
        copy.setRoomInfoInnerAreaSumFromOcr(source.getRoomInfoInnerAreaSumFromOcr());
        copy.setRoomInfoBalconyAreaSumFromOcr(source.getRoomInfoBalconyAreaSumFromOcr());
        copy.setRoomInfoSharedAreaSumFromOcr(source.getRoomInfoSharedAreaSumFromOcr());
        copy.setIsVerified(source.getIsVerified());
        copy.setVerificationErrorReason(source.getVerificationErrorReason());
        copy.setIsParsed(source.getIsParsed());
        copy.setRemark(source.getRemark());
        copy.setCreateTime(source.getCreateTime());
        copy.setUpdateTime(source.getUpdateTime());
        copy.setIsDeleted(source.getIsDeleted());
        return copy;
    }

    public static List<RoomInfo> copyRoomSnapshots(List<RoomInfo> source) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        List<RoomInfo> copies = new ArrayList<>(source.size());
        for (RoomInfo room : source) {
            if (room == null) {
                continue;
            }
            RoomInfo copy = new RoomInfo();
            copy.setId(room.getId());
            copy.setProjectId(room.getProjectId());
            copy.setFileRecordId(room.getFileRecordId());
            copy.setSurveyReportInfoId(room.getSurveyReportInfoId());
            copy.setRoomLevel(room.getRoomLevel());
            copy.setRoomNumber(room.getRoomNumber());
            copy.setBuildingArea(room.getBuildingArea());
            copy.setInnerArea(room.getInnerArea());
            copy.setBalconyArea(room.getBalconyArea());
            copy.setSharedArea(room.getSharedArea());
            copy.setRoomStructure(room.getRoomStructure());
            copy.setRoomUsage(room.getRoomUsage());
            copy.setRemark(room.getRemark());
            copy.setIsCalculate(room.getIsCalculate());
            copy.setUsageCategory(room.getUsageCategory());
            copy.setFloorAreaType(room.getFloorAreaType());
            copy.setCreateTime(room.getCreateTime());
            copy.setUpdateTime(room.getUpdateTime());
            copy.setIsDeleted(room.getIsDeleted());
            copies.add(copy);
        }
        return copies;
    }

    public static PlanningReviewForm copyPlanningFormSnapshot(PlanningReviewForm source) {
        if (source == null) {
            return null;
        }
        PlanningReviewForm copy = new PlanningReviewForm();
        copy.setId(source.getId());
        copy.setProjectId(source.getProjectId());
        copy.setFileRecordId(source.getFileRecordId());
        copy.setIsParsed(source.getIsParsed());
        copy.setProjectName(source.getProjectName());
        copy.setConstructionUnit(source.getConstructionUnit());
        copy.setDesignUnit(source.getDesignUnit());
        copy.setConstructionLocation(source.getConstructionLocation());
        copy.setLandUseNature(source.getLandUseNature());
        copy.setContactPerson(source.getContactPerson());
        copy.setContactPhone(source.getContactPhone());
        copy.setRemarks(source.getRemarks());
        copy.setCreateTime(source.getCreateTime());
        copy.setUpdateTime(source.getUpdateTime());
        return copy;
    }

    public static List<PlanningReviewRow> copyPlanningRowSnapshots(List<PlanningReviewRow> source) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        List<PlanningReviewRow> copies = new ArrayList<>(source.size());
        for (PlanningReviewRow row : source) {
            if (row == null) {
                continue;
            }
            PlanningReviewRow copy = new PlanningReviewRow();
            copy.setId(row.getId());
            copy.setProjectId(row.getProjectId());
            copy.setFileRecordId(row.getFileRecordId());
            copy.setPlanningReviewFormId(row.getPlanningReviewFormId());
            copy.setRowIndex(row.getRowIndex());
            copy.setEngineeringProject(row.getEngineeringProject());
            copy.setBuildingNatureRaw(row.getBuildingNatureRaw());
            copy.setAreaCategory(row.getAreaCategory());
            copy.setConstructionNature(row.getConstructionNature());
            copy.setBuildingCount(row.getBuildingCount());
            copy.setAboveGroundFloors(row.getAboveGroundFloors());
            copy.setBelowGroundFloors(row.getBelowGroundFloors());
            copy.setHeightM(row.getHeightM());
            copy.setBaseAreaM2(row.getBaseAreaM2());
            copy.setResidentialResidentialArea(row.getResidentialResidentialArea());
            copy.setResidentialHotelApartmentArea(row.getResidentialHotelApartmentArea());
            copy.setResidentialOtherArea(row.getResidentialOtherArea());
            copy.setNrAboveCommercial(row.getNrAboveCommercial());
            copy.setNrAboveGarage(row.getNrAboveGarage());
            copy.setNrAboveOther(row.getNrAboveOther());
            copy.setNrBelowCommercial(row.getNrBelowCommercial());
            copy.setNrBelowSupporting(row.getNrBelowSupporting());
            copy.setNrBelowOther(row.getNrBelowOther());
            copy.setAboveGroundArea(row.getAboveGroundArea());
            copy.setBelowGroundArea(row.getBelowGroundArea());
            copy.setTotalArea(row.getTotalArea());
            copy.setFarAboveGround(row.getFarAboveGround());
            copy.setFarBelowGround(row.getFarBelowGround());
            copy.setCreateTime(row.getCreateTime());
            copy.setUpdateTime(row.getUpdateTime());
            copies.add(copy);
        }
        return copies;
    }

    public static CapacityIndicatorInfo copyCapacitySnapshot(CapacityIndicatorInfo source) {
        if (source == null) {
            return null;
        }
        CapacityIndicatorInfo copy = new CapacityIndicatorInfo();
        copy.setId(source.getId());
        copy.setProjectId(source.getProjectId());
        copy.setFileRecordId(source.getFileRecordId());
        copy.setIsParsed(source.getIsParsed());
        copy.setTotalArea(source.getTotalArea());
        copy.setCommercialArea(source.getCommercialArea());
        copy.setResidentialArea(source.getResidentialArea());
        copy.setCreateTime(source.getCreateTime());
        copy.setUpdateTime(source.getUpdateTime());
        return copy;
    }

    public static ProjectPartySurveySummaryForm copyPartySummarySnapshot(ProjectPartySurveySummaryForm source) {
        if (source == null) {
            return null;
        }
        ProjectPartySurveySummaryForm copy = new ProjectPartySurveySummaryForm();
        copy.setId(source.getId());
        copy.setProjectId(source.getProjectId());
        copy.setFileRecordId(source.getFileRecordId());
        copy.setIsParsed(source.getIsParsed());
        copy.setDeclaredTotals(copyDeclaredTotals(source.getDeclaredTotals()));
        copy.setParseStatus(source.getParseStatus());
        copy.setRemark(source.getRemark());
        copy.setCreateTime(source.getCreateTime());
        copy.setUpdateTime(source.getUpdateTime());
        return copy;
    }

    private static ProjectPartyDeclaredTotals copyDeclaredTotals(ProjectPartyDeclaredTotals source) {
        if (source == null) {
            return null;
        }
        ProjectPartyDeclaredTotals copy = new ProjectPartyDeclaredTotals();
        copy.setContractAgreedTotalBuildingArea(source.getContractAgreedTotalBuildingArea());
        copy.setBuildableTotalBuildingArea(source.getBuildableTotalBuildingArea());
        copy.setDifferenceTotalBuildingArea(source.getDifferenceTotalBuildingArea());
        copy.setContractAgreedCommercialArea(source.getContractAgreedCommercialArea());
        copy.setBuildableCommercialArea(source.getBuildableCommercialArea());
        copy.setDifferenceCommercialArea(source.getDifferenceCommercialArea());
        copy.setContractAgreedResidentialArea(source.getContractAgreedResidentialArea());
        copy.setBuildableResidentialArea(source.getBuildableResidentialArea());
        copy.setDifferenceResidentialArea(source.getDifferenceResidentialArea());
        return copy;
    }

    private void publishFillRestoredEvents(FileRecord fileRecord, TaskData taskData) {
        if (fileRecord == null || fileRecord.getFileContextType() == null) {
            return;
        }
        Long projectId = fileRecord.getProjectId();
        switch (fileRecord.getFileContextType()) {
            case CONTRACT -> {
                ContractInfo snapshot = taskData != null ? taskData.getPreFillContractSnapshot() : null;
                if (snapshot != null && snapshot.getProjectId() != null && snapshot.getId() != null) {
                    publish(ProjectDataChangedEvent.contractChanged(snapshot.getProjectId(), snapshot.getId()));
                } else if (projectId != null) {
                    publish(ProjectDataChangedEvent.contractChanged(projectId, null));
                }
            }
            case SURVEY_REPORT -> {
                SurveyReportInfo snapshot = taskData != null ? taskData.getPreFillSurveySnapshot() : null;
                if (snapshot != null && snapshot.getProjectId() != null && snapshot.getId() != null) {
                    publish(ProjectDataChangedEvent.surveyReportChanged(snapshot.getProjectId(), snapshot.getId(),
                            true));
                } else if (projectId != null) {
                    publish(ProjectDataChangedEvent.surveyReportChanged(projectId, null, true));
                }
            }
            case PLANNING_REVIEW -> {
                if (projectId != null) {
                    publish(ProjectDataChangedEvent.planningReviewChanged(projectId));
                }
            }
            case CAPACITY_INDICATOR -> {
                if (projectId != null) {
                    publish(ProjectDataChangedEvent.capacityIndicatorChanged(projectId));
                }
            }
            case PROJECT_PARTY_SURVEY_SUMMARY -> {
                ProjectPartySurveySummaryForm snapshot = taskData != null ? taskData.getPreFillPartySummarySnapshot()
                        : null;
                if (snapshot != null && snapshot.getProjectId() != null && snapshot.getId() != null) {
                    publish(ProjectDataChangedEvent.projectPartySummaryChanged(snapshot.getProjectId(), snapshot.getId()));
                } else if (projectId != null) {
                    publish(ProjectDataChangedEvent.projectPartySummaryChanged(projectId, null));
                }
            }
            default -> {
            }
        }
    }
}
