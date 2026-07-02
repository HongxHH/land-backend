package com.gov.landcheck.file.service;

import java.util.List;

import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.ParseJob;
import com.gov.landcheck.core.enums.FileStateEnum;
import com.gov.landcheck.core.bo.dto.SystemRuntimeStatusDTO;
import com.gov.landcheck.file.dto.TaskStatusDTO;
import com.gov.landcheck.core.bo.dto.ThreadPoolResizeDTO;

public interface ITaskExecuteService {
    /**
     * 任务执行的入口
     * 
     * @param fileRecord                     文件记录
     * @param rollbackFileStateIfSubmitFails 提交线程池失败等异常时，将仍处于
     *                                       {@link FileStateEnum#PENDING} 的文件恢复为该状态
     */
    String executeParseTask(FileRecord fileRecord, FileStateEnum rollbackFileStateIfSubmitFails);

    /**
     * 重试解析任务的入口（使用现有的ParseJob）
     * 
     * @param existingParseJob 已存在的解析任务
     * @param fileRecord       文件记录
     * @return 任务ID
     */
    String retryParseTask(ParseJob existingParseJob, FileRecord fileRecord);

    /**
     * 单文件上传后处理任务入口（缩略图、状态迁移、自动解析提交）。
     *
     * @param fileId       已落库文件 ID
     * @param operatorId   操作人 ID
     * @param operatorName 操作人名称
     * @return 后处理任务 ID
     */
    String executeFileUploadPostProcess(String fileId, Long operatorId, String operatorName);

    /**
     * 取消解析任务（通过线程池取消正在运行的任务）
     * 
     * @param taskId 任务ID
     * @param reason 取消原因
     * @return 是否成功从线程池取消（若任务未在运行则返回 false，仅表示未执行线程池取消）
     */
    boolean cancelParseTask(String taskId, String reason);

    /**
     * 回滚指定解析任务产生的中间数据（任务未在线程池运行时使用）。
     */
    void rollbackParseJob(ParseJob parseJob, FileRecord fileRecord);

    /**
     * 检查指定任务是否仍在运行（未完成且未从线程池移除）
     * 
     * @param taskId 任务ID
     * @return 是否正在运行
     */
    boolean isTaskRunning(String taskId);

    /**
     * 获取当前任务状态信息
     * 包括正在执行的任务、排队任务和线程池状态
     *
     * @return 任务状态信息
     */
    TaskStatusDTO getTaskStatus();

    /**
     * 获取单个任务的详细进度信息（含阶段轨迹）
     * 
     * @param taskId 任务ID
     * @return 任务详细信息，不存在时返回 null
     */
    TaskStatusDTO.RunningTaskInfo getTaskDetail(String taskId);

    /**
     * 根据持久化的解析任务 ID 获取解析流水线视图（任务结束后仍可查）
     */
    TaskStatusDTO.RunningTaskInfo getParseJobFlowDetail(Long parseJobId);

    /**
     * 按 taskId 直接取消任务
     * 
     * @param taskId 任务ID
     * @param reason 取消原因
     * @return 是否取消成功
     */
    boolean cancelTaskByTaskId(String taskId, String reason);

    /**
     * 获取系统运行状态（CPU/内存/线程/连接池/GPU）
     * 
     * @return 系统状态
     */
    SystemRuntimeStatusDTO getSystemRuntimeStatus();

    /**
     * 动态调整任务线程池参数
     * 
     * @param resizeDTO 核心线程数与最大线程数
     */
    void updateTaskPoolSize(ThreadPoolResizeDTO resizeDTO);
}