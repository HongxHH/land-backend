package com.gov.landcheck.file.service.parse;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.ParseJob;
import com.gov.landcheck.core.bo.entity.PlanningReviewForm;
import com.gov.landcheck.core.bo.entity.CapacityIndicatorInfo;
import com.gov.landcheck.core.bo.entity.ProjectPartySurveySummaryForm;
import com.gov.landcheck.core.bo.entity.RoomInfo;
import com.gov.landcheck.core.bo.entity.SurveyReportInfo;
import com.gov.landcheck.core.config.cache.event.ProjectDataChangedEvent;
import com.gov.landcheck.core.common.MessageConstant;
import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.core.enums.FileStateEnum;
import com.gov.landcheck.core.enums.ParseJobStateEnum;
import com.gov.landcheck.core.service.UnknownUsageRecordService;
import com.gov.landcheck.file.dto.SubmitParseResult;
import com.gov.landcheck.file.service.ITaskExecuteService;
import com.gov.landcheck.file.service.InvalidGridFsFileCleanup;
import com.gov.landcheck.file.utils.GridFSUtils;
import com.mongodb.client.result.UpdateResult;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * 解析提交编排：资格校验、原子抢占 PENDING、提交异步任务及 GridFS 缺失时的级联清理。
 * <p>
 * 从 {@code FileServiceImpl} 拆分，减轻上帝类体积并保持
 * {@link com.gov.landcheck.file.service.FileService} 接口不变。
 */
@Slf4j
@Service
public class FileParseSubmissionService {

    @Resource
    private MongoTemplate mongoTemplate;
    @Resource
    private ApplicationEventPublisher applicationEventPublisher;
    @Resource
    private GridFSUtils gridFSUtils;
    @Resource
    private ITaskExecuteService taskExecuteService;
    @Lazy
    @Resource
    private InvalidGridFsFileCleanup invalidGridFsFileCleanup;
    @Resource
    private UnknownUsageRecordService unknownUsageRecordService;

    /**
     * 按文件记录 ID 查询最新一条解析任务（按创建时间降序）。
     */
    public ParseJob findLatestParseJobByFileRecordId(Long fileRecordId) {
        if (fileRecordId == null) {
            return null;
        }
        Query query = new Query(Criteria.where("file_record_id").is(fileRecordId))
                .with(Sort.by(Sort.Direction.DESC, "createTime"))
                .limit(1);
        return mongoTemplate.findOne(query, ParseJob.class);
    }

