package com.gov.landcheck.file.service.parse;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.ParseFillSnapshot;
import com.gov.landcheck.core.bo.entity.ParseJob;
import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.file.task.base.TaskData;
import com.gov.landcheck.file.task.context.FileParseModule;
import com.gov.landcheck.file.task.context.FileParseModuleRegistry;

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

    @Resource
    private FileParseModuleRegistry fileParseModuleRegistry;

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
        tpl.executeWithoutResult(status -> action.run());
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
        FileParseModule module = fileParseModuleRegistry.find(contextType);
        if (module != null) {
            module.captureFillSnapshot(snapshot, fileRecord.getId());
        }
        return snapshot;
    }

    void hydrateTaskData(TaskData taskData, ParseFillSnapshot snapshot) {
        if (taskData == null || snapshot == null) {
            return;
        }
        FileContextType contextType = snapshot.getFileContextType();
        if (contextType == null) {
            return;
        }
        FileParseModule module = fileParseModuleRegistry.find(contextType);
        if (module != null) {
            module.hydrateTaskData(taskData, snapshot);
        }
    }

    private void restoreFromSnapshot(ParseFillSnapshot snapshot) {
        FileContextType contextType = snapshot.getFileContextType();
        Long fileRecordId = snapshot.getFileRecordId();
        if (contextType == null || fileRecordId == null) {
            return;
        }
        FileParseModule module = fileParseModuleRegistry.find(contextType);
        if (module != null) {
            module.restoreFillSnapshot(snapshot);
        }
    }
}
