package com.gov.landcheck.file.service.parse;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.ParseJob;
import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.core.enums.FileStateEnum;
import com.gov.landcheck.file.dto.BulkParseEnqueueResultDTO;
import com.gov.landcheck.file.dto.SubmitParseResult;
import com.gov.landcheck.file.task.thread.TaskThreadPool;

import lombok.extern.slf4j.Slf4j;

/**
 * 全项目范围内，将待解析/解析失败文件批量提交到解析线程池。
 */
@Slf4j
@Service
public class GlobalParseEnqueueService {

    private final MongoTemplate mongoTemplate;
    private final FileParseSubmissionService fileParseSubmissionService;
    private final TaskThreadPool taskThreadPool;

    public GlobalParseEnqueueService(
            MongoTemplate mongoTemplate,
            FileParseSubmissionService fileParseSubmissionService,
            TaskThreadPool taskThreadPool) {
        this.mongoTemplate = mongoTemplate;
        this.fileParseSubmissionService = fileParseSubmissionService;
        this.taskThreadPool = taskThreadPool;
    }

    public BulkParseEnqueueResultDTO enqueuePendingAndFailed() {
        Query query = new Query(Criteria.where("file_state")
                .in(FileStateEnum.WAITING_PARSE, FileStateEnum.PARSE_FAIL))
                .with(Sort.by(Sort.Direction.ASC, "update_time"));
        List<FileRecord> candidates = mongoTemplate.find(query, FileRecord.class);

        int scanned = 0;
        int submitted = 0;
        int skipped = 0;
        boolean queueFull = false;

        for (FileRecord fileRecord : candidates) {
            if (!taskThreadPool.hasSubmissionCapacity()) {
                queueFull = true;
                break;
            }
            if (fileRecord.getId() == null || !FileContextType.isAutoParseContext(fileRecord.getFileContextType())) {
                continue;
            }
            ParseJob latestJob = fileParseSubmissionService.findLatestParseJobByFileRecordId(fileRecord.getId());
            if (ParseCancelSupport.isUserCancelled(latestJob)) {
                skipped++;
                log.debug("批量入队跳过（用户已取消）: fileId={}", fileRecord.getId());
                continue;
            }
            scanned++;
            SubmitParseResult result = fileParseSubmissionService.submitParseIfEligible(fileRecord);
            if (result.isSubmitted()) {
                submitted++;
            } else {
                skipped++;
                log.debug("批量入队跳过: fileId={}, reason={}", fileRecord.getId(), result.getErrorMessage());
            }
        }

        int remainingEstimate = countEligibleRemaining();

        if (submitted > 0) {
            log.info("全库批量入队解析: 扫描 {} 个, 提交 {} 个, 跳过 {} 个, queueFull={}",
                    scanned, submitted, skipped, queueFull);
        }

        return BulkParseEnqueueResultDTO.builder()
                .scanned(scanned)
                .submitted(submitted)
                .skipped(skipped)
                .queueFull(queueFull)
                .remainingEstimate(remainingEstimate)
                .build();
    }

    private int countEligibleRemaining() {
        Query query = new Query(Criteria.where("file_state")
                .in(FileStateEnum.WAITING_PARSE, FileStateEnum.PARSE_FAIL)
                .and("file_context_type")
                .in(
                        FileContextType.CONTRACT,
                        FileContextType.SURVEY_REPORT,
                        FileContextType.PLANNING_REVIEW,
                        FileContextType.CAPACITY_INDICATOR,
                        FileContextType.PROJECT_PARTY_SURVEY_SUMMARY));
        return (int) mongoTemplate.count(query, FileRecord.class);
    }
}