    public SubmitParseResult submitParseIfEligible(FileRecord fileRecord) {
        FileStateEnum rollbackState = null;
        Long fileRecordId = null;
        boolean acquired = false;
        try {
            if (fileRecord == null) {
                return SubmitParseResult.fail(String.valueOf(MessageConstant.PARAMS_ERROR_CODE), "文件不存在");
            }
            fileRecordId = fileRecord.getId();
            if (fileRecord.getFileContextType() != FileContextType.CONTRACT
                    && fileRecord.getFileContextType() != FileContextType.SURVEY_REPORT
                    && fileRecord.getFileContextType() != FileContextType.PLANNING_REVIEW
                    && fileRecord.getFileContextType() != FileContextType.CAPACITY_INDICATOR
                    && fileRecord.getFileContextType() != FileContextType.PROJECT_PARTY_SURVEY_SUMMARY) {
                return SubmitParseResult.fail(String.valueOf(MessageConstant.PARAMS_ERROR_CODE), "文件类型不支持解析");
            }
            if (!fileRecord.getFileContextType().isSupportedFileType(fileRecord.getFileType())) {
                markUnparseableDueToFormatMismatch(fileRecord);
                String hint = fileRecord.getFileContextType().getSupportedFormatHint();
                String message = hint.isEmpty()
                        ? "文件格式与归档类型不匹配，请重新上传正确格式"
                        : fileRecord.getFileContextType().getName() + hint + "，请重新上传";
                return SubmitParseResult.fail(String.valueOf(MessageConstant.PARAMS_ERROR_CODE), message);
            }
            if (FileStateEnum.UPLOADING.equals(fileRecord.getFileState())
                    || FileStateEnum.WAITING_POST_PROCESS.equals(fileRecord.getFileState())) {
                return SubmitParseResult.fail(String.valueOf(MessageConstant.PARAMS_ERROR_CODE), "文件正在上传或后处理中，请稍后再试");
            }
            if (!FileStateEnum.WAITING_PARSE.equals(fileRecord.getFileState())
                    && !FileStateEnum.PARSE_COMPLETE.equals(fileRecord.getFileState())
                    && !FileStateEnum.PARSE_FAIL.equals(fileRecord.getFileState())) {
                return SubmitParseResult.fail(String.valueOf(MessageConstant.PARAMS_ERROR_CODE), "文件状态不允许解析");
            }
            if (FileStateEnum.PARSING.equals(fileRecord.getFileState())) {
                return SubmitParseResult.fail(String.valueOf(MessageConstant.PARAMS_ERROR_CODE), "文件正在解析中，请稍后再试");
            }
            if (FileStateEnum.UPLOAD_FAIL.equals(fileRecord.getFileState())) {
                return SubmitParseResult.fail(String.valueOf(MessageConstant.PARAMS_ERROR_CODE),
                        "文件上传失败，请重新将文件删除后重新上传后再试");
            }

            ParseJob existingJob = findLatestParseJobByFileRecordId(fileRecord.getId());
            if (existingJob != null
                    && (ParseJobStateEnum.PENDING.equals(existingJob.getJobStatus())
                            || ParseJobStateEnum.RUNNING.equals(existingJob.getJobStatus()))) {
                return SubmitParseResult.fail(String.valueOf(MessageConstant.PARAMS_ERROR_CODE), "文件解析任务已在进行中");
            }
            if (!gridFSUtils.exists(fileRecord.getGridfsId())) {
                log.debug("GridFS文件不存在，开始清理无效文件记录: fileId={}, gridfsId={}", fileRecordId, fileRecord.getGridfsId());
                invalidGridFsFileCleanup.cleanupFileRecordWhenGridFsMissing(fileRecord);
                log.debug("无效文件记录清理完成: fileId={}, gridfsId={}", fileRecordId, fileRecord.getGridfsId());
                return SubmitParseResult.fail(String.valueOf(MessageConstant.PARAMS_ERROR_CODE), "该文件已不存在，请重新上传后再试");
            }

            resetBusinessStateBeforeParse(fileRecord);

            rollbackState = fileRecord.getFileState();
            acquired = false;
            if (fileRecordId != null) {
                Query query = new Query(Criteria.where("_id").is(fileRecordId)
                        .and("file_state")
                        .in(FileStateEnum.WAITING_PARSE, FileStateEnum.PARSE_COMPLETE, FileStateEnum.PARSE_FAIL));
                Update update = new Update()
                        .set("file_state", FileStateEnum.PENDING)
                        .set("update_time", java.time.LocalDateTime.now());
                UpdateResult result = mongoTemplate.updateFirst(query, update, FileRecord.class);
                acquired = result.getModifiedCount() > 0;
            }
            if (!acquired) {
                return SubmitParseResult.fail(String.valueOf(MessageConstant.PARAMS_ERROR_CODE), "文件解析任务已在进行中");
            }
            fileRecord.setFileState(FileStateEnum.PENDING);

            String taskId = taskExecuteService.executeParseTask(fileRecord, rollbackState);
            return SubmitParseResult.success(taskId);
        } catch (Exception e) {
            log.error("提交解析任务异常: fileId={}, error={}", fileRecord != null ? fileRecord.getId() : null, e.getMessage(),
                    e);
            if (acquired && rollbackState != null && fileRecordId != null) {
                revertPendingParseReservation(fileRecordId, rollbackState);
            }
            return SubmitParseResult.fail(String.valueOf(MessageConstant.PARAMS_ERROR_CODE),
                    "解析文件失败: " + e.getMessage());
        }
    }

    private void markUnparseableDueToFormatMismatch(FileRecord fileRecord) {
        if (fileRecord == null || fileRecord.getId() == null) {
            return;
        }
        FileContextType contextType = fileRecord.getFileContextType();
        String hint = contextType != null ? contextType.getSupportedFormatHint() : "";
        String parseMessage;
        if (hint.isEmpty()) {
            parseMessage = "文件格式与归档类型不匹配";
        } else {
            parseMessage = contextType.getName() + hint;
        }
        try {
            Query query = new Query(Criteria.where("_id").is(fileRecord.getId()));
            Update update = new Update()
                    .set("file_state", FileStateEnum.UNPARSEABLE)
                    .set("parse_message", parseMessage)
                    .set("update_time", java.time.LocalDateTime.now());
            mongoTemplate.updateFirst(query, update, FileRecord.class);
            fileRecord.setFileState(FileStateEnum.UNPARSEABLE);
            fileRecord.setParseMessage(parseMessage);
        } catch (Exception ex) {
            log.warn("标记不可解析状态失败: fileRecordId={}, error={}", fileRecord.getId(), ex.getMessage(), ex);
        }
    }

