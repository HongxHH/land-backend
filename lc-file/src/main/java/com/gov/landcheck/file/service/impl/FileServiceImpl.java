package com.gov.landcheck.file.service.impl;

import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.mongodb.client.result.UpdateResult;
import com.gov.landcheck.core.audit.AuditDiffHelper;
import com.gov.landcheck.core.audit.AuditFileRecorder;
import com.gov.landcheck.core.audit.AuditOperation;
import com.gov.landcheck.core.audit.OperationAuditService;
import com.gov.landcheck.core.audit.OperationType;
import com.gov.landcheck.core.audit.OperatorContext;
import com.gov.landcheck.core.audit.TargetType;
import com.gov.landcheck.core.bo.R.AjaxJson;
import com.gov.landcheck.core.bo.entity.CapacityIndicatorInfo;
import com.gov.landcheck.core.bo.entity.ContractInfo;
import com.gov.landcheck.core.bo.entity.FileArchive;
import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.OCRExecutionResult;
import com.gov.landcheck.core.bo.entity.ParseJob;
import com.gov.landcheck.core.bo.entity.ParsedDataHeader;
import com.gov.landcheck.core.bo.entity.ParsedDataItem;
import com.gov.landcheck.core.bo.entity.PlanningReviewForm;
import com.gov.landcheck.core.bo.entity.PlanningReviewRow;
import com.gov.landcheck.core.bo.entity.Project;
import com.gov.landcheck.core.bo.entity.ProjectPartySurveySummaryForm;
import com.gov.landcheck.core.bo.entity.RoomInfo;
import com.gov.landcheck.core.bo.entity.SurveyReportInfo;
import com.gov.landcheck.core.bo.entity.UnknownUsageRecord;
import com.gov.landcheck.core.common.MessageConstant;
import com.gov.landcheck.core.config.cache.event.ProjectDataChangedEvent;
import com.gov.landcheck.core.config.query.MongoQueryBuilder;
import com.gov.landcheck.core.config.query.MongoSortFields;
import com.gov.landcheck.core.config.query.SafePageSort;
import com.gov.landcheck.core.audit.FileOperationAuthorization;
import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.core.enums.FileStateEnum;
import com.gov.landcheck.core.enums.FileType;
import com.gov.landcheck.core.enums.ParseJobStateEnum;
import com.gov.landcheck.core.service.IFileArchiveService;
import com.gov.landcheck.core.service.SurveyReportContractApprovalSyncService;
import com.gov.landcheck.file.dto.FileUploadDTO;
import com.gov.landcheck.file.dto.FileQueryDTO;
import com.gov.landcheck.file.dto.FileQueryResultDTO;
import com.gov.landcheck.file.dto.SubmitParseResult;
import com.gov.landcheck.file.service.FileService;
import com.gov.landcheck.core.utils.UploadFileNameSanitizer;
import com.gov.landcheck.file.service.parse.AutoParseSubmissionService;
import com.gov.landcheck.file.service.parse.FileParseSubmissionService;
import com.gov.landcheck.file.service.ITaskExecuteService;
import com.gov.landcheck.file.service.UploadRecordService;
import com.gov.landcheck.file.utils.GridFSUtils;
import com.gov.landcheck.file.vo.FileRecordVO;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * Author: Administrator
 * Date: 2025/12/20
 * Description:
 * :
 */
@Service
@Slf4j
public class FileServiceImpl implements FileService {

    @Resource
    private MongoTemplate mongoTemplate; // 用于操作MongoDB
    @Resource
    private ITaskExecuteService taskExecuteService; // 任务执行服务
    @Resource
    private GridFSUtils gridFSUtils; // GridFS工具类
    @Resource
    private GridFsTemplate gridFsTemplate;
    @Resource
    private IFileArchiveService fileArchiveService;
    @Resource
    private ApplicationEventPublisher applicationEventPublisher;
    @Resource
    private UploadRecordService uploadRecordService;
    @Resource
    private OperationAuditService operationAuditService;
    @Lazy
    @Resource
    private FileParseSubmissionService fileParseSubmissionService;
    @Lazy
    @Resource
    private AutoParseSubmissionService autoParseSubmissionService;
    @Resource
    private SurveyReportContractApprovalSyncService surveyReportContractApprovalSyncService;

    private static final long DELETE_PARSE_CANCEL_WAIT_MS = 15_000L;

    private void publish(ProjectDataChangedEvent event) {
        if (event == null || !event.hasAnyId()) {
            return;
        }
        try {
            applicationEventPublisher.publishEvent(event);
        } catch (Exception ex) {
            log.error("Publish ProjectDataChangedEvent failed, event={}", event, ex);
        }
    }

    /**
     * 查询附件（包含文件内容）
     */
    @Override
    public Optional<FileRecord> getById(String id) {
        FileRecord fileRecord = mongoTemplate.findById(id, FileRecord.class);
        if (fileRecord != null && fileRecord.getGridfsId() != null) { // 文件存在
            return loadFileContent(fileRecord);
        }
        return Optional.empty();
    }

    /**
     * 根据ID获取文件信息（不包含文件内容）
     */
    @Override
    public Optional<FileRecord> getFileInfoById(String id) {
        FileRecord fileRecord = mongoTemplate.findById(id, FileRecord.class);
        if (fileRecord != null) {
            // 不加载文件内容，直接返回文件信息
            fileRecord.setFileContent(null);
            return Optional.of(fileRecord);
        }
        return Optional.empty();
    }

    /**
     * 加载文件内容具体代码
     */
    private Optional<FileRecord> loadFileContent(FileRecord fileRecord) {
        try {
            byte[] fileBytes = gridFSUtils.getFileBytes(fileRecord.getGridfsId());
            if (fileBytes != null && fileBytes.length > 0) { // 文件存在且有内容
                fileRecord.setFileContent(fileBytes); // 设置文件内容
                return Optional.of(fileRecord); // 返回文件
            }
        } catch (Exception ex) {
            log.error("获取文件内容失败: fileRecordId={}, error={}",
                    fileRecord != null ? fileRecord.getId() : null, ex.getMessage(), ex);
        }
        return Optional.empty();
    }

    @Override
    public SubmitParseResult submitParseIfEligible(FileRecord fileRecord) {
        return fileParseSubmissionService.submitParseIfEligible(fileRecord);
    }

    @Override
    public void cleanupFileRecordWhenGridFsMissing(FileRecord fileRecord) {
        if (fileRecord == null || fileRecord.getId() == null) {
            return;
        }
        Long fileRecordId = fileRecord.getId();
        deleteBusinessData(fileRecord);
        deleteParseData(fileRecordId);
        mongoTemplate.remove(fileRecord);
    }

