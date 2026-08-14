package com.gov.landcheck.file.config;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.ParseJob;
import com.gov.landcheck.core.enums.FileStateEnum;
import com.gov.landcheck.core.enums.ParseJobStateEnum;
import com.gov.landcheck.file.service.parse.ParseArtifactCleanupService;

import lombok.extern.slf4j.Slf4j;

/**
 * 解析任务状态恢复器：应用启动时清理悬挂的解析任务状态。
 */
@Slf4j
@Component
@Order(0)
public class ParseJobStateRecovery implements ApplicationRunner {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired(required = false)
    private ParseArtifactCleanupService parseArtifactCleanupService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            recoverHangingParseJobs();
            recoverHangingFileRecords();
            log.info("解析任务状态恢复完成");
        } catch (Exception e) {
            log.error("解析任务状态恢复失败: {}", e.getMessage(), e);
        }
    }

    private void recoverHangingParseJobs() {
        Query query = new Query(Criteria.where("job_status").in(
                ParseJobStateEnum.PENDING.getCode(),
                ParseJobStateEnum.RUNNING.getCode()));
        List<ParseJob> hangingJobs = mongoTemplate.find(query, ParseJob.class);

        if (parseArtifactCleanupService != null && !hangingJobs.isEmpty()) {
            try {
                parseArtifactCleanupService.cleanupBeforeMarkFailed(hangingJobs);
            } catch (Exception ex) {
                log.error("悬挂解析任务中间产物清理失败，将继续更新状态: {}", ex.getMessage(), ex);
            }
        }

        Update update = new Update()
                .set("job_status", ParseJobStateEnum.FAILED.getCode())
                .set("error_message", "任务因程序重启而被中断")
                .set("finished_at", LocalDateTime.now());

        long updatedCount = mongoTemplate.updateMulti(query, update, ParseJob.class).getModifiedCount();
        log.debug("已恢复 {} 个悬挂的解析任务状态", updatedCount);
    }

    private void recoverHangingFileRecords() {
        Query pendingQuery = new Query(Criteria.where("file_state").is(FileStateEnum.PENDING.getCode()));
        Update pendingUpdate = new Update()
                .set("file_state", FileStateEnum.WAITING_PARSE.getCode())
                .unset("parse_job_id")
                .unset("auto_parse_queued_at");
        long pendingCount = mongoTemplate.updateMulti(pendingQuery, pendingUpdate, FileRecord.class).getModifiedCount();
        log.debug("已将 {} 个 PENDING 文件恢复为 WAITING_PARSE", pendingCount);

        Set<Long> completedParseJobIds = recoverCompletedParsingFileRecords();

        Criteria parsingCriteria = Criteria.where("file_state").is(FileStateEnum.PARSING.getCode());
        if (!completedParseJobIds.isEmpty()) {
            parsingCriteria.and("parse_job_id").nin(completedParseJobIds);
        }
        Query parsingQuery = new Query(parsingCriteria);
        Update parsingUpdate = new Update()
                .set("file_state", FileStateEnum.PARSE_FAIL.getCode())
                .unset("preprocess_gridfs_id");
        long parsingCount = mongoTemplate.updateMulti(parsingQuery, parsingUpdate, FileRecord.class).getModifiedCount();
        log.debug("已将 {} 个 PARSING 文件恢复为 PARSE_FAIL", parsingCount);
    }

    private Set<Long> recoverCompletedParsingFileRecords() {
        Query parsingWithJobQuery = new Query(Criteria.where("file_state").is(FileStateEnum.PARSING.getCode())
                .and("parse_job_id").ne(null));
        List<FileRecord> parsingRecords = mongoTemplate.find(parsingWithJobQuery, FileRecord.class);
        Set<Long> parseJobIds = parsingRecords.stream()
                .map(FileRecord::getParseJobId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (parseJobIds.isEmpty()) {
            return Set.of();
        }

        Query successJobQuery = new Query(Criteria.where("_id").in(parseJobIds)
                .and("job_status").is(ParseJobStateEnum.SUCCESS.getCode()));
        Set<Long> successJobIds = mongoTemplate.find(successJobQuery, ParseJob.class).stream()
                .map(ParseJob::getId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (successJobIds.isEmpty()) {
            return Set.of();
        }

        Query restoreQuery = new Query(Criteria.where("file_state").is(FileStateEnum.PARSING.getCode())
                .and("parse_job_id").in(successJobIds));
        Update restoreUpdate = new Update()
                .set("file_state", FileStateEnum.PARSE_COMPLETE.getCode())
                .unset("auto_parse_queued_at");
        long restoredCount = mongoTemplate.updateMulti(restoreQuery, restoreUpdate, FileRecord.class).getModifiedCount();
        log.debug("已将 {} 个 ParseJob 成功但 FileRecord 仍为 PARSING 的文件恢复为 PARSE_COMPLETE", restoredCount);
        return successJobIds;
    }
}
