package com.gov.landcheck.file.service;

import com.gov.landcheck.core.bo.entity.ParseJob;

/**
 * 解析任务状态更新服务
 * 统一负责「设置内存中的 ParseJob + 落库」，避免 Command 中先 markXxx 再 updateXxx 的重复更新。
 *
 * @author system
 * @date 2026/02/05
 */
public interface ParseJobUpdateService {

    /**
     * 更新为运行中（任务开始）
     *
     * @return 是否成功落库（已取消的任务返回 false）
     */
    boolean updateRunning(ParseJob parseJob);

    /**
     * 预处理开始
     */
    void updatePreprocessStarted(ParseJob parseJob);

    /**
     * 预处理完成
     */
    void updatePreprocessCompleted(ParseJob parseJob, String preprocessGridfsId);

    /**
     * 预处理失败
     */
    void updatePreprocessFailed(ParseJob parseJob, String errorMessage);

    /**
     * OCR 开始
     */
    void updateOcrStarted(ParseJob parseJob);

    /**
     * OCR 完成
     */
    void updateOcrCompleted(ParseJob parseJob, String ocrResultPath);

    /**
     * OCR 失败
     */
    void updateOcrFailed(ParseJob parseJob, String errorMessage);

    /**
     * 解析开始
     */
    void updateParseStarted(ParseJob parseJob);

    /**
     * 解析完成
     */
    void updateParseCompleted(ParseJob parseJob, String llmResultPath);

    /**
     * 解析失败
     */
    void updateParseFailed(ParseJob parseJob, String errorMessage);

    /**
     * 回填开始
     */
    void updateFillStarted(ParseJob parseJob);

    /**
     * 回填完成
     */
    void updateFillCompleted(ParseJob parseJob, Integer fillResultCount);

    /**
     * 回填失败
     */
    void updateFillFailed(ParseJob parseJob, String errorMessage);

    /**
     * 校验开始
     */
    void updateValidateStarted(ParseJob parseJob);

    /**
     * 校验完成
     */
    void updateValidateCompleted(ParseJob parseJob, String summary);

    /**
     * 校验失败
     */
    void updateValidateFailed(ParseJob parseJob, String errorMessage);

    /**
     * 任务成功
     *
     * @return 是否成功落库（已取消的任务返回 false）
     */
    boolean updateJobSuccess(ParseJob parseJob, Long executionTimeMs);

    /**
     * 任务失败
     */
    void updateJobFailed(ParseJob parseJob, String errorMessage);

    /**
     * 任务取消
     */
    void updateJobCancelled(ParseJob parseJob);
}
