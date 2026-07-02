package com.gov.landcheck.core.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.gov.landcheck.core.bo.R.AjaxJson;
import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.Project;
import com.gov.landcheck.core.bo.entity.UnknownUsageRecord;
import com.gov.landcheck.core.bo.entity.UsageConfig;
import com.gov.landcheck.core.bo.vo.UnknownUsageRecordVO;
import com.gov.landcheck.core.service.UnknownUsageRecordService;
import com.gov.landcheck.core.service.UsageConfigService;

import lombok.extern.slf4j.Slf4j;

/**
 * 未知用途记录服务实现类
 *
 * @author system
 * @date 2025/01/21
 */
@Slf4j
@Service
public class UnknownUsageRecordServiceImpl implements UnknownUsageRecordService {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private UsageConfigService usageConfigService;

    @Override
    public UnknownUsageRecord recordUnknownUsage(String usageName, Long projectId, Long fileRecordId,
            Long surveyReportInfoId, Long roomInfoId) {
        if (usageName == null || usageName.trim().isEmpty()) {
            log.debug("跳过空白用途名的未知用途记录: projectId={}, fileRecordId={}", projectId, fileRecordId);
            return null;
        }
        // 按项目 + 用途名 + 来源文件 唯一；同文件内多次出现只累加 occurrence_count
        Query query = new Query(Criteria.where("usage_name").is(usageName)
                .and("project_id").is(projectId)
                .and("file_record_id").is(fileRecordId));
        UnknownUsageRecord existingRecord = mongoTemplate.findOne(query, UnknownUsageRecord.class);

        if (existingRecord != null) {
            existingRecord.setOccurrenceCount(Objects.requireNonNullElse(existingRecord.getOccurrenceCount(), 0) + 1);
            existingRecord.setSurveyReportInfoId(surveyReportInfoId);
            existingRecord.setRoomInfoId(roomInfoId);

            existingRecord.preSave();
            return mongoTemplate.save(existingRecord);
        } else {
            // 创建新记录
            UnknownUsageRecord newRecord = new UnknownUsageRecord();
            newRecord.setUsageName(usageName);
            newRecord.setProjectId(projectId);
            newRecord.setFileRecordId(fileRecordId);
            newRecord.setSurveyReportInfoId(surveyReportInfoId);
            newRecord.setRoomInfoId(roomInfoId);
            newRecord.setOccurrenceCount(1);
            newRecord.setStatus(0); // 待处理

            newRecord.preSave();
            return mongoTemplate.save(newRecord);
        }
    }

    @Override
    public List<UnknownUsageRecord> batchRecordUnknownUsages(List<String> usageNames, Long projectId,
            Long fileRecordId, Long surveyReportInfoId,
            List<Long> roomInfoIds) {
        List<UnknownUsageRecord> records = new ArrayList<>();

        for (int i = 0; i < usageNames.size(); i++) {
            String usageName = usageNames.get(i);
            Long roomInfoId = i < roomInfoIds.size() ? roomInfoIds.get(i) : null;

            UnknownUsageRecord record = recordUnknownUsage(usageName, projectId, fileRecordId,
                    surveyReportInfoId, roomInfoId);
            records.add(record);
        }

        return records;
    }

    @Override
    public UnknownUsageRecord getById(Long id) {
        return mongoTemplate.findById(id, UnknownUsageRecord.class);
    }

    @Override
    public List<UnknownUsageRecord> getByProjectId(Long projectId) {
        Query query = new Query(Criteria.where("project_id").is(projectId).and("status").is(0));
        return mongoTemplate.find(query, UnknownUsageRecord.class);
    }

    @Override
    public List<UnknownUsageRecord> getByFileRecordId(Long fileRecordId) {
        Query query = new Query(Criteria.where("file_record_id").is(fileRecordId));
        return mongoTemplate.find(query, UnknownUsageRecord.class);
    }

    @Override
    public List<UnknownUsageRecord> getPendingRecords() {
        Query query = new Query(Criteria.where("status").is(0));
        return mongoTemplate.find(query, UnknownUsageRecord.class);
    }

    @Override
    public List<UnknownUsageRecordVO> listPendingRecordVos() {
        return toRecordVos(getPendingRecords());
    }

    @Override
    public List<UnknownUsageRecordVO> listRecordVosByProjectId(Long projectId) {
        return toRecordVos(getByProjectId(projectId));
    }

