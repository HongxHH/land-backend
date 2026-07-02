package com.gov.landcheck.core.config.global;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.ParseJob;
import com.gov.landcheck.core.enums.FileStateEnum;
import com.gov.landcheck.core.enums.ParseJobStateEnum;

import lombok.extern.slf4j.Slf4j;

/**
 * 解析任务状态恢复器
 * 用于在应用启动时清理因程序异常退出而悬挂的解析任务状态
 *
 * @author system
 * @date 2026/01/23
 */
@Slf4j
@Component
public class ParseJobStateRecovery implements ApplicationRunner {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            // 1. 恢复悬挂的 ParseJob 状态
            recoverHangingParseJobs();

            // 2. 恢复悬挂的 FileRecord 状态
            recoverHangingFileRecords();

            log.info("解析任务状态恢复完成");
        } catch (Exception e) {
            log.error("解析任务状态恢复失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 恢复悬挂的解析任务
     * 将状态为 PENDING 或 RUNNING 的任务标记为 FAILED
     */
    private void recoverHangingParseJobs() {
        // 查询所有处于进行中状态的解析任务
        Query query = new Query(Criteria.where("job_status").in(
            ParseJobStateEnum.PENDING.getCode(),
            ParseJobStateEnum.RUNNING.getCode()
        ));

        Update update = new Update()
            .set("job_status", ParseJobStateEnum.FAILED.getCode())
            .set("error_message", "任务因程序重启而被中断")
            .set("finished_at", LocalDateTime.now());

        // 执行批量更新
        long updatedCount = mongoTemplate.updateMulti(query, update, ParseJob.class).getModifiedCount();
        log.debug("已恢复 {} 个悬挂的解析任务状态", updatedCount);
    }

    /**
     * 恢复悬挂的文件记录状态
     * 将状态为 PENDING 或 PARSING 的文件标记为 PARSE_FAIL
     */
    private void recoverHangingFileRecords() {
        // 查询所有处于解析中状态的文件记录
        Query query = new Query(Criteria.where("file_state").in(
            FileStateEnum.PENDING.getCode(),
            FileStateEnum.PARSING.getCode()
        ));

        Update update = new Update()
            .set("file_state", FileStateEnum.PARSE_FAIL.getCode());

        // 执行批量更新
        long updatedCount = mongoTemplate.updateMulti(query, update, FileRecord.class).getModifiedCount();
        log.debug("已恢复 {} 个悬挂的文件记录状态", updatedCount);
    }
}