    private void revertPendingParseReservation(Long fileRecordId, FileStateEnum rollbackState) {
        try {
            Query q = new Query(Criteria.where("_id").is(fileRecordId).and("file_state").is(FileStateEnum.PENDING));
            Update u = new Update()
                    .set("file_state", rollbackState)
                    .set("update_time", java.time.LocalDateTime.now())
                    .unset("parse_job_id");
            mongoTemplate.updateFirst(q, u, FileRecord.class);
            log.warn("已回滚解析抢占状态: fileRecordId={}, rollbackState={}", fileRecordId, rollbackState);
        } catch (Exception ex) {
            log.error("回滚解析抢占状态失败: fileRecordId={}, rollbackState={}, error={}",
                    fileRecordId, rollbackState, ex.getMessage(), ex);
        }
    }

    private void resetBusinessStateBeforeParse(FileRecord fileRecord) {
        if (fileRecord == null || fileRecord.getId() == null || fileRecord.getFileContextType() == null) {
            return;
        }
        Long fileRecordId = fileRecord.getId();
        switch (fileRecord.getFileContextType()) {
            case CONTRACT, DATA_FILE, OTHER -> {
            }
            case SURVEY_REPORT -> {
                long unknownDeleted = unknownUsageRecordService.deleteByFileRecordId(fileRecordId);
                if (unknownDeleted > 0) {
                    log.info("重新解析前已清理未知用途记录: fileRecordId={}, deleted={}", fileRecordId, unknownDeleted);
                }
                Query roomQuery = new Query(Criteria.where("file_record_id").is(fileRecordId));
                long roomsDeleted = mongoTemplate.remove(roomQuery, RoomInfo.class).getDeletedCount();
                if (roomsDeleted > 0) {
                    log.info("重新解析前已清理房间数据: fileRecordId={}, deleted={}", fileRecordId, roomsDeleted);
                }
                Query query = new Query(Criteria.where("file_record_id").is(fileRecordId));
                Update update = SurveyReportFieldUpdates.reparseReset();
                mongoTemplate.updateFirst(query, update, SurveyReportInfo.class);
                SurveyReportInfo sr = mongoTemplate.findOne(query, SurveyReportInfo.class);
                if (sr != null && sr.getProjectId() != null && sr.getId() != null) {
                    publishCacheHint(ProjectDataChangedEvent.surveyReportChanged(sr.getProjectId(), sr.getId(), false));
                }
            }
            case PLANNING_REVIEW -> {
                Query query = new Query(Criteria.where("file_record_id").is(fileRecordId));
                Update update = new Update()
                        .set("is_parsed", 0)
                        .set("update_time", java.time.LocalDateTime.now());
                mongoTemplate.updateFirst(query, update, PlanningReviewForm.class);
                Long projectId = fileRecord.getProjectId();
                if (projectId != null) {
                    publishCacheHint(ProjectDataChangedEvent.planningReviewChanged(projectId));
                }
            }
            case CAPACITY_INDICATOR -> {
                Query query = new Query(Criteria.where("file_record_id").is(fileRecordId));
                Update update = new Update()
                        .set("is_parsed", 0)
                        .set("update_time", java.time.LocalDateTime.now());
                mongoTemplate.updateFirst(query, update, CapacityIndicatorInfo.class);
                Long projectId = fileRecord.getProjectId();
                if (projectId != null) {
                    publishCacheHint(ProjectDataChangedEvent.capacityIndicatorChanged(projectId));
                }
            }
            case PROJECT_PARTY_SURVEY_SUMMARY -> {
                Query query = new Query(Criteria.where("file_record_id").is(fileRecordId));
                Update update = new Update()
                        .set("is_parsed", 0)
                        .set("parse_status", "PENDING")
                        .set("remark", null)
                        .set("update_time", java.time.LocalDateTime.now());
                mongoTemplate.updateFirst(query, update, ProjectPartySurveySummaryForm.class);
                ProjectPartySurveySummaryForm form = mongoTemplate.findOne(query, ProjectPartySurveySummaryForm.class);
                if (form != null && form.getProjectId() != null && form.getId() != null) {
                    publishCacheHint(
                            ProjectDataChangedEvent.projectPartySummaryChanged(form.getProjectId(), form.getId()));
                }
            }
            default -> {
            }
        }
    }

    private void publishCacheHint(ProjectDataChangedEvent event) {
        if (applicationEventPublisher == null || event == null || !event.hasAnyId()) {
            return;
        }
        try {
            applicationEventPublisher.publishEvent(event);
        } catch (Exception ex) {
            log.warn("Publish ProjectDataChangedEvent failed (parse reset), event={}", event, ex);
        }
    }
}
