package com.gov.landcheck.core.service.impl;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.gov.landcheck.core.audit.AuditOperation;
import com.gov.landcheck.core.audit.OperationType;
import com.gov.landcheck.core.audit.TargetType;
import com.gov.landcheck.core.bo.R.AjaxJson;
import com.gov.landcheck.core.bo.dto.CreateArchiveDTO;
import com.gov.landcheck.core.bo.dto.UpdateArchiveDTO;
import com.gov.landcheck.core.bo.entity.FileArchive;
import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.common.MessageConstant;
import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.core.service.IFileArchiveService;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * 归档文件夹服务实现
 *
 * @author system
 * @date 2026/02/05
 */
@Service
@Slf4j
public class FileArchiveServiceImpl implements IFileArchiveService {

    private record DefaultArchiveSpec(FileContextType kind, String name, int sortOrder) {
    }

    private static final List<DefaultArchiveSpec> DEFAULT_ARCHIVES = List.of(
            new DefaultArchiveSpec(FileContextType.CONTRACT, "合同", 0),
            new DefaultArchiveSpec(FileContextType.SURVEY_REPORT, "实测报告", 1),
            new DefaultArchiveSpec(FileContextType.PLANNING_REVIEW, "规划复核表", 2),
            new DefaultArchiveSpec(FileContextType.CAPACITY_INDICATOR, "容量指标核查表", 3),
            new DefaultArchiveSpec(FileContextType.PROJECT_PARTY_SURVEY_SUMMARY, "项目方实测汇总表", 4),
            new DefaultArchiveSpec(FileContextType.OTHER, "其他未归档文件", 5));

    @Resource
    private MongoTemplate mongoTemplate;

    @Override
    public void ensureDefaultArchivesForProject(Long projectId) {
        if (projectId == null) {
            return;
        }
        for (DefaultArchiveSpec spec : DEFAULT_ARCHIVES) {
            Query q = buildDefaultArchiveQuery(projectId, spec.kind());
            if (mongoTemplate.findOne(q, FileArchive.class) != null) {
                continue;
            }
            FileArchive a = FileArchive.builder()
                    .projectId(projectId)
                    .kind(spec.kind())
                    .name(spec.name())
                    .sortOrder(spec.sortOrder())
                    .isDefault(true)
                    .build();
            a.preSave();
            mongoTemplate.save(a);
            log.debug("已补充默认归档夹: projectId={}, kind={}, sortOrder={}", projectId, spec.kind(), spec.sortOrder());
        }
    }

    private Query buildDefaultArchiveQuery(Long projectId, FileContextType kind) {
        Criteria defaultOnly = new Criteria().orOperator(
                Criteria.where("is_default").is(true),
                Criteria.where("is_default").exists(false));
        return new Query(Criteria.where("project_id").is(projectId).and("kind").is(kind).andOperator(defaultOnly));
    }

    @Override
    public Long getArchiveIdByProjectAndKind(Long projectId, FileContextType kind) {
        if (projectId == null || kind == null) {
            return null;
        }
        ensureDefaultArchivesForProject(projectId);
        Query query = buildDefaultArchiveQuery(projectId, kind);
        FileArchive one = mongoTemplate.findOne(query, FileArchive.class);
        return one != null ? one.getId() : null;
    }

    @Override
    public Long getArchiveIdIfBelongsToProject(Long archiveId, Long projectId) {
        if (archiveId == null || projectId == null) {
            return null;
        }
        Query query = new Query(
                Criteria.where("_id").is(archiveId).and("project_id").is(projectId));
        FileArchive one = mongoTemplate.findOne(query, FileArchive.class);
        return one != null ? one.getId() : null;
    }

    @Override
    public List<FileArchive> listByProjectId(Long projectId) {
        if (projectId == null) {
            return List.of();
        }
        ensureDefaultArchivesForProject(projectId);
        Query query = new Query(Criteria.where("project_id").is(projectId))
                .with(Sort.by(Sort.Direction.ASC, "sort_order").and(Sort.by(Sort.Direction.ASC, "_id")));
        return mongoTemplate.find(query, FileArchive.class);
    }

