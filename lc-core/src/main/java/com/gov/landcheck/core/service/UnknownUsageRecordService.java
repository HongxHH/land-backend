package com.gov.landcheck.core.service;

import java.util.List;

import com.gov.landcheck.core.bo.R.AjaxJson;
import com.gov.landcheck.core.bo.entity.UnknownUsageRecord;
import com.gov.landcheck.core.bo.vo.UnknownUsageRecordVO;

/**
 * 未知用途记录服务接口
 * 负责未知用途记录的管理
 *
 * @author system
 * @date 2025/01/21
 */
public interface UnknownUsageRecordService {

        /**
         * 记录未知用途
         * 如果已存在则更新出现次数，否则创建新记录
         *
         * @param usageName          未知用途名称
         * @param projectId          项目ID
         * @param fileRecordId       文件ID
         * @param surveyReportInfoId 实测报告ID
         * @param roomInfoId         房间信息ID
         * @return 保存后的记录
         */
        UnknownUsageRecord recordUnknownUsage(String usageName, Long projectId, Long fileRecordId,
                        Long surveyReportInfoId, Long roomInfoId);

        /**
         * 批量记录未知用途
         *
         * @param usageNames         未知用途名称列表
         * @param projectId          项目ID
         * @param fileRecordId       文件ID
         * @param surveyReportInfoId 实测报告ID
         * @param roomInfoIds        房间信息ID列表
         * @return 保存后的记录列表
         */
        List<UnknownUsageRecord> batchRecordUnknownUsages(List<String> usageNames, Long projectId,
                        Long fileRecordId, Long surveyReportInfoId,
                        List<Long> roomInfoIds);

        /**
         * 根据ID获取未知用途记录
         *
         * @param id 记录ID
         * @return 未知用途记录
         */
        UnknownUsageRecord getById(Long id);

        /**
         * 根据项目ID获取未知用途记录
         *
         * @param projectId 项目ID
         * @return 未知用途记录列表
         */
        List<UnknownUsageRecord> getByProjectId(Long projectId);

        /**
         * 根据文件ID获取未知用途记录
         *
         * @param fileRecordId 文件ID
         * @return 未知用途记录列表
         */
        List<UnknownUsageRecord> getByFileRecordId(Long fileRecordId);

        /**
         * 获取所有待处理的未知用途记录
         *
         * @return 待处理记录列表
         */
        List<UnknownUsageRecord> getPendingRecords();

        /**
         * 待处理未知用途（含最近一次来源文件名、项目名称）
         */
        List<UnknownUsageRecordVO> listPendingRecordVos();

        /**
         * 某项目未处理未知用途（含展示字段）
         */
        List<UnknownUsageRecordVO> listRecordVosByProjectId(Long projectId);

        /**
         * 按用途名称查询记录（跨项目、多文件可能多条）
         *
         * @param usageName 用途名称
         * @return 匹配的记录列表，可能为空
         */
        List<UnknownUsageRecord> listByUsageName(String usageName);

        /**
         * 删除未知用途记录
         *
         * @param id 记录ID
         */
        void deleteById(Long id);

        /**
         * 删除指定来源文件产生的全部未知用途记录（重新解析/校验前清理旧数据）。
         *
         * @param fileRecordId 文件记录 ID
         * @return 删除条数
         */
        long deleteByFileRecordId(Long fileRecordId);

        /**
         * 更新未知用途记录状态
         *
         * @param id           记录ID
         * @param status       状态（0待处理 1已处理 2已忽略）
         * @param handledBy    处理人
         * @param handleRemark 处理备注
         * @return 更新后的记录
         */
        UnknownUsageRecord updateStatus(Long id, Integer status, String handledBy, String handleRemark);

        void closePendingIfUsageNameMatches(String usageName, String handledBy, String handleRemark);

        /**
         * 基于未知用途创建新用途配置
         *
         * @param unknownUsageId 未知用途记录ID
         * @param usageCategory  用途类别
         * @param floorAreaType  面积类型
         * @param isRegex        是否使用正则
         * @param priority       优先级
         * 
         */
        AjaxJson createFromUnknownUsage(Long unknownUsageId, String usageCategory, String floorAreaType,
                        Integer isRegex,
                        Integer priority);

}