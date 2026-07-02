package com.gov.landcheck.core.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.gov.landcheck.core.audit.AuditOperation;
import com.gov.landcheck.core.audit.OperationType;
import com.gov.landcheck.core.audit.TargetType;
import com.gov.landcheck.core.bo.dto.UsageConfigRelatedFileQueryResultDTO;
import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.Project;
import com.gov.landcheck.core.bo.entity.RoomInfo;
import com.gov.landcheck.core.bo.entity.UsageConfig;
import com.gov.landcheck.core.bo.vo.UsageConfigRelatedFileVO;
import com.gov.landcheck.core.service.UsageConfigService;

import lombok.extern.slf4j.Slf4j;

/**
 * 用途配置服务实现类
 *
 * @author system
 * @date 2025/01/21
 */
@Slf4j
@Service
public class UsageConfigServiceImpl implements UsageConfigService {

    private static final int MAX_RELATED_FILE_PAGE_SIZE = 50;
    private static final int MAX_SAMPLE_USAGES = 3;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public UsageConfig matchUsageConfig(String usageName) {
        if (usageName == null || usageName.trim().isEmpty()) {
            return null;
        }
        UsageConfig matched = resolveMatchedConfig(usageName, getAllEnabledConfigs());
        if (matched == null) {
            log.debug("用途 '{}' 未匹配到任何配置", usageName);
        }
        return matched;
    }

