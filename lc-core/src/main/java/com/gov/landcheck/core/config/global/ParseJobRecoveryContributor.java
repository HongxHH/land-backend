package com.gov.landcheck.core.config.global;

import java.util.List;

import com.gov.landcheck.core.bo.entity.ParseJob;

/**
 * 解析任务悬挂恢复时的中间产物清理扩展点（由 lc-file 模块实现）。
 */
public interface ParseJobRecoveryContributor {

    /**
     * 在悬挂 ParseJob 被批量标记为 FAILED 之前，清理其产生的中间数据与半回填业务状态。
     */
    void cleanupBeforeMarkFailed(List<ParseJob> hangingJobs);
}
