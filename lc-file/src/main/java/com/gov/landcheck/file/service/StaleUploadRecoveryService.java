package com.gov.landcheck.file.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.enums.FileStateEnum;
import com.gov.landcheck.file.config.FileProcessingProperties;
import com.gov.landcheck.file.task.thread.UploadThreadPool;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * 将僵死的上传中 / 后处理中文件标为 {@link FileStateEnum#UPLOAD_FAIL}，由用户删除后重传。
 * <p>
 * 启动时按宽限期清理，避免误杀启动瞬间新写入的记录。运行中按超时扫描：
 * 仍在读取请求体的上传、以及 {@link UploadThreadPool} 中仍活跃的后处理会被跳过。
 */
@Service
@Slf4j
public class StaleUploadRecoveryService {

    public static final String MSG_UPLOADING_INTERRUPTED = "上传中断：服务重启或超时，请删除后重新上传";
    public static final String MSG_POST_PROCESS_INTERRUPTED = "后处理中断：服务重启或超时，请删除后重新上传";

    private static final int CANDIDATE_SCAN_LIMIT = 200;

    @Resource
    private MongoTemplate mongoTemplate;

    @Resource
    private FileProcessingProperties fileProcessingProperties;

    @Resource
    private UploadThreadPool uploadThreadPool;

    @Resource
    private ActiveUploadTracker activeUploadTracker;

    /**
     * 启动恢复：仅标记宽限期之前进入 UPLOADING / WAITING_POST_PROCESS 的记录。
     */
    public RecoveryStats recoverOnStartup() {
        if (!isEnabled()) {
            return RecoveryStats.empty();
        }
        long graceMs = fileProcessingProperties.getStaleUpload().getStartupGraceMs();
        LocalDateTime cutoff = LocalDateTime.now().minus(graceMs, ChronoUnit.MILLIS);
        long uploading = markStateFailedByAge(FileStateEnum.UPLOADING, cutoff, MSG_UPLOADING_INTERRUPTED);
        long postProcess = markStateFailedByAge(
                FileStateEnum.WAITING_POST_PROCESS, cutoff, MSG_POST_PROCESS_INTERRUPTED);
        if (uploading > 0 || postProcess > 0) {
            log.info("启动恢复僵死上传状态: graceMs={}, uploading→FAIL={}, postProcess→FAIL={}",
                    graceMs, uploading, postProcess);
        }
        return new RecoveryStats(uploading, postProcess);
    }

    /**
     * 定时扫描：超时且已停止收数据的 UPLOADING 标 FAIL；超时 WAITING_POST_PROCESS 跳过仍在池中的任务。
     */
    public RecoveryStats recoverTimedOut() {
        if (!isEnabled()) {
            return RecoveryStats.empty();
        }
        FileProcessingProperties.StaleUpload cfg = fileProcessingProperties.getStaleUpload();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime uploadingCutoff = now.minus(cfg.getUploadingTimeoutMs(), ChronoUnit.MILLIS);
        LocalDateTime postProcessCutoff = now.minus(cfg.getPostProcessTimeoutMs(), ChronoUnit.MILLIS);

        long uploading = markStaleUploadingSkippingReceiving(uploadingCutoff, cfg.getUploadingTimeoutMs());
        long postProcess = markStalePostProcessSkippingActive(postProcessCutoff);
        if (uploading > 0 || postProcess > 0) {
            log.info("定时清理僵死上传状态: uploading→FAIL={}, postProcess→FAIL={}", uploading, postProcess);
        }
        return new RecoveryStats(uploading, postProcess);
    }

    private boolean isEnabled() {
        return fileProcessingProperties != null
                && fileProcessingProperties.getStaleUpload() != null
                && fileProcessingProperties.getStaleUpload().isEnabled();
    }

    private long markStateFailedByAge(FileStateEnum fromState, LocalDateTime olderThan, String message) {
        Criteria criteria = Criteria.where("file_state").is(fromState).and("update_time").lt(olderThan);
        Update update = failUpdate(message);
        return mongoTemplate.updateMulti(new Query(criteria), update, FileRecord.class).getModifiedCount();
    }

    /**
     * 上传超时：逐条 FAIL，跳过仍在读取请求体的 fileId，避免慢传输被误杀。
     */
    private long markStaleUploadingSkippingReceiving(LocalDateTime olderThan, long stallTimeoutMs) {
        Query query = new Query(Criteria.where("file_state").is(FileStateEnum.UPLOADING)
                .and("update_time").lt(olderThan));
        query.fields().include("_id");
        query.limit(CANDIDATE_SCAN_LIMIT);
        List<FileRecord> candidates = mongoTemplate.find(query, FileRecord.class);
        if (candidates.isEmpty()) {
            return 0L;
        }

        long marked = 0L;
        Update update = failUpdate(MSG_UPLOADING_INTERRUPTED);
        for (FileRecord record : candidates) {
            if (record == null || record.getId() == null) {
                continue;
            }
            if (activeUploadTracker != null && activeUploadTracker.isReceiving(record.getId(), stallTimeoutMs)) {
                log.debug("跳过仍在接收上传数据的文件: fileId={}", record.getId());
                continue;
            }
            Query one = new Query(Criteria.where("_id").is(record.getId())
                    .and("file_state").is(FileStateEnum.UPLOADING));
            marked += mongoTemplate.updateFirst(one, update, FileRecord.class).getModifiedCount();
        }
        return marked;
    }

    /**
     * 后处理超时：逐条 FAIL，跳过 UploadThreadPool 中仍活跃的 fileId，避免大文件缩略图误杀。
     */
    private long markStalePostProcessSkippingActive(LocalDateTime olderThan) {
        Query query = new Query(Criteria.where("file_state").is(FileStateEnum.WAITING_POST_PROCESS)
                .and("update_time").lt(olderThan));
        query.fields().include("_id");
        query.limit(CANDIDATE_SCAN_LIMIT);
        List<FileRecord> candidates = mongoTemplate.find(query, FileRecord.class);
        if (candidates.isEmpty()) {
            return 0L;
        }

        long marked = 0L;
        Update update = failUpdate(MSG_POST_PROCESS_INTERRUPTED);
        for (FileRecord record : candidates) {
            if (record == null || record.getId() == null) {
                continue;
            }
            String fileId = String.valueOf(record.getId());
            if (uploadThreadPool != null && uploadThreadPool.isPostProcessActive(fileId)) {
                log.debug("跳过仍在后处理中的超时文件: fileId={}", fileId);
                continue;
            }
            Query one = new Query(Criteria.where("_id").is(record.getId())
                    .and("file_state").is(FileStateEnum.WAITING_POST_PROCESS));
            long n = mongoTemplate.updateFirst(one, update, FileRecord.class).getModifiedCount();
            marked += n;
        }
        return marked;
    }

    private static Update failUpdate(String message) {
        return new Update()
                .set("file_state", FileStateEnum.UPLOAD_FAIL)
                .set("parse_message", message)
                .set("update_time", LocalDateTime.now());
    }

    public record RecoveryStats(long uploadingFailed, long postProcessFailed) {
        static RecoveryStats empty() {
            return new RecoveryStats(0, 0);
        }
    }
}
