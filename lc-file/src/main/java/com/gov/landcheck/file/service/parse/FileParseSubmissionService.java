package com.gov.landcheck.file.service.parse;

import java.util.concurrent.RejectedExecutionException;

import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.ParseJob;
import com.gov.landcheck.core.common.MessageConstant;
import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.core.enums.FileStateEnum;
import com.gov.landcheck.core.enums.ParseJobStateEnum;
import com.gov.landcheck.file.dto.SubmitParseResult;
import com.gov.landcheck.file.service.FileService;
import com.gov.landcheck.file.service.ITaskExecuteService;
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
    private GridFSUtils gridFSUtils;
    @Resource
    private ITaskExecuteService taskExecuteService;
    @Lazy
    @Resource
    private FileService fileService;

    @Resource
    private ParseArtifactCleanupService parseArtifactCleanupService;

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

    public ParseJob findByTaskId(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return null;
        }
        return mongoTemplate.findOne(Query.query(Criteria.where("task_id").is(taskId)), ParseJob.class);
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
            if (!FileContextType.isAutoParseContext(fileRecord.getFileContextType())) {
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
                fileService.cleanupFileRecordWhenGridFsMissing(fileRecord);
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
                        .set("update_time", java.time.LocalDateTime.now())
                        .unset("auto_parse_queued_at");
                UpdateResult result = mongoTemplate.updateFirst(query, update, FileRecord.class);
                acquired = result.getModifiedCount() > 0;
            }
            if (!acquired) {
                return SubmitParseResult.fail(String.valueOf(MessageConstant.PARAMS_ERROR_CODE), "文件解析任务已在进行中");
            }
            fileRecord.setFileState(FileStateEnum.PENDING);
            fileRecord.setAutoParseQueuedAt(null);

            String taskId = taskExecuteService.executeParseTask(fileRecord, rollbackState);
            return SubmitParseResult.success(taskId);
        } catch (Exception e) {
            log.error("提交解析任务异常: fileId={}, error={}", fileRecord != null ? fileRecord.getId() : null, e.getMessage(),
                    e);
            if (acquired && rollbackState != null && fileRecordId != null) {
                revertPendingParseReservation(fileRecordId, rollbackState);
            }
            return SubmitParseResult.fail(String.valueOf(MessageConstant.PARAMS_ERROR_CODE),
                    resolveSubmitFailureMessage(e));
        }
    }

    private static String resolveSubmitFailureMessage(Exception e) {
        if (containsCause(e, RejectedExecutionException.class)) {
            return "解析任务线程池已满，请稍后再试";
        }
        String message = e.getMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }
        return "解析文件失败";
    }

    private static boolean containsCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
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
                    .unset("parse_job_id")
                    .unset("auto_parse_queued_at");
            mongoTemplate.updateFirst(q, u, FileRecord.class);
            log.warn("已回滚解析抢占状态: fileRecordId={}, rollbackState={}", fileRecordId, rollbackState);
        } catch (Exception ex) {
            log.error("回滚解析抢占状态失败: fileRecordId={}, rollbackState={}, error={}",
                    fileRecordId, rollbackState, ex.getMessage(), ex);
        }
    }

    private void resetBusinessStateBeforeParse(FileRecord fileRecord) {
        parseArtifactCleanupService.resetBusinessStateBeforeParse(fileRecord);
    }
}
