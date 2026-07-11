package com.gov.landcheck.file.service.parse;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.core.enums.FileStateEnum;
import com.gov.landcheck.file.config.FileProcessingProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * 应用重启后恢复 WAITING_PARSE 文件的自动解析（在 ParseJobStateRecovery 之后执行）。
 */
@Slf4j
@Component
@Order(1)
public class AutoParseStartupRecovery implements ApplicationRunner {

    private final MongoTemplate mongoTemplate;
    private final AutoParseSubmissionService autoParseSubmissionService;
    private final FileProcessingProperties properties;

    public AutoParseStartupRecovery(
            MongoTemplate mongoTemplate,
            AutoParseSubmissionService autoParseSubmissionService,
            FileProcessingProperties properties) {
        this.mongoTemplate = mongoTemplate;
        this.autoParseSubmissionService = autoParseSubmissionService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.getAutoParse().isEnabled()) {
            return;
        }
        try {
            Query query = new Query(Criteria.where("file_state").is(FileStateEnum.WAITING_PARSE)
                    .and("auto_parse_suppressed").ne(true));
            List<FileRecord> waitingFiles = mongoTemplate.find(query, FileRecord.class);
            int attempted = 0;
            int submitted = 0;
            for (FileRecord fileRecord : waitingFiles) {
                if (fileRecord.getId() == null) {
                    continue;
                }
                if (!FileContextType.isAutoParseContext(fileRecord.getFileContextType())) {
                    continue;
                }
                attempted++;
                if (autoParseSubmissionService.submitWaitingFile(fileRecord)) {
                    submitted++;
                }
            }
            if (attempted > 0) {
                log.info("启动恢复：尝试 {} 个 WAITING_PARSE 文件，成功提交 {} 个到解析线程池", attempted, submitted);
            }
        } catch (Exception e) {
            log.error("启动自动解析恢复失败: {}", e.getMessage(), e);
        }
    }
}
