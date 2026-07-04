package com.gov.landcheck.file.service.parse;

import org.springframework.util.StringUtils;

import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.ParseJob;
import com.gov.landcheck.file.task.base.TaskData;

/**
 * 根据 ParseJob 持久化阶段状态推断管道执行进度（供离线回滚 / 崩溃恢复使用）。
 */
public final class ParseJobStageHelper {

    private ParseJobStageHelper() {
    }

    public static boolean hasFillStageStarted(ParseJob parseJob, TaskData taskData) {
        if (taskData != null && taskData.isFillCommandEntered()) {
            return true;
        }
        return stageStarted(parseJob != null ? parseJob.getFillStatus() : null);
    }

    public static boolean hasValidateStageStarted(ParseJob parseJob, TaskData taskData) {
        return stageStarted(parseJob != null ? parseJob.getValidateStatus() : null);
    }

    private static boolean stageStarted(String status) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        String normalized = status.trim().toUpperCase();
        return !"PENDING".equals(normalized) && !"SKIPPED".equals(normalized);
    }

    public static TaskData taskDataForOfflineRollback(ParseJob parseJob, FileRecord fileRecord) {
        TaskData taskData = new TaskData();
        taskData.setFileRecord(fileRecord);
        taskData.setParseJob(parseJob);
        if (hasFillStageStarted(parseJob, null)) {
            taskData.setFillCommandEntered(true);
        }
        return taskData;
    }
}
