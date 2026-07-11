package com.gov.landcheck.core.service;

import java.util.List;

import com.gov.landcheck.core.bo.R.AjaxJson;
import com.gov.landcheck.core.bo.dto.CreateArchiveDTO;
import com.gov.landcheck.core.bo.dto.UpdateArchiveDTO;
import com.gov.landcheck.core.bo.entity.FileArchive;
import com.gov.landcheck.core.enums.FileContextType;

/**
 * 归档文件夹服务接口
 * 负责项目下默认归档夹的创建与查询。
 *
 * @author system
 * @date 2026/02/05
 */
public interface IFileArchiveService {

    /**
     * 确保项目下存在默认归档夹，不存在则创建
     *
     * @param projectId 项目ID
     */
    void ensureDefaultArchivesForProject(Long projectId);

    /**
     * 根据项目ID和归档类型获取归档夹ID
     *
     * @param projectId 项目ID
     * @param kind      归档类型（与 FileContextType 对应）
     * @return 归档夹ID，不存在时先创建再返回
     */
    Long getArchiveIdByProjectAndKind(Long projectId, FileContextType kind);

    /**
     * 校验归档夹是否存在且属于指定项目
     *
     * @param archiveId 归档夹ID
     * @param projectId 项目ID
     * @return 校验通过时返回该归档夹ID，否则返回 null
     */
    Long getArchiveIdIfBelongsToProject(Long archiveId, Long projectId);

    /**
     * 查询项目下所有归档夹（含默认归档夹 + 用户自定义），按 sortOrder、id 排序
     *
     * @param projectId 项目ID
     * @return 归档夹列表
     */
    List<FileArchive> listByProjectId(Long projectId);

    /**
     * 在项目下新建用户自定义归档夹。
     *
     * @param dto 包含 projectId、name、sortOrder
     * @return 成功时带 data 的 AjaxJson，失败时为错误码与提示信息
     */
    AjaxJson createArchive(CreateArchiveDTO dto);

    /**
     * 删除归档夹。
     *
     */
    AjaxJson deleteArchive(Long projectId, Long archiveId);

    /**
     * 更新归档夹，仅可修改名称与排序值。
     * 校验归档夹存在且属于指定项目；同项目下名称不重复（排除自身）。
     *
     */
    AjaxJson updateArchive(UpdateArchiveDTO dto);
}