    private List<UnknownUsageRecordVO> toRecordVos(List<UnknownUsageRecord> records) {
        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> fileIds = records.stream()
                .map(UnknownUsageRecord::getFileRecordId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        Set<Long> projectIds = records.stream()
                .map(UnknownUsageRecord::getProjectId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));

        Map<Long, FileRecord> fileMap = new HashMap<>();
        if (!fileIds.isEmpty()) {
            Query fq = new Query(Criteria.where("_id").in(fileIds));
            for (FileRecord fr : mongoTemplate.find(fq, FileRecord.class)) {
                if (fr.getId() != null) {
                    fileMap.put(fr.getId(), fr);
                }
            }
        }
        Map<Long, Project> projectMap = new HashMap<>();
        if (!projectIds.isEmpty()) {
            Query pq = new Query(Criteria.where("_id").in(projectIds));
            for (Project p : mongoTemplate.find(pq, Project.class)) {
                if (p.getId() != null) {
                    projectMap.put(p.getId(), p);
                }
            }
        }

        List<UnknownUsageRecordVO> vos = new ArrayList<>(records.size());
        for (UnknownUsageRecord r : records) {
            UnknownUsageRecordVO vo = new UnknownUsageRecordVO();
            vo.setId(r.getId());
            vo.setUsageName(r.getUsageName());
            vo.setProjectId(r.getProjectId());
            vo.setFileRecordId(r.getFileRecordId());
            vo.setRoomInfoId(r.getRoomInfoId());
            vo.setSurveyReportInfoId(r.getSurveyReportInfoId());
            vo.setOccurrenceCount(r.getOccurrenceCount());
            vo.setStatus(r.getStatus());
            vo.setHandledBy(r.getHandledBy());
            vo.setHandleRemark(r.getHandleRemark());
            vo.setCreateTime(r.getCreateTime());
            vo.setUpdateTime(r.getUpdateTime());

            FileRecord fr = r.getFileRecordId() != null ? fileMap.get(r.getFileRecordId()) : null;
            vo.setRecentFileName(fr != null ? fr.getOriginalName() : null);

            Project p = r.getProjectId() != null ? projectMap.get(r.getProjectId()) : null;
            vo.setRecentProjectName(p != null ? p.getProjectName() : null);

            vos.add(vo);
        }
        return vos;
    }

    @Override
    public List<UnknownUsageRecord> listByUsageName(String usageName) {
        Query query = new Query(Criteria.where("usage_name").is(usageName));
        return mongoTemplate.find(query, UnknownUsageRecord.class);
    }

    @Override
    public void deleteById(Long id) {
        Query query = new Query(Criteria.where("_id").is(id));
        mongoTemplate.remove(query, UnknownUsageRecord.class);
    }

    @Override
    public long deleteByFileRecordId(Long fileRecordId) {
        if (fileRecordId == null) {
            return 0;
        }
        Query query = new Query(Criteria.where("file_record_id").is(fileRecordId));
        return mongoTemplate.remove(query, UnknownUsageRecord.class).getDeletedCount();
    }

    @Override
    public UnknownUsageRecord updateStatus(Long id, Integer status, String handledBy, String handleRemark) {
        UnknownUsageRecord record = getById(id);
        if (record == null) {
            return null;
        }

        record.setStatus(status);
        record.setHandledBy(handledBy);
        record.setHandleRemark(handleRemark);
        record.preSave();

        return mongoTemplate.save(record);
    }

    @Override
    public AjaxJson createFromUnknownUsage(Long unknownUsageId, String usageCategory, String floorAreaType,
            Integer isRegex, Integer priority) {
        // 根据未知用途ID查询记录
        UnknownUsageRecord unknownRecord = getById(unknownUsageId);
        if (unknownRecord == null) {
            return AjaxJson.getError("未知用途记录不存在");
        }

        // 检查是否已经处理过
        if (unknownRecord.getStatus() != null && unknownRecord.getStatus() == 1) {
            return AjaxJson.getError("该未知用途记录已被处理");
        }

        String usageName = unknownRecord.getUsageName();
        String handledBy = "admin";

        // 检查用途配置是否已存在
        if (usageConfigService.existsByUsageName(usageName)) {
            if (usageConfigService.matchUsageConfig(usageName) == null) {
                return AjaxJson.getError("用途配置已存在但无法匹配该名称，请检查匹配模式");
            }
            closePendingIfUsageNameMatches(usageName, handledBy, "该用途名称的配置已存在，已批量关闭同名待处理记录");
            return AjaxJson.getSuccess("该用途名称的配置已存在");
        }

        // 创建新的用途配置
        UsageConfig config = new UsageConfig();
        config.setUsagePattern(usageName);
        config.setUsageCategory(usageCategory);
        config.setFloorAreaType(floorAreaType);
        config.setIsRegex(isRegex);
        config.setPriority(priority);
        config.setStatus(1);
        config.setRemark("从未知用途创建");

        UsageConfig saved = usageConfigService.create(config);

        if (usageConfigService.matchUsageConfig(usageName) == null) {
            usageConfigService.deleteById(saved.getId());
            return AjaxJson.getError("用途配置无法匹配该名称，请检查匹配模式");
        }

        closePendingIfUsageNameMatches(usageName, handledBy, "已创建用途配置：" + saved.getId());

        return AjaxJson.getSuccessData(saved);
    }

    @Override
    public void closePendingIfUsageNameMatches(String usageName, String handledBy, String handleRemark) {
        if (usageName == null || usageConfigService.matchUsageConfig(usageName) == null) {
            return;
        }
        markAllPendingResolvedByUsageName(usageName, handledBy, handleRemark);
    }

    /**
     * 将所有待处理（status=0）且用途名与给定字符串完全相同的记录标为已处理（全局同名闭环）。
     */
    private void markAllPendingResolvedByUsageName(String usageName, String handledBy, String handleRemark) {
        if (usageName == null) {
            return;
        }
        Query query = new Query(Criteria.where("usage_name").is(usageName).and("status").is(0));
        Update update = new Update()
                .set("status", 1)
                .set("handled_by", handledBy)
                .set("handle_remark", handleRemark)
                .set("update_time", LocalDateTime.now());
        mongoTemplate.updateMulti(query, update, UnknownUsageRecord.class);
    }

}