    @Override
    public AjaxJson parseFile(String fileId) {

        FileRecord fileRecord = mongoTemplate.findById(fileId, FileRecord.class);
        if (fileRecord == null) {
            return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "文件不存在");
        }
        SubmitParseResult result = submitParseIfEligible(fileRecord);
        if (result.isSubmitted()) {
            Map<String, String> data = new HashMap<>();
            data.put("taskId", result.getTaskId());
            data.put("fileId", fileId);
            AuditFileRecorder.recordFileOperation(operationAuditService, OperationType.PARSE.name(), fileRecord, data,
                    null, null);
            return AjaxJson.getSuccess("文件解析任务成功提交,请稍后查询解析状态").setData(data);
        }
        return AjaxJson.get(Integer.parseInt(result.getErrorCode()), result.getErrorMessage());

    }

    @Override
    public AjaxJson getParseStatus(String fileId) {
        try {
            // 1. 检查文件是否存在
            FileRecord fileRecord = mongoTemplate.findById(fileId, FileRecord.class);
            if (fileRecord == null) {
                return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "文件不存在");
            }

            // 2. 获取最新的解析任务
            ParseJob parseJob = fileParseSubmissionService.findLatestParseJobByFileRecordId(fileRecord.getId());

            if (parseJob == null) {
                return AjaxJson.getSuccess("文件尚未开始解析").setData(null);
            }

            return AjaxJson.getSuccess("获取解析状态成功").setData(parseJob);

        } catch (Exception e) {
            log.error("获取解析状态失败: fileId={}, error={}", fileId, e.getMessage(), e);
            return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "获取解析状态失败");
        }
    }

    /**
     * 解析状态信息类
     */

    @Override
    public List<FileRecord> getFilesByProjectId(Long projectId) {
        if (projectId == null) {
            return List.of();
        }

        // 查询指定项目的所有文件，按上传时间倒序排列
        Query query = new Query(Criteria.where("project_id").is(projectId))
                .with(Sort.by(Sort.Direction.DESC, "upload_time"));

        return mongoTemplate.find(query, FileRecord.class);
    }

    @Override
    public List<FileRecord> getFilesByProjectIdAndArchiveId(Long projectId, Long archiveId) {
        if (projectId == null) {
            return List.of();
        }
        Criteria criteria = Criteria.where("project_id").is(projectId);
        if (archiveId == null) {
            criteria.and("archive_id").is(null);
        } else {
            criteria.and("archive_id").is(archiveId);
        }
        Query query = new Query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "upload_time"));
        return mongoTemplate.find(query, FileRecord.class);
    }

    @Override
    public AjaxJson cancelParseTask(String fileId, String reason) {
        return cancelParseTask(fileId, reason, true);
    }

    private AjaxJson cancelParseTask(String fileId, String reason, boolean scheduleAutoParseAfterCancel) {
        try {
            // 1. 检查文件是否存在
            FileRecord fileRecord = mongoTemplate.findById(fileId, FileRecord.class);
            if (fileRecord == null) {
                return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "文件不存在");
            }
            if (!FileOperationAuthorization.canMutateFile(fileRecord)) {
                return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE,
                        FileOperationAuthorization.denyReasonForFileMutate());
            }

            // 2. 获取最新的解析任务
            ParseJob parseJob = fileParseSubmissionService.findLatestParseJobByFileRecordId(fileRecord.getId());
            if (parseJob == null) {
                return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "解析任务不存在");
            }

            // 3. 检查解析任务状态：已成功/失败不可取消
            if (ParseJobStateEnum.SUCCESS.equals(parseJob.getJobStatus())
                    || ParseJobStateEnum.FAILED.equals(parseJob.getJobStatus())) {
                log.info("ParseJob id: {} 任务id: {} 任务状态: {}, fileId: {}", parseJob.getId(), parseJob.getTaskId(),
                        parseJob.getJobStatus(), fileId);
                return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "解析任务已结束,无法取消");
            }
            // 已取消且线程池中已无该任务：幂等返回成功
            if (ParseJobStateEnum.CANCELLED.equals(parseJob.getJobStatus())
                    && !taskExecuteService.isTaskRunning(parseJob.getTaskId())) {
                AuditFileRecorder.recordFileOperation(operationAuditService, OperationType.PARSE_CANCEL.name(),
                        fileRecord, Map.of("reason", reason != null ? reason : ""), null, null);
                scheduleAutoParseIfNeeded(fileRecord, scheduleAutoParseAfterCancel);
                return AjaxJson.getSuccess("解析任务已取消");
            }

            // 4. 请求取消任务
            parseJob.requestCancel(reason);
            mongoTemplate.save(parseJob);

            // 5. 任务在线程池中运行时强制取消；否则回滚中间产物
            if (taskExecuteService.isTaskRunning(parseJob.getTaskId())) {
                taskExecuteService.cancelParseTask(parseJob.getTaskId(), reason);
            } else {
                taskExecuteService.rollbackParseJob(parseJob, fileRecord);
                log.info("任务未在线程池运行，已回滚中间数据: taskId={}, fileId={}", parseJob.getTaskId(), fileId);
            }

            // 6. 更新文件状态
            fileRecord.setFileState(FileStateEnum.WAITING_PARSE); // 重置为可解析状态
            fileRecord.setAutoParseQueuedAt(null);
            mongoTemplate.save(fileRecord);

            AuditFileRecorder.recordFileOperation(operationAuditService, OperationType.PARSE_CANCEL.name(), fileRecord,
                    Map.of("reason", reason != null ? reason : "", "taskId", parseJob.getTaskId()), null, null);
            scheduleAutoParseIfNeeded(fileRecord, scheduleAutoParseAfterCancel);
            return AjaxJson.getSuccess("解析任务取消请求已提交");

        } catch (Exception e) {
            log.error("取消解析任务失败: fileId={}, error={}", fileId, e.getMessage(), e);
            return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "取消解析任务失败");
        }
    }

    private void scheduleAutoParseIfNeeded(FileRecord fileRecord, boolean scheduleAutoParseAfterCancel) {
        if (!scheduleAutoParseAfterCancel || fileRecord == null || fileRecord.getId() == null) {
            return;
        }
        if (!FileContextType.isAutoParseContext(fileRecord.getFileContextType())) {
            return;
        }
        try {
            autoParseSubmissionService.submitAfterUpload(fileRecord.getId());
        } catch (Exception ex) {
            log.warn("取消解析后重新提交自动解析失败: fileId={}, error={}", fileRecord.getId(), ex.getMessage());
        }
    }

    @Override
    @AuditOperation(operation = OperationType.DELETE, targetType = TargetType.FILE, idParam = "p0")
    public AjaxJson deleteFile(String fileId) {
        try {
            log.info("开始删除文件: fileId={}", fileId);

            FileRecord fileRecord = mongoTemplate.findById(fileId, FileRecord.class);
            if (fileRecord == null) {
                return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "文件不存在");
            }

            if (!FileOperationAuthorization.canMutateFile(fileRecord)) {
                return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE,
                        FileOperationAuthorization.denyReasonForFileMutate());
            }

            if (FileStateEnum.isBlockedForDelete(fileRecord.getFileState())) {
                return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE,
                        FileStateEnum.blockedDeleteReason(fileRecord.getFileState()));
            }

            AjaxJson waitResult = awaitParseCancelBeforeDelete(fileId, fileRecord);
            if (waitResult != null) {
                return waitResult;
            }

            fileRecord = mongoTemplate.findById(fileId, FileRecord.class);
            if (fileRecord == null) {
                return AjaxJson.getSuccess("文件删除成功");
            }

            return performFileDeletion(fileRecord);

        } catch (Exception e) {
            log.error("删除文件失败: fileId={}, error={}", fileId, e.getMessage(), e);
            return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "删除文件失败");
        }
    }

    private AjaxJson awaitParseCancelBeforeDelete(String fileId, FileRecord fileRecord) {
        if (!FileStateEnum.isParseActive(fileRecord.getFileState())) {
            return null;
        }
        ParseJob parseJob = fileParseSubmissionService.findLatestParseJobByFileRecordId(fileRecord.getId());
        if (parseJob == null) {
            return null;
        }
        if (!ParseJobStateEnum.PENDING.equals(parseJob.getJobStatus())
                && !ParseJobStateEnum.RUNNING.equals(parseJob.getJobStatus())) {
            return null;
        }

        log.info("文件正在解析中，取消并等待空闲后删除: taskId={}, fileId={}", parseJob.getTaskId(), fileId);
        AjaxJson cancelResult = cancelParseTask(fileId, "文件删除操作-取消解析", false);
        if (cancelResult.getCode() != AjaxJson.CODE_SUCCESS) {
            log.warn("取消解析任务失败: fileId={}, msg={}", fileId, cancelResult.getMsg());
            return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "取消解析任务失败: " + cancelResult.getMsg());
        }

        boolean idle = taskExecuteService.awaitTaskIdle(parseJob.getTaskId(), DELETE_PARSE_CANCEL_WAIT_MS);
        if (!idle) {
            return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "解析任务仍在执行，请稍后再试删除");
        }
        return null;
    }

    private AjaxJson performFileDeletion(FileRecord fileRecord) {
        Long fileRecordId = fileRecord.getId();
        log.info("文件存在，开始清理相关数据: fileRecordId={}", fileRecordId);

        deleteBusinessData(fileRecord);
        deleteParseData(fileRecordId);
        deleteGridFSFiles(fileRecord);
        mongoTemplate.remove(fileRecord);
        uploadRecordService.deleteByFileId(fileRecordId);
        log.info("文件记录删除成功: fileRecordId={}", fileRecordId);
        return AjaxJson.getSuccess("文件删除成功");
    }

    /**
     * 删除业务数据（根据文件类型）
     */
    private void deleteBusinessData(FileRecord fileRecord) {
        Long fileRecordId = fileRecord.getId();
        FileContextType contextType = fileRecord.getFileContextType();

        // 删除未知用途记录（所有文件类型都需要）
        Query query = new Query(Criteria.where("file_record_id").is(fileRecordId));
        long deletedCount = mongoTemplate.remove(query, UnknownUsageRecord.class).getDeletedCount();
        log.info("删除未知用途记录: fileRecordId={}, deletedCount={}", fileRecordId, deletedCount);

        // 删除OCR相关文件（所有文件类型都需要；多次重新解析可能有多条记录）
        deleteOcrExecutionResultsForFile(fileRecordId);

        if (FileContextType.CONTRACT.equals(contextType)) { // 删除合同信息
            deleteContractInfo(fileRecordId);
        } else if (FileContextType.SURVEY_REPORT.equals(contextType)) { // 删除实测报告信息和房间信息
            deleteSurveyReportData(fileRecordId);
        } else if (FileContextType.PLANNING_REVIEW.equals(contextType)) { // 删除规划复核表信息
            deletePlanningReviewData(fileRecordId);
        } else if (FileContextType.CAPACITY_INDICATOR.equals(contextType)) { // 删除容量指标核查表信息
            deleteCapacityIndicatorData(fileRecordId);
        } else if (FileContextType.PROJECT_PARTY_SURVEY_SUMMARY.equals(contextType)) { // 删除项目方实测汇总信息
            deleteProjectPartySummaryData(fileRecordId);
        }
    }

    private void deleteProjectPartySummaryData(Long fileRecordId) {
        Query byFile = new Query(Criteria.where("file_record_id").is(fileRecordId));
        mongoTemplate.remove(byFile, ProjectPartySurveySummaryForm.LEGACY_ROW_COLLECTION);
        Query formQuery = byFile;
        List<ProjectPartySurveySummaryForm> forms = mongoTemplate.find(formQuery, ProjectPartySurveySummaryForm.class);
        mongoTemplate.remove(formQuery, ProjectPartySurveySummaryForm.class);
        if (forms != null) {
            for (ProjectPartySurveySummaryForm form : forms) {
                if (form.getProjectId() != null) {
                    publish(ProjectDataChangedEvent.projectPartySummaryChanged(form.getProjectId(), form.getId()));
                }
            }
        }
        log.info("删除项目方实测汇总数据: fileRecordId={}", fileRecordId);
    }

    private void deletePlanningReviewData(Long fileRecordId) {
        Query rowQuery = new Query(Criteria.where("file_record_id").is(fileRecordId));
        mongoTemplate.remove(rowQuery, PlanningReviewRow.class);
        Query formQuery = new Query(Criteria.where("file_record_id").is(fileRecordId));
        List<PlanningReviewForm> forms = mongoTemplate.find(formQuery, PlanningReviewForm.class);
        mongoTemplate.remove(formQuery, PlanningReviewForm.class);
        if (forms != null) {
            for (PlanningReviewForm f : forms) {
                if (f.getProjectId() != null) {
                    publish(ProjectDataChangedEvent.planningReviewChanged(f.getProjectId()));
                }
            }
        }
        log.info("删除规划复核表数据: fileRecordId={}", fileRecordId);
    }

    private void deleteCapacityIndicatorData(Long fileRecordId) {
        Query query = new Query(Criteria.where("file_record_id").is(fileRecordId));
        List<CapacityIndicatorInfo> records = mongoTemplate.find(query, CapacityIndicatorInfo.class);
        mongoTemplate.remove(query, CapacityIndicatorInfo.class);
        if (records != null) {
            for (CapacityIndicatorInfo info : records) {
                if (info.getProjectId() != null) {
                    publish(ProjectDataChangedEvent.capacityIndicatorChanged(info.getProjectId()));
                }
            }
        }
        log.info("删除容量指标核查表数据: fileRecordId={}", fileRecordId);
    }

    /**
     * 删除合同信息
     */
    private void deleteContractInfo(Long fileRecordId) {
        Query query = new Query(Criteria.where("file_record_id").is(fileRecordId));
        List<ContractInfo> contracts = mongoTemplate.find(query, ContractInfo.class);
        Set<Long> affectedProjectIds = new HashSet<>();
        if (contracts != null) {
            for (ContractInfo c : contracts) {
                if (c != null && c.getProjectId() != null) {
                    affectedProjectIds.add(c.getProjectId());
                }
            }
        }
        if (contracts != null && !contracts.isEmpty()) {
            List<Long> contractIds = contracts.stream()
                    .map(ContractInfo::getId)
                    .filter(id -> id != null)
                    .toList();
            if (!contractIds.isEmpty()) {
                Query parcelQuery = new Query(Criteria.where("contract_id").in(contractIds));
                mongoTemplate.remove(parcelQuery, com.gov.landcheck.core.bo.entity.LandParcel.class).getDeletedCount();

            }
        }
        if (contracts != null && !contracts.isEmpty()) {
            for (ContractInfo contract : contracts) {
                publish(ProjectDataChangedEvent.contractChanged(contract.getProjectId(), contract.getId()));
            }
        }
        long contractDeletedCount = mongoTemplate.remove(query, ContractInfo.class).getDeletedCount();
        log.info("删除合同信息: fileRecordId={}, deletedCount={}", fileRecordId, contractDeletedCount);
        for (Long pid : affectedProjectIds) {
            try {
                surveyReportContractApprovalSyncService.syncAllSurveyReportsInProject(pid);
            } catch (Exception ex) {
                log.warn("同步实测报告合同/批文编号失败 projectId={}", pid, ex);
            }
        }
    }

    /**
     * 删除实测报告数据（包括SurveyReportInfo和RoomInfo）
     */
    private void deleteSurveyReportData(Long fileRecordId) {
        // 删除实测报告信息
        Query surveyQuery = new Query(Criteria.where("file_record_id").is(fileRecordId));
        List<SurveyReportInfo> surveyReports = mongoTemplate.find(surveyQuery, SurveyReportInfo.class);

        long surveyDeletedCount = mongoTemplate.remove(surveyQuery, SurveyReportInfo.class).getDeletedCount();
        log.info("删除实测报告信息: fileRecordId={}, deletedCount={}", fileRecordId, surveyDeletedCount);

        // 删除对应的房间信息
        if (!surveyReports.isEmpty()) {
            List<Long> surveyReportIds = surveyReports.stream()
                    .map(SurveyReportInfo::getId)
                    .toList();

            Query roomQuery = new Query(Criteria.where("survey_report_info_id").in(surveyReportIds));
            long roomDeletedCount = mongoTemplate.remove(roomQuery, RoomInfo.class).getDeletedCount();
            for (SurveyReportInfo report : surveyReports) {
                publish(ProjectDataChangedEvent.surveyReportChanged(report.getProjectId(), report.getId(), true));
            }
            log.info("删除房间信息: surveyReportIds={}, deletedCount={}", surveyReportIds, roomDeletedCount);
        }
    }

    private void deleteOcrExecutionResultsForFile(Long fileRecordId) {
        Query ocrQuery = new Query(Criteria.where("file_record_id").is(fileRecordId));
        List<OCRExecutionResult> results = mongoTemplate.find(ocrQuery, OCRExecutionResult.class);
        if (results == null || results.isEmpty()) {
            return;
        }
        for (OCRExecutionResult result : results) {
            safeDeleteGridFsRef(result.getOcrResultJsonGridfsId(), "ocrExecutionResult.ocrResultJsonGridfsId",
                    fileRecordId);
            safeDeleteGridFsRef(result.getMarkdownFileGridfsId(), "ocrExecutionResult.markdownFileGridfsId",
                    fileRecordId);
        }
        long deletedCount = mongoTemplate.remove(ocrQuery, OCRExecutionResult.class).getDeletedCount();
        log.info("删除OCR执行结果: fileRecordId={}, records={}, gridfsRefs={}",
                fileRecordId, deletedCount, results.size());
    }

    /**
     * 删除解析相关数据
     */
    private void deleteParseData(Long fileRecordId) {
        try {
            // 1. 先找到所有与该文件相关的ParseJob
            Query jobQuery = new Query(Criteria.where("file_record_id").is(fileRecordId));
            List<ParseJob> parseJobs = mongoTemplate.find(jobQuery, ParseJob.class);

            if (parseJobs.isEmpty()) {
                log.info("文件无相关解析任务: fileRecordId={}", fileRecordId);
                return;
            }

            long totalItemDeletedCount = 0;
            long totalHeaderDeletedCount = 0;

            // 2. 对于每个ParseJob，删除对应的数据
            for (ParseJob parseJob : parseJobs) {
                Long parseJobId = parseJob.getId();
                // 删除 ParseJob 直接挂载的中间产物（GridFS）
                safeDeleteGridFsRef(parseJob.getPreprocessGridfsId(), "parseJob.preprocessGridfsId", fileRecordId);
                safeDeleteGridFsRef(parseJob.getOcrResultPath(), "parseJob.ocrResultPath", fileRecordId);
                safeDeleteGridFsRef(parseJob.getLlmResultPath(), "parseJob.llmResultPath", fileRecordId);

                // 删除对应的ParsedDataHeader
                Query headerQuery = new Query(Criteria.where("parse_job_id").is(parseJobId));
                List<ParsedDataHeader> headers = mongoTemplate.find(headerQuery, ParsedDataHeader.class);

                for (ParsedDataHeader header : headers) {
                    Long headerId = header.getId();
                    // 删除 Header 挂载的中间产物（GridFS）
                    safeDeleteGridFsRef(header.getPreprocessGridfsId(), "header.preprocessGridfsId", fileRecordId);
                    safeDeleteGridFsRef(header.getOcrRawDataPath(), "header.ocrRawDataPath", fileRecordId);
                    safeDeleteGridFsRef(header.getLlmRawDataPath(), "header.llmRawDataPath", fileRecordId);
                    safeDeleteGridFsRef(header.getMarkdownDataPath(), "header.markdownDataPath", fileRecordId);

                    // 删除对应的ParsedDataItem（通过header_id关联）
                    Query itemQuery = new Query(Criteria.where("header_id").is(headerId));
                    long itemDeletedCount = mongoTemplate.remove(itemQuery, ParsedDataItem.class).getDeletedCount();
                    totalItemDeletedCount += itemDeletedCount;

                    // 删除ParsedDataHeader
                    mongoTemplate.remove(header);
                    totalHeaderDeletedCount++;
                }

                // 删除OCRExecutionResult（通过parse_job_id关联，含 GridFS）
                Query ocrQuery = new Query(Criteria.where("parse_job_id").is(parseJobId));
                List<OCRExecutionResult> ocrResults = mongoTemplate.find(ocrQuery, OCRExecutionResult.class);
                for (OCRExecutionResult ocrResult : ocrResults) {
                    safeDeleteGridFsRef(ocrResult.getOcrResultJsonGridfsId(),
                            "ocrExecutionResult.ocrResultJsonGridfsId",
                            fileRecordId);
                    safeDeleteGridFsRef(ocrResult.getMarkdownFileGridfsId(), "ocrExecutionResult.markdownFileGridfsId",
                            fileRecordId);
                }
                long ocrDeletedCount = mongoTemplate.remove(ocrQuery, OCRExecutionResult.class).getDeletedCount();
                if (ocrDeletedCount > 0) {
                    log.debug("删除OCR执行结果: parseJobId={}, deletedCount={}", parseJobId, ocrDeletedCount);
                }

                // 删除ParseJob本身
                mongoTemplate.remove(parseJob);
            }

            log.info("删除解析数据完成: fileRecordId={}, parseJobs={}, headers={}, items={}",
                    fileRecordId, parseJobs.size(), totalHeaderDeletedCount, totalItemDeletedCount);

        } catch (Exception e) {
            log.error("删除解析数据失败，中止后续删除以避免数据不一致: fileRecordId={}, error={}",
                    fileRecordId, e.getMessage(), e);
            throw new IllegalStateException("删除解析数据失败: fileRecordId=" + fileRecordId, e);
        }
    }

    /**
     * 尝试删除 GridFS 引用，兼容以下格式：
     * 1) 纯 ObjectId 字符串
     * 2) URL/路径（取最后一段作为备选ID）
     */
    private void safeDeleteGridFsRef(String ref, String source, Long fileRecordId) {
        if (!StringUtils.hasText(ref)) {
            return;
        }
        String trimmed = ref.trim();
        try {
            gridFSUtils.deleteById(trimmed);
            return;
        } catch (Exception ex) {
            // 继续尝试从路径中提取ID，不中断主流程
            log.debug("按原值删除GridFS失败，尝试路径提取: fileRecordId={}, source={}, ref={}, error={}",
                    fileRecordId, source, trimmed, ex.getMessage());
        }
        int lastSlash = Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'));
        if (lastSlash >= 0 && lastSlash < trimmed.length() - 1) {
            String candidate = trimmed.substring(lastSlash + 1);
            try {
                gridFSUtils.deleteById(candidate);
            } catch (Exception ex) {
                log.debug("按路径提取值删除GridFS失败: fileRecordId={}, source={}, ref={}, candidate={}, error={}",
                        fileRecordId, source, trimmed, candidate, ex.getMessage());
            }
        }
    }

    /**
     * 删除GridFS中的所有相关文件
     */
    private void deleteGridFSFiles(FileRecord fileRecord) {
        try {
            Long fileRecordId = fileRecord.getId();

            // 删除原始文件
            if (fileRecord.getGridfsId() != null) {
                gridFSUtils.deleteById(fileRecord.getGridfsId());
                log.info("删除GridFS原始文件: gridfsId={}", fileRecord.getGridfsId());
            }

            // 删除预览图
            if (fileRecord.getThumbGridfsId() != null) {
                gridFSUtils.deleteById(fileRecord.getThumbGridfsId());
                log.info("删除GridFS预览图: gridfsId={}", fileRecord.getThumbGridfsId());
            }

            // 删除文件级别的预处理文件
            if (fileRecord.getPreprocessGridfsId() != null) {
                gridFSUtils.deleteById(fileRecord.getPreprocessGridfsId());
                log.info("删除GridFS文件预处理文件: gridfsId={}", fileRecord.getPreprocessGridfsId());
            }

            // 删除解析任务相关的预处理文件
            Query jobQuery = new Query(Criteria.where("file_record_id").is(fileRecordId));
            List<ParseJob> parseJobs = mongoTemplate.find(jobQuery, ParseJob.class);

            for (ParseJob parseJob : parseJobs) {
                if (parseJob.getPreprocessGridfsId() != null) {
                    gridFSUtils.deleteById(parseJob.getPreprocessGridfsId());
                    log.debug("删除GridFS解析预处理文件: gridfsId={}", parseJob.getPreprocessGridfsId());
                }
            }

        } catch (Exception e) {
            log.warn("删除GridFS文件失败，但继续删除流程: fileRecordId={}, error={}", fileRecord.getId(), e.getMessage());
        }
    }

    @Override
    public AjaxJson batchDeleteFiles(List<String> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "文件ID列表不能为空");
        }
        List<String> successIds = new ArrayList<>();
        List<String> failedIds = new ArrayList<>();
        Map<String, String> errorMessages = new HashMap<>();

        // 逐个删除文件
        for (String fileId : fileIds) {
            AjaxJson deleteResult = deleteFile(fileId);
            if (deleteResult.getCode() == AjaxJson.CODE_SUCCESS) {
                successIds.add(fileId);
            } else {
                failedIds.add(fileId);
                errorMessages.put(fileId, deleteResult.getMsg());
            }

        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalCount", fileIds.size());
        result.put("successCount", successIds.size());
        result.put("failedCount", failedIds.size());
        result.put("successIds", successIds);
        result.put("failedIds", failedIds);

        if (!errorMessages.isEmpty()) {
            result.put("errorMessages", errorMessages);
        }

        if (failedIds.isEmpty()) {
            return AjaxJson.getSuccess("批量删除文件成功").setData(result);
        } else {
            String message = String.format("批量删除文件完成，成功%d个，失败%d个", successIds.size(), failedIds.size());
            return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, message).setData(result);
        }
    }

    @Override
    public AjaxJson uploadFile(FileUploadDTO uploadDTO) {
        if (uploadDTO.getFile() == null || uploadDTO.getFile().isEmpty()) {
            return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "文件不能为空");
        }

        String originFileName = UploadFileNameSanitizer.sanitize(uploadDTO.getFile().getOriginalFilename());
        if (!StringUtils.hasText(originFileName)) {
            return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "文件名非法");
        }

        Project project = mongoTemplate.findById(uploadDTO.getProjectId(), Project.class);
        if (project == null) {
            return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "项目不存在");
        }

        FileContextType fileContextType = uploadDTO.getFileContextType();
        if (fileContextType == null) {
            return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "文件内容类型非法");
        }
        if (!fileContextType.isValidForUpload()) {
            return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "文件内容类型不支持");
        }

        if (fileContextType == FileContextType.SURVEY_REPORT && uploadDTO.getPhase() == null) {
            return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "上传实测文件时必须提供期数");
        }

        FileType fileType = FileType.getFileTypeByFileName(originFileName);
        if (!fileContextType.isSupportedFileType(fileType)) {
            String hint = fileContextType.getSupportedFormatHint();
            String message = hint.isEmpty()
                    ? "文件格式与归档类型不匹配"
                    : fileContextType.getName() + hint;
            return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, message);
        }

        AjaxJson archiveResult = resolveUploadArchiveId(uploadDTO);
        if (archiveResult.getCode() != AjaxJson.CODE_SUCCESS) {
            return archiveResult;
        }
        Long archiveId = (Long) archiveResult.getData();

        Long userId = OperatorContext.getOperatorIdOrDefault(0L);
        String userName = OperatorContext.getOperatorNameOrDefault("system");
        uploadDTO.setOperatorId(userId);
        uploadDTO.setOperatorName(userName);

        String committedFileId = null;
        try {
            FileRecord fileRecord = commitUploadedFile(
                    uploadDTO.getFile(),
                    uploadDTO.getProjectId(),
                    fileContextType,
                    uploadDTO.getPhase(),
                    archiveId,
                    userId,
                    userName);
            committedFileId = fileRecord.getId().toString();

            String postProcessTaskId = taskExecuteService.executeFileUploadPostProcess(
                    committedFileId, userId, userName);

            Map<String, Object> result = new HashMap<>();
            result.put("fileId", committedFileId);
            result.put("fileName", fileRecord.getOriginalName());
            result.put("postProcessTaskId", postProcessTaskId);
            return AjaxJson.getSuccess("文件已入库，正在后台后处理").setData(result);

        } catch (UploadCommitException e) {
            log.warn("文件提交失败: {}", e.getMessage());
            return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "文件提交失败");
        } catch (Exception e) {
            if (committedFileId != null) {
                rollbackCommittedFile(committedFileId, uploadDTO.getProjectId(), e);
            }
            log.error("提交文件上传后处理失败: projectId={}, fileName={}, error={}",
                    uploadDTO.getProjectId(), originFileName, e.getMessage(), e);
            return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "提交文件上传后处理失败");
        }
    }

    private AjaxJson resolveUploadArchiveId(FileUploadDTO uploadDTO) {
        Long archiveId;
        FileContextType fileContextType = uploadDTO.getFileContextType();
        if (uploadDTO.getArchiveId() != null) {
            Query archiveQuery = new Query(Criteria.where("_id").is(uploadDTO.getArchiveId())
                    .and("project_id").is(uploadDTO.getProjectId()));
            FileArchive selectedArchive = mongoTemplate.findOne(archiveQuery, FileArchive.class);
            if (selectedArchive == null) {
                return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "指定的归档夹不存在或不属于当前项目");
            }
            if (Boolean.TRUE.equals(selectedArchive.getIsDefault())
                    && selectedArchive.getKind() != null
                    && selectedArchive.getKind() != FileContextType.OTHER
                    && selectedArchive.getKind() != fileContextType) {
                return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "上传文件类型与默认归档夹类型不一致，请检查后重试");
            }
            archiveId = selectedArchive.getId();
        } else {
            archiveId = fileArchiveService.getArchiveIdByProjectAndKind(
                    uploadDTO.getProjectId(), fileContextType);
        }
        if (archiveId == null) {
            return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "指定的归档夹不存在或不属于当前项目");
        }
        return AjaxJson.getSuccess().setData(archiveId);
    }

    /**
     * 同步落库：FileRecord → GridFS+MD5 → 业务占位 → 审计。
     */
    private FileRecord commitUploadedFile(
            MultipartFile file,
            Long projectId,
            FileContextType fileContextType,
            Integer phase,
            Long archiveId,
            Long userId,
            String userName) {
        String originFileName = UploadFileNameSanitizer.sanitize(file.getOriginalFilename());
        if (!StringUtils.hasText(originFileName)) {
            throw new UploadCommitException("文件名非法");
        }
        FileType fileType = FileType.getFileTypeByFileName(originFileName);

        FileRecord fileRecord = new FileRecord();
        fileRecord.setProjectId(projectId);
        fileRecord.setOriginalName(originFileName);
        fileRecord.setFileType(fileType);
        fileRecord.setFileContextType(fileContextType);
        fileRecord.setFileSize(file.getSize());
        fileRecord.setFileState(FileStateEnum.UPLOADING);
        fileRecord.setUploadUserId(userId);
        fileRecord.setUploadUserName(userName);
        fileRecord.setUploadTime(LocalDateTime.now());
        fileRecord.setPhase(phase);
        fileRecord.setArchiveId(archiveId);

        fileRecord.preSave();
        mongoTemplate.save(fileRecord);

        try (InputStream inputStream = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            try (DigestInputStream digestStream = new DigestInputStream(inputStream, digest)) {
                org.bson.types.ObjectId gridFsId = gridFsTemplate.store(digestStream, originFileName,
                        file.getContentType());
                String md5Hex = HexFormat.of().formatHex(digest.digest());
                fileRecord.setGridfsId(gridFsId.toString());
                fileRecord.setMd5(md5Hex);
                fileRecord.setFileState(FileStateEnum.WAITING_POST_PROCESS);
                mongoTemplate.save(fileRecord);
                try {
                    String changeSummary = AuditDiffHelper.summary(fileRecord, TargetType.FILE);
                    operationAuditService.recordUploadOrMove("UPLOAD", TargetType.FILE.getValue(),
                            String.valueOf(fileRecord.getId()), projectId, null,
                            changeSummary, null);
                } catch (Exception auditEx) {
                    log.warn("上传审计记录失败: fileId={}", fileRecord.getId(), auditEx);
                }
            }
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5算法不可用", e);
        } catch (Exception e) {
            fileRecord.setFileState(FileStateEnum.UPLOAD_FAIL);
            fileRecord.setParseMessage("上传失败: " + e.getMessage());
            mongoTemplate.save(fileRecord);
            log.error("写入GridFS失败: fileName={}, fileId={}, error={}", originFileName, fileRecord.getId(),
                    e.getMessage(), e);
            throw new UploadCommitException("上传失败: " + e.getMessage());
        }

        try {
            createPlaceholderBusinessObject(fileRecord, fileContextType, phase);
        } catch (Exception e) {
            try {
                if (fileRecord.getGridfsId() != null) {
                    gridFSUtils.deleteById(fileRecord.getGridfsId());
                }
            } catch (Exception ex) {
                log.warn("占位创建失败后的GridFS清理失败: fileId={}, gridfsId={}, error={}",
                        fileRecord.getId(), fileRecord.getGridfsId(), ex.getMessage());
            }
            fileRecord.setGridfsId(null);
            fileRecord.setFileState(FileStateEnum.UPLOAD_FAIL);
            fileRecord.setParseMessage("创建业务占位失败: " + e.getMessage());
            mongoTemplate.save(fileRecord);
            log.error("创建业务占位失败: fileName={}, fileId={}, error={}", originFileName, fileRecord.getId(),
                    e.getMessage(), e);
            throw new UploadCommitException("创建业务占位失败: " + e.getMessage());
        }

        log.debug("文件已写入GridFS并进入等待后处理: fileName={}, fileId={}, state={}",
                originFileName, fileRecord.getId(), fileRecord.getFileState());
        return fileRecord;
    }

    private void rollbackCommittedFile(String fileId, Long projectId, Exception cause) {
        log.warn("文件上传异常触发回滚: projectId={}, fileId={}, causeType={}, message={}",
                projectId, fileId, cause != null ? cause.getClass().getSimpleName() : "null",
                cause != null ? cause.getMessage() : "");
        try {
            FileRecord fileRecord = mongoTemplate.findById(fileId, FileRecord.class);
            if (fileRecord == null) {
                log.warn("文件上传回滚：记录已不存在 fileId={}", fileId);
                return;
            }
            AjaxJson r = performFileDeletion(fileRecord);
            if (r == null || r.getCode() == null || r.getCode() != AjaxJson.CODE_SUCCESS) {
                log.warn("文件上传回滚删除未成功: fileId={}, msg={}", fileId, r != null ? r.getMsg() : "null");
            }
        } catch (Exception ex) {
            log.warn("文件上传回滚删除异常: fileId={}, error={}", fileId, ex.getMessage(), ex);
        }
    }

    private static final class UploadCommitException extends RuntimeException {
        UploadCommitException(String message) {
            super(message);
        }
    }

    /**
     * 上传即创建业务占位（幂等）
     */
    private void createPlaceholderBusinessObject(FileRecord fileRecord, FileContextType fileContextType,
            Integer phase) {
        if (fileRecord == null || fileRecord.getId() == null || fileContextType == null) {
            return;
        }
        if (FileContextType.CONTRACT.equals(fileContextType)) {
            Query query = new Query(Criteria.where("file_record_id").is(fileRecord.getId()));
            ContractInfo existing = mongoTemplate.findOne(query, ContractInfo.class);
            if (existing != null) {
                return;
            }
            ContractInfo contractInfo = new ContractInfo();
            contractInfo.setProjectId(fileRecord.getProjectId());
            contractInfo.setFileRecordId(fileRecord.getId());
            contractInfo.preSave();
            mongoTemplate.save(contractInfo);
            auditPlaceholderCreate(TargetType.CONTRACT, contractInfo, fileRecord);
            try {
                surveyReportContractApprovalSyncService.syncAllSurveyReportsInProject(contractInfo.getProjectId());
            } catch (Exception ex) {
                log.warn("同步实测报告合同/批文编号失败 projectId={}", contractInfo.getProjectId(), ex);
            }
            publish(ProjectDataChangedEvent.contractChanged(contractInfo.getProjectId(), contractInfo.getId()));
        } else if (FileContextType.SURVEY_REPORT.equals(fileContextType)) {
            Query query = new Query(Criteria.where("file_record_id").is(fileRecord.getId()));
            SurveyReportInfo existing = mongoTemplate.findOne(query, SurveyReportInfo.class);
            if (existing != null) {
                return;
            }
            SurveyReportInfo surveyReportInfo = new SurveyReportInfo();
            surveyReportInfo.setProjectId(fileRecord.getProjectId());
            surveyReportInfo.setFileRecordId(fileRecord.getId());
            surveyReportInfo.setPhase(phase);
            surveyReportInfo.setIsParsed(0);
            surveyReportInfo.preSave();
            mongoTemplate.save(surveyReportInfo);
            auditPlaceholderCreate(TargetType.SURVEY_REPORT, surveyReportInfo, fileRecord);
            try {
                surveyReportContractApprovalSyncService.syncAllSurveyReportsInProject(surveyReportInfo.getProjectId());
            } catch (Exception ex) {
                log.warn("同步实测报告合同/批文编号失败 projectId={}", surveyReportInfo.getProjectId(), ex);
            }
            publish(ProjectDataChangedEvent.surveyReportChanged(surveyReportInfo.getProjectId(),
                    surveyReportInfo.getId(), false));
        } else if (FileContextType.PLANNING_REVIEW.equals(fileContextType)) {
            Query query = new Query(Criteria.where("file_record_id").is(fileRecord.getId()));
            PlanningReviewForm existing = mongoTemplate.findOne(query, PlanningReviewForm.class);
            if (existing != null) {
                return;
            }
            PlanningReviewForm form = new PlanningReviewForm();
            form.setProjectId(fileRecord.getProjectId());
            form.setFileRecordId(fileRecord.getId());
            form.setIsParsed(0);
            form.preSave();
            mongoTemplate.save(form);
            auditPlaceholderCreate(TargetType.PLANNING_REVIEW_FORM, form, fileRecord);
            publish(ProjectDataChangedEvent.planningReviewChanged(form.getProjectId()));
        } else if (FileContextType.CAPACITY_INDICATOR.equals(fileContextType)) {
            Query query = new Query(Criteria.where("file_record_id").is(fileRecord.getId()));
            CapacityIndicatorInfo existing = mongoTemplate.findOne(query, CapacityIndicatorInfo.class);
            if (existing != null) {
                return;
            }
            CapacityIndicatorInfo info = new CapacityIndicatorInfo();
            info.setProjectId(fileRecord.getProjectId());
            info.setFileRecordId(fileRecord.getId());
            info.setIsParsed(0);
            info.preSave();
            mongoTemplate.save(info);
            auditPlaceholderCreate(TargetType.CAPACITY_INDICATOR_FORM, info, fileRecord);
            publish(ProjectDataChangedEvent.capacityIndicatorChanged(info.getProjectId()));
        } else if (FileContextType.PROJECT_PARTY_SURVEY_SUMMARY.equals(fileContextType)) {
            Query query = new Query(Criteria.where("file_record_id").is(fileRecord.getId()));
            ProjectPartySurveySummaryForm existing = mongoTemplate.findOne(query, ProjectPartySurveySummaryForm.class);
            if (existing != null) {
                return;
            }
            ProjectPartySurveySummaryForm form = new ProjectPartySurveySummaryForm();
            form.setProjectId(fileRecord.getProjectId());
            form.setFileRecordId(fileRecord.getId());
            form.setIsParsed(0);
            form.setParseStatus("PENDING");
            form.preSave();
            mongoTemplate.save(form);
            auditPlaceholderCreate(TargetType.PROJECT_PARTY_SUMMARY_FORM, form, fileRecord);
            publish(ProjectDataChangedEvent.projectPartySummaryChanged(form.getProjectId(), form.getId()));
        }
    }

    private void auditPlaceholderCreate(TargetType targetType, Object entity, FileRecord fileRecord) {
        AuditFileRecorder.recordEntityCreate(operationAuditService, targetType, entity,
                fileRecord.getUploadUserId(), fileRecord.getUploadUserName());
    }

    @Override
    public AjaxJson queryFiles(FileQueryDTO queryDTO) {
        try {
            // 使用 MongoQueryBuilder 动态构建基础查询条件
            Criteria criteria = MongoQueryBuilder.buildCriteria(queryDTO);

            // 上传时间范围（MongoQueryBuilder 不支持范围，在 Service 层合并）
            if (queryDTO.getUploadTimeStart() != null || queryDTO.getUploadTimeEnd() != null) {
                Criteria timeCriteria = new Criteria();
                if (queryDTO.getUploadTimeStart() != null && queryDTO.getUploadTimeEnd() != null) {
                    timeCriteria.and("upload_time").gte(queryDTO.getUploadTimeStart()).lte(queryDTO.getUploadTimeEnd());
                } else if (queryDTO.getUploadTimeStart() != null) {
                    timeCriteria.and("upload_time").gte(queryDTO.getUploadTimeStart());
                } else {
                    timeCriteria.and("upload_time").lte(queryDTO.getUploadTimeEnd());
                }
                criteria = criteria.andOperator(timeCriteria);
            }

            // 未归档仅查（archive_id 为空）
            if (Boolean.TRUE.equals(queryDTO.getUnarchivedOnly())) {
                criteria = criteria.and("archive_id").is(null);
            }

            // 校验状态（与 SurveyReportInfo.is_verified 关联，MongoQueryBuilder 不支持）
            if (StringUtils.hasText(queryDTO.getVerifyStatus())) {
                criteria = applyVerifyStatusFilter(criteria, queryDTO.getVerifyStatus(), queryDTO.getProjectId());
            }

            // 构建查询对象
            Query query = new Query(criteria);

            // 排序与分页
            Sort sort = SafePageSort.resolve(
                    queryDTO.getSortDirection(),
                    queryDTO.getSortField(),
                    "upload_time",
                    MongoSortFields.FILE_RECORD);
            int pageNum = queryDTO.getPageNum() != null && queryDTO.getPageNum() > 0 ? queryDTO.getPageNum() : 1;
            int pageSize = queryDTO.getPageSize() != null && queryDTO.getPageSize() > 0 ? queryDTO.getPageSize() : 10;
            Pageable pageable = PageRequest.of(pageNum - 1, pageSize, sort);
            query.with(pageable);

            List<FileRecord> records = mongoTemplate.find(query, FileRecord.class);
            long total = mongoTemplate.count(query.skip(-1).limit(-1), FileRecord.class);

            // 清除文件内容，只返回基本信息
            records.forEach(file -> file.setFileContent(null));

            // 实测报告类型：批量查 SurveyReportInfo 获取校验状态，并转为 FileRecordVO
            List<Long> surveyReportFileIds = records.stream()
                    .filter(f -> f.getFileContextType() == FileContextType.SURVEY_REPORT && f.getId() != null)
                    .map(FileRecord::getId)
                    .distinct()
                    .collect(Collectors.toList());
            Map<Long, VerificationData> verificationMap = buildSurveyReportVerificationMap(surveyReportFileIds);
            List<FileRecordVO> voList = records.stream()
                    .map(f -> toFileRecordVO(f, verificationMap))
                    .collect(Collectors.toList());

            // 构建分页结果
            FileQueryResultDTO result = new FileQueryResultDTO();
            result.setRecords(voList);
            result.setCurrent(pageNum);
            result.setSize(pageSize);
            result.setTotal(total);
            result.setPages((int) Math.ceil((double) total / pageSize));

            return AjaxJson.getSuccess("查询成功").setData(result);

        } catch (Exception e) {
            log.error("文件查询失败: error={}", e.getMessage(), e);
            return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "文件查询失败");
        }
    }

    private Criteria applyVerifyStatusFilter(Criteria base, String verifyStatus, Long projectId) {
        String status = verifyStatus.trim().toUpperCase();
        if ("PASSED".equals(status)) {
            List<Long> passedIds = findSurveyReportFileRecordIds(projectId, 1);
            return passedIds.isEmpty() ? base.and("_id").is(-1L) : base.and("_id").in(passedIds);
        }
        if ("FAILED".equals(status)) {
            List<Long> failedIds = findSurveyReportFileRecordIds(projectId, 0);
            return failedIds.isEmpty() ? base.and("_id").is(-1L) : base.and("_id").in(failedIds);
        }
        if ("UNVERIFIED".equals(status)) {
            List<Long> decidedIds = findSurveyReportDecidedFileRecordIds(projectId);
            return decidedIds.isEmpty() ? base : base.and("_id").nin(decidedIds);
        }
        return base;
    }

    private List<Long> findSurveyReportFileRecordIds(Long projectId, int isVerified) {
        Criteria criteria = Criteria.where("is_verified").is(isVerified);
        if (projectId != null) {
            criteria = criteria.and("project_id").is(projectId);
        }
        return findFileRecordIdsBySurveyReportCriteria(criteria);
    }

    private List<Long> findSurveyReportDecidedFileRecordIds(Long projectId) {
        Criteria criteria = Criteria.where("is_verified").in(Arrays.asList(0, 1));
        if (projectId != null) {
            criteria = criteria.and("project_id").is(projectId);
        }
        return findFileRecordIdsBySurveyReportCriteria(criteria);
    }

    private List<Long> findFileRecordIdsBySurveyReportCriteria(Criteria criteria) {
        Query query = new Query(criteria);
        query.fields().include("file_record_id");
        return mongoTemplate.find(query, SurveyReportInfo.class).stream()
                .map(SurveyReportInfo::getFileRecordId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    private Map<Long, VerificationData> buildSurveyReportVerificationMap(List<Long> fileRecordIds) {
        if (CollectionUtils.isEmpty(fileRecordIds)) {
            return Map.of();
        }
        Query query = new Query(Criteria.where("file_record_id").in(fileRecordIds));
        List<SurveyReportInfo> list = mongoTemplate.find(query, SurveyReportInfo.class);
        return list.stream()
                .filter(r -> r.getFileRecordId() != null)
                .collect(Collectors.toMap(SurveyReportInfo::getFileRecordId,
                        r -> new VerificationData(r.getIsVerified(), r.getVerificationErrorReason()), (a, b) -> a));
    }

    /**
     * FileRecord 转 FileRecordVO，实测报告类型填充 isVerified、verificationErrorReason
     */
    private FileRecordVO toFileRecordVO(FileRecord record, Map<Long, VerificationData> verificationMap) {
        FileRecordVO vo = new FileRecordVO();
        BeanUtils.copyProperties(record, vo);
        if (record.getFileContextType() == FileContextType.SURVEY_REPORT && record.getId() != null) {
            VerificationData data = verificationMap.get(record.getId());
            if (data != null) {
                vo.setIsVerified(data.isVerified);
                vo.setVerificationErrorReason(data.verificationErrorReason);
            }
        }
        return vo;
    }

    @Override
    public boolean isRegisteredGridFsId(String gridFsId) {
        if (!StringUtils.hasText(gridFsId)) {
            return false;
        }
        String trimmed = gridFsId.trim();
        Criteria criteria = new Criteria().orOperator(
                Criteria.where("gridfs_id").is(trimmed),
                Criteria.where("thumb_gridfs_id").is(trimmed));
        return mongoTemplate.exists(new Query(criteria), FileRecord.class);
    }

    /** 实测报告校验状态，仅用于 Service 内部映射，不对外暴露 */
    private static class VerificationData {
        final Integer isVerified;
        final String verificationErrorReason;

        VerificationData(Integer isVerified, String verificationErrorReason) {
            this.isVerified = isVerified;
            this.verificationErrorReason = verificationErrorReason;
        }
    }
}