    @Override
    public List<UsageConfig> getAllEnabledConfigs() {
        Query query = new Query(Criteria.where("status").is(1));
        List<UsageConfig> configs = mongoTemplate.find(query, UsageConfig.class);

        return configs.stream()
                .sorted(Comparator.comparing(UsageConfig::getPriority, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    @Override
    public UsageConfig getById(Long id) {
        return mongoTemplate.findById(id, UsageConfig.class);
    }

    @Override
    @AuditOperation(operation = OperationType.CREATE, targetType = TargetType.USAGE_CONFIG)
    public UsageConfig create(UsageConfig usageConfig) {
        if (usageConfig != null) {
            usageConfig.setId(null);
        }
        return save(usageConfig);
    }

    @Override
    @AuditOperation(operation = OperationType.UPDATE, targetType = TargetType.USAGE_CONFIG, idParam = "p0.id")
    public UsageConfig update(UsageConfig usageConfig) {
        return save(usageConfig);
    }

    @Override
    public UsageConfig save(UsageConfig usageConfig) {
        usageConfig.preSave();
        return mongoTemplate.save(usageConfig);
    }

    @Override
    public List<UsageConfig> saveAll(List<UsageConfig> usageConfigs) {
        return usageConfigs.stream()
                .map(this::save)
                .collect(Collectors.toList());
    }

    @Override
    @AuditOperation(operation = OperationType.DELETE, targetType = TargetType.USAGE_CONFIG, idParam = "p0")
    public void deleteById(Long id) {
        Query query = new Query(Criteria.where("_id").is(id));
        mongoTemplate.remove(query, UsageConfig.class);
    }

    @Override
    public List<UsageConfig> getByUsageCategory(String usageCategory) {
        Query query = new Query(Criteria.where("usage_category").is(usageCategory));
        return mongoTemplate.find(query, UsageConfig.class);
    }

    @Override
    public List<UsageConfig> getByFloorAreaType(String floorAreaType) {
        Query query = new Query(Criteria.where("floor_area_type").is(floorAreaType)
                .and("status").is(1));
        return mongoTemplate.find(query, UsageConfig.class);
    }

    @Override
    public boolean existsByUsageName(String usageName) {
        Query query = new Query(Criteria.where("usage_pattern").is(usageName).and("status").is(1));
        return mongoTemplate.exists(query, UsageConfig.class);
    }

    @Override
    public UsageConfig findByUsageName(String usageName) {
        Query query = new Query(Criteria.where("usage_pattern").is(usageName).and("status").is(1));
        return mongoTemplate.findOne(query, UsageConfig.class);
    }

    @Override
    public UsageConfigRelatedFileQueryResultDTO listRelatedFiles(UsageConfig usageConfig, Integer pageNum,
            Integer pageSize, String keyword) {
        UsageConfigRelatedFileQueryResultDTO result = emptyRelatedFileResult(pageNum, pageSize);
        if (usageConfig == null || usageConfig.getId() == null) {
            return result;
        }

        String pattern = usageConfig.getUsagePattern();
        if (!StringUtils.hasText(pattern)) {
            return result;
        }

        int resolvedPageNum = pageNum != null && pageNum > 0 ? pageNum : 1;
        int resolvedPageSize = pageSize != null && pageSize > 0 ? Math.min(pageSize, MAX_RELATED_FILE_PAGE_SIZE) : 10;
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase(Locale.ROOT) : null;

        List<UsageConfig> enabledConfigs = getAllEnabledConfigs();
        List<RoomInfo> candidates = findCandidateRooms(pattern.trim(), usageConfig.getIsRegex());
        Map<Long, FileAggregation> aggregationMap = aggregateMatchedRooms(candidates, usageConfig.getId(),
                enabledConfigs);
        if (aggregationMap.isEmpty()) {
            return result;
        }

        Map<Long, FileRecord> fileMap = loadFileRecordsByIds(aggregationMap.keySet());
        Map<Long, Project> projectMap = loadProjectsByIds(aggregationMap.values().stream()
                .map(agg -> agg.projectId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));

        List<UsageConfigRelatedFileVO> allRows = buildRelatedFileRows(aggregationMap, fileMap, projectMap);
        if (normalizedKeyword != null) {
            allRows = allRows.stream()
                    .filter(row -> matchesRelatedFileKeyword(row, normalizedKeyword))
                    .collect(Collectors.toList());
        }

        allRows.sort(Comparator
                .comparing(UsageConfigRelatedFileVO::getUploadTime, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(UsageConfigRelatedFileVO::getFileRecordId,
                        Comparator.nullsLast(Comparator.reverseOrder())));

        long totalMatchedRooms = normalizedKeyword != null
                ? allRows.stream().mapToLong(row -> row.getMatchedRoomCount() != null ? row.getMatchedRoomCount() : 0)
                        .sum()
                : aggregationMap.values().stream().mapToLong(agg -> agg.matchedRoomCount).sum();

        int fromIndex = Math.min((resolvedPageNum - 1) * resolvedPageSize, allRows.size());
        int toIndex = Math.min(fromIndex + resolvedPageSize, allRows.size());
        List<UsageConfigRelatedFileVO> pageRows = fromIndex >= allRows.size()
                ? List.of()
                : allRows.subList(fromIndex, toIndex);

        result.setRecords(pageRows);
        result.setCurrent(resolvedPageNum);
        result.setSize(resolvedPageSize);
        result.setTotal((long) allRows.size());
        result.setPages(resolvedPageSize == 0 ? 0 : (int) Math.ceil((double) allRows.size() / resolvedPageSize));
        result.setTotalMatchedRooms(totalMatchedRooms);
        return result;
    }

    private UsageConfig resolveMatchedConfig(String usageName, List<UsageConfig> enabledConfigs) {
        for (UsageConfig config : enabledConfigs) {
            if (matchesPattern(usageName, config)) {
                return config;
            }
        }
        return null;
    }

    private UsageConfigRelatedFileQueryResultDTO emptyRelatedFileResult(Integer pageNum, Integer pageSize) {
        UsageConfigRelatedFileQueryResultDTO result = new UsageConfigRelatedFileQueryResultDTO();
        result.setRecords(List.of());
        result.setCurrent(pageNum != null && pageNum > 0 ? pageNum : 1);
        result.setSize(pageSize != null && pageSize > 0 ? Math.min(pageSize, MAX_RELATED_FILE_PAGE_SIZE) : 10);
        result.setTotal(0L);
        result.setPages(0);
        result.setTotalMatchedRooms(0L);
        return result;
    }

    /**
     * Mongo Criteria 同一字段只能出现一次条件，非空校验合并进 regex。
     */
    private List<RoomInfo> findCandidateRooms(String pattern, Integer isRegex) {
        String regex = isRegex != null && isRegex == 1
                ? pattern
                : ".*" + Pattern.quote(pattern) + ".*";
        Query query = new Query(Criteria.where("room_usage").regex(regex));
        query.fields().include("file_record_id", "project_id", "room_usage");
        return mongoTemplate.find(query, RoomInfo.class);
    }

    private Map<Long, FileAggregation> aggregateMatchedRooms(List<RoomInfo> candidates, Long targetConfigId,
            List<UsageConfig> enabledConfigs) {
        Map<String, UsageConfig> usageMatchCache = new HashMap<>();
        Map<Long, FileAggregation> aggregationMap = new HashMap<>();

        for (RoomInfo room : candidates) {
            if (room.getFileRecordId() == null || !StringUtils.hasText(room.getRoomUsage())) {
                continue;
            }
            UsageConfig matched = usageMatchCache.computeIfAbsent(room.getRoomUsage(),
                    usage -> resolveMatchedConfig(usage, enabledConfigs));
            if (matched == null || !targetConfigId.equals(matched.getId())) {
                continue;
            }
            FileAggregation agg = aggregationMap.computeIfAbsent(room.getFileRecordId(), id -> {
                FileAggregation created = new FileAggregation();
                created.fileRecordId = id;
                created.projectId = room.getProjectId();
                return created;
            });
            agg.matchedRoomCount++;
            if (agg.sampleUsages.size() < MAX_SAMPLE_USAGES) {
                agg.sampleUsages.add(room.getRoomUsage().trim());
            }
            if (agg.projectId == null && room.getProjectId() != null) {
                agg.projectId = room.getProjectId();
            }
        }
        return aggregationMap;
    }

    private Map<Long, FileRecord> loadFileRecordsByIds(Set<Long> fileRecordIds) {
        if (fileRecordIds == null || fileRecordIds.isEmpty()) {
            return Map.of();
        }
        Query query = new Query(Criteria.where("_id").in(fileRecordIds));
        query.fields().include("_id", "project_id", "original_name", "upload_time", "file_context_type");
        Map<Long, FileRecord> fileMap = new HashMap<>();
        for (FileRecord fileRecord : mongoTemplate.find(query, FileRecord.class)) {
            if (fileRecord.getId() != null) {
                fileMap.put(fileRecord.getId(), fileRecord);
            }
        }
        return fileMap;
    }

    private Map<Long, Project> loadProjectsByIds(Set<Long> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            return Map.of();
        }
        Query query = new Query(Criteria.where("_id").in(projectIds));
        query.fields().include("_id", "project_name");
        Map<Long, Project> projectMap = new HashMap<>();
        for (Project project : mongoTemplate.find(query, Project.class)) {
            if (project.getId() != null) {
                projectMap.put(project.getId(), project);
            }
        }
        return projectMap;
    }

    private List<UsageConfigRelatedFileVO> buildRelatedFileRows(Map<Long, FileAggregation> aggregationMap,
            Map<Long, FileRecord> fileMap, Map<Long, Project> projectMap) {
        List<UsageConfigRelatedFileVO> rows = new ArrayList<>(aggregationMap.size());
        for (FileAggregation agg : aggregationMap.values()) {
            UsageConfigRelatedFileVO vo = new UsageConfigRelatedFileVO();
            vo.setFileRecordId(agg.fileRecordId);
            vo.setMatchedRoomCount(agg.matchedRoomCount);
            vo.setSampleRoomUsages(List.copyOf(agg.sampleUsages));

            FileRecord fileRecord = fileMap.get(agg.fileRecordId);
            Long projectId = agg.projectId;
            if (fileRecord != null) {
                vo.setOriginalName(fileRecord.getOriginalName());
                vo.setUploadTime(fileRecord.getUploadTime());
                if (fileRecord.getFileContextType() != null) {
                    vo.setFileContextType(fileRecord.getFileContextType().name());
                }
                if (projectId == null) {
                    projectId = fileRecord.getProjectId();
                }
            }

            vo.setProjectId(projectId);
            Project project = projectId != null ? projectMap.get(projectId) : null;
            vo.setProjectName(project != null ? project.getProjectName() : null);
            rows.add(vo);
        }
        return rows;
    }

    private boolean matchesRelatedFileKeyword(UsageConfigRelatedFileVO row, String keyword) {
        String projectName = row.getProjectName() != null ? row.getProjectName().toLowerCase(Locale.ROOT) : "";
        String originalName = row.getOriginalName() != null ? row.getOriginalName().toLowerCase(Locale.ROOT) : "";
        return projectName.contains(keyword) || originalName.contains(keyword);
    }

    private boolean matchesPattern(String usageName, UsageConfig config) {
        String pattern = config.getUsagePattern();
        Integer isRegex = config.getIsRegex();

        if (pattern == null || pattern.trim().isEmpty()) {
            return false;
        }

        if (isRegex != null && isRegex == 1) {
            try {
                return Pattern.compile(pattern).matcher(usageName).find();
            } catch (Exception e) {
                log.warn("正则表达式 '{}' 编译失败: {}", pattern, e.getMessage());
                return false;
            }
        }
        return usageName.contains(pattern);
    }

    private static final class FileAggregation {
        private Long fileRecordId;
        private Long projectId;
        private int matchedRoomCount;
        private final LinkedHashSet<String> sampleUsages = new LinkedHashSet<>();
    }
}
