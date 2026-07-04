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
 * 应用重启后恢复 WAITING_PARSE 文件的自动解析入队（在 ParseJobStateRecovery 之后执行）。
 */
@Slf4j
@Component
@Order(1)
public class AutoParseStartupRecovery implements ApplicationRunner {

    private final MongoTemplate mongoTemplate;
    private final DeferredParseSubmissionService deferredParseSubmissionService;
    private final FileProcessingProperties properties;

    public AutoParseStartupRecovery(
            MongoTemplate mongoTemplate,
            DeferredParseSubmissionService deferredParseSubmissionService,
            FileProcessingProperties properties) {
        this.mongoTemplate = mongoTemplate;
        this.deferredParseSubmissionService = deferredParseSubmissionService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.getAutoParse().isEnabled()) {
            return;
        }
        try {
            Query query = new Query(Criteria.where("file_state").is(FileStateEnum.WAITING_PARSE));
            List<FileRecord> waitingFiles = mongoTemplate.find(query, FileRecord.class);
            int enqueued = 0;
            for (FileRecord fileRecord : waitingFiles) {
                if (fileRecord.getId() == null) {
                    continue;
                }
                if (!FileContextType.isAutoParseContext(fileRecord.getFileContextType())) {
                    continue;
                }
                deferredParseSubmissionService.enqueueAfterUpload(fileRecord.getId());
                enqueued++;
            }
            if (enqueued > 0) {
                log.info("启动恢复：已将 {} 个 WAITING_PARSE 文件加入自动解析队列", enqueued);
            }
        } catch (Exception e) {
            log.error("启动自动解析恢复失败: {}", e.getMessage(), e);
        }
    }
}
