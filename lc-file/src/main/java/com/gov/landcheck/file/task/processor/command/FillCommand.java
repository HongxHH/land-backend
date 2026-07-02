package com.gov.landcheck.file.task.processor.command;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import com.gov.landcheck.core.bo.entity.PlanningReviewRow;
import com.gov.landcheck.core.bo.entity.ProjectPartySurveySummaryForm;
import com.gov.landcheck.core.bo.entity.RoomInfo;
import com.gov.landcheck.file.service.ParseJobUpdateService;
import com.gov.landcheck.file.task.base.AbstractCommand;
import com.gov.landcheck.file.task.base.TaskData;
import com.gov.landcheck.file.task.base.TaskException;
import com.gov.landcheck.file.task.processor.receiver.DataFillReceiver;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * 命令模式中角色：具体命令 - 数据回填命令
 * 数据回填命令 - 执行数据回填操作
 *
 * @author system
 * @date 2026/01/27
 */
@Slf4j
@Component
public class FillCommand extends AbstractCommand {

    @Resource
    private DataFillReceiver dataFillReceiver;

    @Resource
    private ParseJobUpdateService parseJobUpdateService;

    @Resource
    private MongoTemplate mongoTemplate;

    public FillCommand() {
        super("数据回填", "FILL");
    }

    @Override
    protected void doExecute(TaskData taskData) throws TaskException {
        log.debug("开始数据回填: fileId={}", taskData.getFileRecord().getId());

        taskData.setFillCommandEntered(true);

        parseJobUpdateService.updateFillStarted(taskData.getParseJob());

        try {
            dataFillReceiver.fillData(taskData);

            // 标记回填完成（目前没有具体的回填条数统计）
            parseJobUpdateService.updateFillCompleted(taskData.getParseJob(), null);

            log.debug("数据回填完成: fileId={}", taskData.getFileRecord().getId());

        } catch (Exception e) {
            // 根据异常类型确定具体的错误码
            TaskException.ErrorCode errorCode = determineFillErrorCode(e);
            parseJobUpdateService.updateFillFailed(taskData.getParseJob(), e.getMessage());

            throw new TaskException(
                    errorCode,
                    getStage(),
                    taskData.getFileRecord().getId(),
                    taskData.getParseJob() != null ? taskData.getParseJob().getId() : null,
                    "数据回填失败: " + e.getMessage(),
                    e);
        }
    }

    /**
     * 数据回填阶段回滚：
     * 1. 删除本文件下当前房间数据（即 Fill 阶段插入的“新”数据）
     */
    @Override
    public void rollback(TaskData taskData) throws TaskException {
        if (taskData == null || taskData.getFileRecord() == null || taskData.getParseJob() == null) {
            return;
        }
        Long fileRecordId = taskData.getFileRecord().getId();
        Long parseJobId = taskData.getParseJob().getId();

        if (!taskData.isFillCommandEntered()) {
            return;
        }

        Query deleteQuery = new Query(Criteria.where("file_record_id").is(fileRecordId));
        long deleted = mongoTemplate.remove(deleteQuery, RoomInfo.class).getDeletedCount();
        long prRows = mongoTemplate.remove(deleteQuery, PlanningReviewRow.class).getDeletedCount();
        long legacyPartySummaryDeleted = mongoTemplate.remove(deleteQuery,
                ProjectPartySurveySummaryForm.LEGACY_ROW_COLLECTION).getDeletedCount();
        log.debug(
                "回填回滚：已删除房间/规划复核行/历史项目方汇总数据, fileRecordId={}, parseJobId={}, roomsDeleted={}, planningRowsDeleted={}, legacyPartySummaryDeleted={}",
                fileRecordId, parseJobId, deleted, prRows, legacyPartySummaryDeleted);
    }

    /**
     * 根据异常类型确定回填错误码
     */
    private TaskException.ErrorCode determineFillErrorCode(Exception exception) {
        String message = exception.getMessage();
        if (message == null)
            message = "";

        String lowerMessage = message.toLowerCase();

        // 检查是否是TaskException，如果是则保持原有错误码
        if (exception instanceof TaskException taskException) {
            return taskException.getErrorCode();
        }

        // 回填操作主要是数据库操作，超时通常是数据库相关
        if (lowerMessage.contains("timeout") || lowerMessage.contains("time out")) {
            return TaskException.ErrorCode.FILL_TIMEOUT;
        }

        // 默认使用通用回填失败错误码
        return TaskException.ErrorCode.FILL_FAILED;
    }
}