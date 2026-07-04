package com.gov.landcheck.file.service;

import java.util.List;
import java.util.Optional;

import com.gov.landcheck.core.bo.R.AjaxJson;
import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.file.dto.FileUploadDTO;
import com.gov.landcheck.file.dto.FileQueryDTO;
import com.gov.landcheck.file.dto.SubmitParseResult;

/**
 * 文件服务接口
 *
 * @author system
 * @date 2025/12/20
 */
public interface FileService {

    /**
     * 单文件上传：同步完成 GridFS 存储与业务占位，文件状态进入 WAITING_POST_PROCESS；
     * 缩略图/上传记录/状态推进等后处理在 UploadThreadPool 异步完成。
     *
     * @param uploadDTO 单文件上传请求
     * @return 含 fileId 与 postProcessTaskId（不等待后处理完成）
     */
    AjaxJson uploadFile(FileUploadDTO uploadDTO);

    /**
     * 根据ID获取文件（包含文件内容）
     *
     * @param id 文件ID
     * @return 文件记录（包含文件内容）
     */
    Optional<FileRecord> getById(String id);

    /**
     * 根据ID获取文件信息（不包含文件内容）
     *
     * @param id 文件ID
     * @return 文件记录（不包含文件内容）
     */
    Optional<FileRecord> getFileInfoById(String id);

    /**
     * 可解析且无进行中任务则提交：校验类型为合同/实测报告、状态为 WAITING_PARSE 或 PARSE_COMPLETE、
     * 无 PENDING/RUNNING 的 ParseJob、GridFS 存在后提交解析任务。供手动解析接口与批量上传后自动解析复用。
     *
     * @param fileRecord 文件记录（不可为 null）
     * @return 提交结果，含是否已提交、任务ID 或错误信息
     */
    SubmitParseResult submitParseIfEligible(FileRecord fileRecord);

    /**
     * 解析文件
     *
     * @param fileId 文件ID
     * @return 解析结果
     */
    AjaxJson parseFile(String fileId);

    /**
     * 获取解析状态
     *
     * @param fileId 文件ID
     * @return 解析状态信息
     */
    AjaxJson getParseStatus(String fileId);

    /**
     * 取消解析任务
     *
     * @param fileId 文件ID
     * @param reason 取消原因
     * @return 取消结果
     */
    AjaxJson cancelParseTask(String fileId, String reason);

    /**
     * 根据项目ID获取项目的所有文件信息
     *
     * @param projectId 项目ID
     * @return 文件列表
     */
    List<FileRecord> getFilesByProjectId(Long projectId);

    /**
     * 根据项目ID和归档夹ID获取该归档下的文件；archiveId 为 null 时返回未归档文件（archive_id 为空的文件，如老数据）
     *
     * @param projectId 项目ID
     * @param archiveId 归档夹ID，null 表示未归档
     * @return 文件列表
     */
    List<FileRecord> getFilesByProjectIdAndArchiveId(Long projectId, Long archiveId);

    /**
     * 删除文件及其所有相关数据
     * 包括：FileRecord、ParseJob、ParsedDataHeader、ParsedDataItem、OCRExecutionResult
     * 以及业务数据（ContractInfo、SurveyReportInfo、RoomInfo等）
     * 同时删除GridFS中的所有相关文件
     *
     * @param fileId 文件ID
     * @return 删除结果
     */
    AjaxJson deleteFile(String fileId);

    /**
     * 批量删除文件及其所有相关数据
     * 包括：FileRecord、ParseJob、ParsedDataHeader、ParsedDataItem、OCRExecutionResult
     * 以及业务数据（ContractInfo、SurveyReportInfo、RoomInfo等）
     * 同时删除GridFS中的所有相关文件
     *
     * @param fileIds 文件ID列表
     * @return 批量删除结果
     */
    AjaxJson batchDeleteFiles(List<String> fileIds);

    /**
     * 通用文件查询
     * 根据查询条件动态构建查询语句，支持分页和排序
     *
     * @param queryDTO 查询条件DTO
     * @return 分页查询结果
     */
    AjaxJson queryFiles(FileQueryDTO queryDTO);

    /**
     * 校验 GridFS 文件 ID 是否已登记在 FileRecord（主文件或缩略图）。
     */
    boolean isRegisteredGridFsId(String gridFsId);

    /**
     * GridFS 缺失等场景下，按与删除相同的顺序级联清理文件相关数据。
     */
    void cleanupFileRecordWhenGridFsMissing(FileRecord fileRecord);

}