    @Override
    @AuditOperation(operation = OperationType.CREATE, targetType = TargetType.FILE_ARCHIVE)
    public AjaxJson createArchive(CreateArchiveDTO dto) {
        if (dto == null) {
            return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "请求参数不能为空");
        }
        Long projectId = dto.getProjectId();
        String name = dto.getName();
        if (projectId == null) {
            return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "项目ID不能为空");
        }
        if (name == null || name.isBlank()) {
            return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "归档夹名称不能为空");
        }
        String trimmedName = name.trim();
        ensureDefaultArchivesForProject(projectId);

        Query nameQuery = new Query(Criteria.where("project_id").is(projectId).and("name").is(trimmedName));
        if (mongoTemplate.exists(nameQuery, FileArchive.class)) {
            return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "该项目下已存在同名归档夹，请使用其他名称");
        }

        Integer sortOrder = dto.getSortOrder();
        if (sortOrder == null) {
            Query q = new Query(Criteria.where("project_id").is(projectId))
                    .with(Sort.by(Sort.Direction.DESC, "sort_order")).limit(1);
            FileArchive last = mongoTemplate.findOne(q, FileArchive.class);
            sortOrder = last != null && last.getSortOrder() != null ? last.getSortOrder() + 1 : 100;
        }

        FileArchive a = new FileArchive();
        a.setProjectId(projectId);
        a.setName(trimmedName);
        a.setKind(FileContextType.OTHER);
        a.setIsDefault(false);
        a.setSortOrder(sortOrder);
        a.preSave();
        mongoTemplate.save(a);
        log.debug("新建归档夹: projectId={}, name={}, archiveId={}", projectId, trimmedName, a.getId());
        return AjaxJson.getSuccess("新建归档夹成功").setData(a);
    }

    @Override
    @AuditOperation(operation = OperationType.DELETE, targetType = TargetType.FILE_ARCHIVE, idParam = "p1", projectIdParam = "p0")
    public AjaxJson deleteArchive(Long projectId, Long archiveId) {
        if (projectId == null || archiveId == null) {
            return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "项目ID或归档夹ID不能为空");
        }
        FileArchive archive = mongoTemplate.findOne(
                new Query(Criteria.where("_id").is(archiveId).and("project_id").is(projectId)),
                FileArchive.class);
        if (archive == null) {
            return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "归档夹不存在或不属于当前项目");
        }
        if (!Boolean.FALSE.equals(archive.getIsDefault())) {
            return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "默认归档夹不可删除");
        }
        long fileCount = mongoTemplate.count(
                new Query(Criteria.where("archive_id").is(archiveId)),
                FileRecord.class);
        if (fileCount > 0) {
            return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "该归档夹下仍有文件，请先删除或移出所有文件后再删除归档夹");
        }
        mongoTemplate.remove(archive);
        log.debug("删除归档夹: projectId={}, archiveId={}", projectId, archiveId);
        return AjaxJson.getSuccess("删除归档夹成功");
    }

    @Override
    @AuditOperation(operation = OperationType.UPDATE, targetType = TargetType.FILE_ARCHIVE, idParam = "p0.archiveId", projectIdParam = "p0.projectId")
    public AjaxJson updateArchive(UpdateArchiveDTO dto) {
        Long archiveId = dto.getArchiveId();
        Long projectId = dto.getProjectId();
        FileArchive archive = mongoTemplate.findOne(
                new Query(Criteria.where("_id").is(archiveId).and("project_id").is(projectId)),
                FileArchive.class);
        if (archive == null) {
            return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "归档夹不存在或不属于当前项目");
        }

        if (dto.getName() != null && !dto.getName().isBlank()) {
            String trimmedName = dto.getName().trim();
            Query nameQuery = new Query(
                    Criteria.where("project_id").is(projectId)
                            .and("name").is(trimmedName)
                            .and("_id").ne(archiveId));
            if (mongoTemplate.exists(nameQuery, FileArchive.class)) {
                return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "该项目下已存在同名归档夹，请使用其他名称");
            }
            archive.setName(trimmedName);
        }
        if (dto.getSortOrder() != null) {
            archive.setSortOrder(dto.getSortOrder());
        }

        mongoTemplate.save(archive);
        log.debug("更新归档夹: projectId={}, archiveId={}", projectId, archiveId);
        return AjaxJson.getSuccess("更新归档夹成功").setData(archive);
    }

}
