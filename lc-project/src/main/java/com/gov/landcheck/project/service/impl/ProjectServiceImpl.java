package com.gov.landcheck.project.service.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.MongoTransactionException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.gov.landcheck.core.audit.AuditOperation;
import com.gov.landcheck.core.audit.FileOperationAuthorization;
import com.gov.landcheck.core.audit.OperationType;
import com.gov.landcheck.core.audit.OperatorContext;
import com.gov.landcheck.core.audit.TargetType;
import com.gov.landcheck.core.bo.R.AjaxJson;
import com.gov.landcheck.core.bo.entity.ContractInfo;
import com.gov.landcheck.core.bo.entity.FileArchive;
import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.LandParcel;
import com.gov.landcheck.core.bo.entity.OCRExecutionResult;
import com.gov.landcheck.core.bo.entity.ParseJob;
import com.gov.landcheck.core.bo.entity.ParsedDataHeader;
import com.gov.landcheck.core.bo.entity.ParsedDataItem;
import com.gov.landcheck.core.bo.entity.PlanningReviewForm;
import com.gov.landcheck.core.bo.entity.PlanningReviewRow;
import com.gov.landcheck.core.bo.entity.Project;
import com.gov.landcheck.core.bo.entity.ProjectPartySurveySummaryForm;
import com.gov.landcheck.core.bo.entity.RoomInfo;
import com.gov.landcheck.core.bo.entity.SurveyReportInfo;
import com.gov.landcheck.core.bo.entity.UnknownUsageRecord;
import com.gov.landcheck.core.config.cache.model.CacheLoadOptionsFactory;
import com.gov.landcheck.core.config.cache.service.CacheInvalidationService;
import com.gov.landcheck.core.config.cache.service.CacheOpsService;
import com.gov.landcheck.core.config.query.MongoQueryBuilder;
import com.gov.landcheck.core.enums.FileStateEnum;
import com.gov.landcheck.core.enums.ParseJobStateEnum;
import com.gov.landcheck.core.enums.ProjectStatusEnum;
import com.gov.landcheck.core.enums.SurveyValidationStatusEnum;
import com.gov.landcheck.core.service.IFileArchiveService;
import com.gov.landcheck.core.service.SurveyReportCalculationService;
import com.gov.landcheck.core.utils.ProjectTimeUtil;
import com.gov.landcheck.project.cache.ProjectCacheKeys;
import com.gov.landcheck.project.dto.ProjectDetailQueryResultDTO;
import com.gov.landcheck.project.dto.ProjectQueryDTO;
import com.gov.landcheck.project.dto.ProjectQueryResultDTO;
import com.gov.landcheck.project.dto.ProjectUpdateDTO;
import com.gov.landcheck.project.service.ContractAndLandParcelService;
import com.gov.landcheck.project.service.ProjectAreaComparisonService;
import com.gov.landcheck.project.service.ProjectService;
import com.gov.landcheck.project.utils.DynamicUpdateHelper;
import com.gov.landcheck.project.utils.PageSortSupport;
import com.gov.landcheck.project.vo.ContractProjectStatsVO;
import com.gov.landcheck.project.vo.ProjectDetailVO;
import com.gov.landcheck.project.vo.ProjectVO;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ProjectServiceImpl implements ProjectService {

    private static final long DOUBLE_DELETE_DELAY_MS = 500L;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private CacheOpsService cacheOpsService;

    @Autowired
    private CacheInvalidationService cacheInvalidationService;

    @Autowired
    private CacheLoadOptionsFactory cacheLoadOptionsFactory;

    @Autowired
    private ProjectCacheKeys projectCacheKeys;

    @Autowired
    private IFileArchiveService fileArchiveService;

    @Autowired
    private SurveyReportCalculationService calculationService;

    @Autowired
    private ContractAndLandParcelService contractAndLandParcelService;

    @Autowired
    private ProjectAreaComparisonService projectAreaComparisonService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Override
    public AjaxJson getProjectById(Long projectId) {
        if (projectId == null) {
            return AjaxJson.getError("项目ID不能为空");
        }
        String key = projectCacheKeys.projectById(projectId);
        ProjectVO projectVO = cacheOpsService.getOrLoad(key, cacheLoadOptionsFactory.byIdOptions(), () -> {
            Query query = new Query(Criteria.where("_id").is(projectId));
            Project project = mongoTemplate.findOne(query, Project.class);
            if (project == null) {
                return null;
            }
            ProjectVO vo = new ProjectVO();
            org.springframework.beans.BeanUtils.copyProperties(project, vo);
            return vo;
        });
        if (projectVO == null) {
            return AjaxJson.getError("Project not found");
        }
        return AjaxJson.getSuccessData(projectVO);
    }

    @Override
    public AjaxJson queryProjects(ProjectQueryDTO queryDTO) {
        ProjectQueryDTO normalized = queryDTO == null ? new ProjectQueryDTO() : queryDTO;
        String key = projectCacheKeys.queryProjects(normalized);
        ProjectQueryResultDTO result = cacheOpsService.getOrLoad(key, cacheLoadOptionsFactory.queryOptions(),
                () -> queryProjectsInternal(normalized));
        return AjaxJson.getSuccessData(result);
    }

    @Override
    public AjaxJson queryProjectDetails(ProjectQueryDTO queryDTO) {
        ProjectQueryDTO normalized = queryDTO == null ? new ProjectQueryDTO() : queryDTO;
        String key = projectCacheKeys.queryProjectDetails(normalized);
        ProjectDetailQueryResultDTO result = cacheOpsService.getOrLoad(key, cacheLoadOptionsFactory.queryOptions(),
                () -> queryProjectDetailsInternal(normalized));
        return AjaxJson.getSuccessData(result);
    }

    @Override
    @AuditOperation(operation = OperationType.CREATE, targetType = TargetType.PROJECT)
    public AjaxJson createProject(String projectName, String projectTime) {
        if (projectName == null || projectName.trim().isEmpty()) {
            return AjaxJson.getError("项目名称不能为空");
        }
        if (projectTime == null || projectTime.trim().isEmpty()) {
            return AjaxJson.getError("项目时间不能为空");
        }
        if (!ProjectTimeUtil.validateProjectTime(projectTime)) {
            return AjaxJson
                    .getError("Invalid project time format: " + ProjectTimeUtil.getProjectTimeFormatDescription());
        }
        String normalizedProjectTime = ProjectTimeUtil.normalizeToIsoDate(projectTime.trim());
        Query query = new Query(Criteria.where("project_name").is(projectName.trim()));
        Project existingProject = mongoTemplate.findOne(query, Project.class);
        if (existingProject != null) {
            return AjaxJson.getError("项目名称已存在");
        }
        Project project = new Project();
        project.setProjectName(projectName.trim());
        project.setProjectTime(normalizedProjectTime);
        project.setCreatedBy(OperatorContext.getOperatorId());
        try {
            Set<String> directKeys = new HashSet<>();
            directKeys.add(projectCacheKeys.allProjects());
            evictBeforeWrite(directKeys);
            project.preSave();
            Project savedProject = mongoTemplate.save(project);
            fileArchiveService.ensureDefaultArchivesForProject(savedProject.getId());
            evictAfterWrite(directKeys, projectCacheKeys.queryPattern());
            return AjaxJson.getSuccess("项目创建成功").setData(savedProject);
        } catch (Exception e) {
            log.error("Failed to create project", e);
            return AjaxJson.getError("Failed to create project");
        }
    }

    @Override
    @AuditOperation(operation = OperationType.UPDATE, targetType = TargetType.PROJECT, idParam = "p0.id", projectIdParam = "p0.id")
    public AjaxJson updateProject(ProjectUpdateDTO updateDTO) {
        Objects.requireNonNull(updateDTO, "updateDTO不能为空");
        try {
            Query query = new Query(Criteria.where("_id").is(updateDTO.getId()));
            Project existingProject = mongoTemplate.findOne(query, Project.class);
            if (existingProject == null) {
                return AjaxJson.getError("Project not found");
            }
            var update = DynamicUpdateHelper.buildDynamicUpdate(updateDTO);
            if (updateDTO.getProjectName() != null && !updateDTO.getProjectName().trim().isEmpty()) {
                Query nameQuery = new Query(Criteria.where("project_name").is(updateDTO.getProjectName().trim())
                        .and("_id").ne(updateDTO.getId()));
                if (mongoTemplate.exists(nameQuery, Project.class)) {
                    return AjaxJson.getError("Project name already exists");
                }
            }
            if (updateDTO.getProjectTime() != null && !updateDTO.getProjectTime().trim().isEmpty()) {
                if (!ProjectTimeUtil.validateProjectTime(updateDTO.getProjectTime())) {
                    return AjaxJson.getError(
                            "Invalid project time format: " + ProjectTimeUtil.getProjectTimeFormatDescription());
                }
                updateDTO.setProjectTime(ProjectTimeUtil.normalizeToIsoDate(updateDTO.getProjectTime().trim()));
            }
            Set<String> directKeys = new HashSet<>();
            directKeys.add(projectCacheKeys.projectById(updateDTO.getId()));
            directKeys.add(projectCacheKeys.allProjects());
            directKeys.add(projectCacheKeys.contractsByProject(updateDTO.getId()));
            directKeys.add(projectCacheKeys.parsedReportsByProject(updateDTO.getId()));

            evictBeforeWrite(directKeys);
            mongoTemplate.updateFirst(query, update, Project.class);
            evictAfterWrite(directKeys, projectCacheKeys.queryPattern());
            return AjaxJson.getSuccess("项目信息更新成功");
        } catch (Exception e) {
            log.error("Failed to update project", e);
            return AjaxJson.getError("Failed to update project");
        }
    }

    @Override
    public AjaxJson refreshProjectSurveyReports(Long projectId) {
        try {
            if (mongoTemplate.findOne(new Query(Criteria.where("_id").is(projectId)), Project.class) == null) {
                return AjaxJson.getError("Project not found");
            }
            List<SurveyReportInfo> surveyReports = mongoTemplate.find(
                    new Query(Criteria.where("project_id").is(projectId)), SurveyReportInfo.class);
            if (surveyReports.isEmpty()) {
                return AjaxJson.getSuccess("项目没有实测报告，无需刷新");
            }
            Set<String> directKeys = new HashSet<>();
            directKeys.add(projectCacheKeys.parsedReportsByProject(projectId));
            directKeys.add(projectCacheKeys.areaComparisonByProject(projectId));
            surveyReports.forEach(
                    sr -> directKeys.add(projectCacheKeys.roomsByProjectAndSurveyReport(projectId, sr.getId())));
            evictBeforeWrite(directKeys);
            surveyReports.forEach(this::applyCalculationToSurveyReport);
            evictAfterWrite(directKeys, projectCacheKeys.queryPattern());
            return AjaxJson.getSuccess("Survey report refresh completed, total: " + surveyReports.size());
        } catch (Exception e) {
            log.error("Failed to refresh survey reports", e);
            return AjaxJson.getError("Failed to refresh survey reports");
        }
    }

    @Override
    public AjaxJson queryAreaComparison(Long projectId) {
        if (projectId == null) {
            return AjaxJson.getError("项目ID不能为空");
        }
        Project project = mongoTemplate.findOne(new Query(Criteria.where("_id").is(projectId)), Project.class);
        if (project == null) {
            return AjaxJson.getError("Project not found");
        }
        String key = projectCacheKeys.areaComparisonByProject(projectId);
        var comparison = cacheOpsService.getOrLoad(key, cacheLoadOptionsFactory.byRelationOptions(),
                () -> projectAreaComparisonService.buildComparison(projectId));
        return AjaxJson.getSuccessData(comparison);
    }

    private void applyCalculationToSurveyReport(SurveyReportInfo surveyReport) {
        Query roomQuery = new Query(Criteria.where("survey_report_info_id").is(surveyReport.getId()));
        List<RoomInfo> roomInfos = mongoTemplate.find(roomQuery, RoomInfo.class);
        if (roomInfos.isEmpty()) {
            return;
        }
        com.gov.landcheck.core.common.ValidationResult result = calculationService.calculateAndValidate(surveyReport,
                roomInfos);
        surveyReport.setIsVerified(result.isValid() ? 1 : 0);
        surveyReport.setVerificationErrorReason(result.getErrorMessage());
        mongoTemplate.save(surveyReport);
        for (RoomInfo roomInfo : roomInfos) {
            mongoTemplate.save(roomInfo);
        }
    }

    @Override
    @AuditOperation(operation = OperationType.DELETE, targetType = TargetType.PROJECT, idParam = "p0")
    public AjaxJson deleteProject(Long projectId) {
        try {
            if (projectId == null) {
                return AjaxJson.getError("项目ID不能为空");
            }
            Project project = mongoTemplate.findById(projectId, Project.class);
            if (project == null) {
                return AjaxJson.getError("项目不存在或已删除");
            }
            if (!FileOperationAuthorization.canDeleteProject(project)) {
                return AjaxJson.getError(FileOperationAuthorization.denyReasonForProjectDelete());
            }
            String msg = checkProjectFilesAndTasksBeforeDelete(projectId);
            if (msg != null) {
                return AjaxJson.getError(msg);
            }
            Set<String> directKeys = new HashSet<>();
            directKeys.add(projectCacheKeys.projectById(projectId));
            directKeys.add(projectCacheKeys.allProjects());
            directKeys.add(projectCacheKeys.contractsByProject(projectId));
            directKeys.add(projectCacheKeys.parsedReportsByProject(projectId));
            directKeys.add(projectCacheKeys.areaComparisonByProject(projectId));
            directKeys.add(projectCacheKeys.planningReviewsByProject(projectId));
            directKeys.add(projectCacheKeys.projectPartySummariesByProject(projectId));
            for (SurveyReportInfo sr : mongoTemplate.find(new Query(Criteria.where("project_id").is(projectId)),
                    SurveyReportInfo.class)) {
                if (sr.getId() != null) {
                    directKeys.add(projectCacheKeys.roomsByProjectAndSurveyReport(projectId, sr.getId()));
                }
            }
            TransactionTemplate tpl = new TransactionTemplate(transactionManager);
            tpl.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
            tpl.executeWithoutResult(status -> deleteProjectPersistentData(projectId));
            evictBeforeWrite(directKeys);
            evictAfterWrite(directKeys, projectCacheKeys.queryPattern());
            return AjaxJson.getSuccess("项目删除成功");
        } catch (TransactionException | MongoTransactionException e) {
            log.error("删除项目失败（Mongo 事务不可用）, projectId={}", projectId, e);
            return AjaxJson.getError("删除项目失败：Mongo 事务不可用，请确认已启用副本集");
        } catch (Exception e) {
            log.error("删除项目失败, projectId={}", projectId, e);
            return AjaxJson.getError("删除项目失败");
        }
    }

    /**
     * 删除项目关联的持久化数据（Mongo 及归档夹记录），供事务内调用；失败时由外层事务回滚或外层 catch 处理。
     */
    private void deleteProjectPersistentData(Long projectId) {
        Query contractQuery = new Query(Criteria.where("project_id").is(projectId));
        List<ContractInfo> contracts = mongoTemplate.find(contractQuery, ContractInfo.class);
        if (!org.springframework.util.CollectionUtils.isEmpty(contracts)) {
            List<Long> contractIds = contracts.stream().map(ContractInfo::getId).toList();
            Query parcelQuery = new Query(Criteria.where("contract_id").in(contractIds));
            mongoTemplate.remove(parcelQuery, LandParcel.class);
            mongoTemplate.remove(contractQuery, ContractInfo.class);
        }
        Query surveyQuery = new Query(Criteria.where("project_id").is(projectId));
        List<SurveyReportInfo> surveyReports = mongoTemplate.find(surveyQuery, SurveyReportInfo.class);
        if (!org.springframework.util.CollectionUtils.isEmpty(surveyReports)) {
            List<Long> surveyIds = surveyReports.stream().map(SurveyReportInfo::getId).toList();
            Query roomQuery = new Query(Criteria.where("survey_report_info_id").in(surveyIds));
            mongoTemplate.remove(roomQuery, RoomInfo.class);
            mongoTemplate.remove(surveyQuery, SurveyReportInfo.class);
        }
        try {
            Query unknownUsageQuery = new Query(Criteria.where("project_id").is(projectId));
            long unknownDeleted = mongoTemplate.remove(unknownUsageQuery, UnknownUsageRecord.class)
                    .getDeletedCount();
            log.info("删除项目下未知用途记录: projectId={}, deletedCount={}", projectId, unknownDeleted);
        } catch (Exception ex) {
            log.warn("删除项目未知用途记录失败, projectId={}, error={}", projectId, ex.getMessage());
        }
        // 兜底清理：防历史异常导致文件已删除但主表/中间数据残留
        try {
            Query projectOnlyQuery = new Query(Criteria.where("project_id").is(projectId));
            long planningRowsDeleted = mongoTemplate.remove(projectOnlyQuery, PlanningReviewRow.class)
                    .getDeletedCount();
            long planningFormsDeleted = mongoTemplate.remove(projectOnlyQuery, PlanningReviewForm.class)
                    .getDeletedCount();
            long legacyPartySummaryDeleted = mongoTemplate
                    .remove(projectOnlyQuery, ProjectPartySurveySummaryForm.LEGACY_ROW_COLLECTION)
                    .getDeletedCount();
            long partyFormsDeleted = mongoTemplate.remove(projectOnlyQuery, ProjectPartySurveySummaryForm.class)
                    .getDeletedCount();
            long parseJobsDeleted = mongoTemplate.remove(projectOnlyQuery, ParseJob.class).getDeletedCount();
            long headersDeleted = mongoTemplate.remove(projectOnlyQuery, ParsedDataHeader.class).getDeletedCount();
            long itemsDeleted = mongoTemplate.remove(projectOnlyQuery, ParsedDataItem.class).getDeletedCount();
            long ocrExecDeleted = mongoTemplate.remove(projectOnlyQuery, OCRExecutionResult.class)
                    .getDeletedCount();
            if (planningRowsDeleted + planningFormsDeleted + legacyPartySummaryDeleted + partyFormsDeleted
                    + parseJobsDeleted + headersDeleted + itemsDeleted + ocrExecDeleted > 0) {
                log.info(
                        "项目删除兜底清理完成: projectId={}, planningRows={}, planningForms={}, legacyPartySummary={}, partyForms={}, parseJobs={}, headers={}, items={}, ocrExec={}",
                        projectId, planningRowsDeleted, planningFormsDeleted, legacyPartySummaryDeleted,
                        partyFormsDeleted, parseJobsDeleted, headersDeleted, itemsDeleted, ocrExecDeleted);
            }
        } catch (Exception ex) {
            log.warn("项目删除兜底清理失败, projectId={}, error={}", projectId, ex.getMessage());
        }
        try {
            List<FileArchive> archives = fileArchiveService.listByProjectId(projectId);
            if (!org.springframework.util.CollectionUtils.isEmpty(archives)) {
                for (FileArchive archive : archives) {
                    try {
                        fileArchiveService.deleteArchive(projectId, archive.getId());
                    } catch (Exception ex) {
                        log.warn("删除归档夹失败, projectId={}, archiveId={}", projectId, archive.getId(), ex);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("查询或删除项目归档夹失败, projectId={}", projectId, e);
        }
        Query projectQuery = new Query(Criteria.where("_id").is(projectId));
        mongoTemplate.remove(projectQuery, Project.class);
    }

    private ProjectQueryResultDTO queryProjectsInternal(ProjectQueryDTO queryDTO) {
        Criteria criteria = MongoQueryBuilder.buildCriteria(queryDTO);
        boolean hasStart = StringUtils.hasText(queryDTO.getProjectTimeStart());
        boolean hasEnd = StringUtils.hasText(queryDTO.getProjectTimeEnd());
        if (hasStart && hasEnd) {
            String normalizedStart = hasStart
                    ? ProjectTimeUtil.normalizeToIsoDate(queryDTO.getProjectTimeStart().trim())
                    : null;
            String normalizedEnd = hasEnd ? ProjectTimeUtil.normalizeToIsoDate(queryDTO.getProjectTimeEnd().trim())
                    : null;
            if (normalizedStart != null && normalizedEnd != null) {
                Criteria rangeCrit = Criteria.where("project_time").gte(normalizedStart).lte(normalizedEnd);
                criteria = criteria.andOperator(rangeCrit);
            }
        }
        Query query = new Query(criteria);
        int pageNum = PageSortSupport.resolvePageNum(queryDTO.getPageNum());
        int pageSize = PageSortSupport.resolvePageSize(queryDTO.getPageSize());
        Sort sort = PageSortSupport.resolveSort(queryDTO.getSortDirection(), queryDTO.getSortField(), "createTime");
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, sort);
        query.with(pageable);
        List<Project> projects = mongoTemplate.find(query, Project.class);
        long total = mongoTemplate.count(query.skip(-1).limit(-1), Project.class);
        ProjectQueryResultDTO result = new ProjectQueryResultDTO();
        result.setRecords(projects);
        result.setCurrent(pageNum);
        result.setSize(pageSize);
        result.setTotal(total);
        result.setPages((int) Math.ceil((double) total / pageSize));
        return result;
    }

    private ProjectDetailQueryResultDTO queryProjectDetailsInternal(ProjectQueryDTO queryDTO) {
        /*
         * 性能建议（Mongo 索引）：
         * 1) file_record(project_id, file_context_type, file_state)
         * 2) survey_report_info(project_id, is_parsed, is_verified)
         * 3) contract_info(project_id)
         *
         * 本接口通过“分页查询 project + 批量取数（file_record/survey_report_info/contract_info）”避免
         * N+1。
         */
        Criteria criteria = MongoQueryBuilder.buildCriteria(queryDTO);
        boolean hasStart = StringUtils.hasText(queryDTO.getProjectTimeStart());
        boolean hasEnd = StringUtils.hasText(queryDTO.getProjectTimeEnd());
        if (hasStart && hasEnd) {
            String normalizedStart = hasStart
                    ? ProjectTimeUtil.normalizeToIsoDate(queryDTO.getProjectTimeStart().trim())
                    : null;
            String normalizedEnd = hasEnd ? ProjectTimeUtil.normalizeToIsoDate(queryDTO.getProjectTimeEnd().trim())
                    : null;
            if (normalizedStart != null && normalizedEnd != null) {
                Criteria rangeCrit = Criteria.where("project_time").gte(normalizedStart).lte(normalizedEnd);
                criteria = criteria.andOperator(rangeCrit);
            }
        }

        Query query = new Query(criteria);
        int pageNum = PageSortSupport.resolvePageNum(queryDTO.getPageNum());
        int pageSize = PageSortSupport.resolvePageSize(queryDTO.getPageSize());
        Sort sort = PageSortSupport.resolveSort(queryDTO.getSortDirection(), queryDTO.getSortField(), "createTime");
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, sort);
        query.with(pageable);

        List<Project> projects = mongoTemplate.find(query, Project.class);
        long total = mongoTemplate.count(query.skip(-1).limit(-1), Project.class);

        ProjectDetailQueryResultDTO result = new ProjectDetailQueryResultDTO();
        result.setCurrent(pageNum);
        result.setSize(pageSize);
        result.setTotal(total);
        result.setPages((int) Math.ceil((double) total / pageSize));
        if (projects == null || projects.isEmpty()) {
            result.setRecords(List.of());
            return result;
        }

        List<Long> projectIds = projects.stream().map(Project::getId).filter(java.util.Objects::nonNull).toList();
        if (projectIds.isEmpty()) {
            result.setRecords(List.of());
            return result;
        }

        // 1) 文件级别：统计合同/实测文件数、解析失败、未解析
        var fileStatsMap = batchBuildFileStatsByProjectId(projectIds);

        // 2) 实测校验级别：判断校验失败（isParsed=1 && isVerified=0）
        var surveyValidationFailedMap = batchBuildSurveyValidationFailedMap(projectIds);

        // 3) 合同级别：汇总面积 + 任意非空的出让方/受让方
        var contractStatsMap = contractAndLandParcelService.batchGetContractProjectStatsByProjectIds(projectIds);

        // 4) 组装 VO
        List<ProjectDetailVO> detailList = projects.stream().map(project -> {
            ProjectDetailVO vo = new ProjectDetailVO();
            org.springframework.beans.BeanUtils.copyProperties(project, vo);

            FileStats stats = fileStatsMap.get(project.getId());
            if (stats != null) {
                vo.setContractFileCount(stats.contractFileCount);
                vo.setSurveyReportFileCount(stats.surveyReportFileCount);
                vo.setContractParseStatus(stats.contractParseStatus());
                vo.setSurveyParseStatus(stats.surveyParseStatus());

                SurveyValidationStatusEnum validationStatus = stats.validationStatus(
                        surveyValidationFailedMap.getOrDefault(project.getId(), false));
                vo.setSurveyValidationStatus(validationStatus);

                vo.setProjectStatus(stats.projectStatus(
                        surveyValidationFailedMap.getOrDefault(project.getId(), false)));
            } else {
                // 理论上不会发生：只要 project 存在就应该有 fileStatsMap entry
                vo.setContractFileCount(0);
                vo.setSurveyReportFileCount(0);
                vo.setContractParseStatus(com.gov.landcheck.core.enums.ContractParseStatusEnum.NOT_EXIST);
                vo.setSurveyParseStatus(com.gov.landcheck.core.enums.SurveyParseStatusEnum.NOT_EXIST);
                vo.setSurveyValidationStatus(com.gov.landcheck.core.enums.SurveyValidationStatusEnum.NOT_EXIST);
                vo.setProjectStatus(com.gov.landcheck.core.enums.ProjectStatusEnum.UNPARSED);
            }

            ContractProjectStatsVO contractStats = contractStatsMap.get(project.getId());
            if (contractStats != null) {
                vo.setContractAgreedTotalBuildingArea(contractStats.getTotalArea());
                vo.setContractAgreedCommercialArea(contractStats.getCommercialArea());
                vo.setContractAgreedResidentialArea(contractStats.getResidentialArea());
                vo.setTransferor(contractStats.getTransferor());
                vo.setTransferee(contractStats.getTransferee());
            } else {
                vo.setContractAgreedTotalBuildingArea(null);
                vo.setContractAgreedCommercialArea(null);
                vo.setContractAgreedResidentialArea(null);
                vo.setTransferor(null);
                vo.setTransferee(null);
            }

            return vo;
        }).toList();

        result.setRecords(detailList);
        return result;
    }

    private static class FileStats {
        private Integer contractFileCount = 0;
        private Integer surveyReportFileCount = 0;

        private boolean contractParseFailExists = false;
        private boolean surveyParseFailExists = false;

        private boolean contractUnparsedExists = false;
        private boolean surveyUnparsedExists = false;

        private com.gov.landcheck.core.enums.ContractParseStatusEnum contractParseStatus() {
            if (contractFileCount == 0) {
                return com.gov.landcheck.core.enums.ContractParseStatusEnum.NOT_EXIST;
            }
            if (contractParseFailExists) {
                return com.gov.landcheck.core.enums.ContractParseStatusEnum.PARSE_FAILED;
            }
            if (contractUnparsedExists) {
                return com.gov.landcheck.core.enums.ContractParseStatusEnum.UNPARSED;
            }
            return com.gov.landcheck.core.enums.ContractParseStatusEnum.PARSE_COMPLETE;
        }

        private com.gov.landcheck.core.enums.SurveyParseStatusEnum surveyParseStatus() {
            if (surveyReportFileCount == 0) {
                return com.gov.landcheck.core.enums.SurveyParseStatusEnum.NOT_EXIST;
            }
            if (surveyParseFailExists) {
                return com.gov.landcheck.core.enums.SurveyParseStatusEnum.PARSE_FAILED;
            }
            if (surveyUnparsedExists) {
                return com.gov.landcheck.core.enums.SurveyParseStatusEnum.UNPARSED;
            }
            return com.gov.landcheck.core.enums.SurveyParseStatusEnum.PARSE_COMPLETE;
        }

        private SurveyValidationStatusEnum validationStatus(boolean validationFailed) {
            if (surveyReportFileCount == 0) {
                return SurveyValidationStatusEnum.NOT_EXIST;
            }
            if (validationFailed) {
                return SurveyValidationStatusEnum.VALIDATION_FAILED;
            }
            // 未拿到详细校验通过/未解析信息时，按文件是否“解析完成”判断
            if (surveyUnparsedExists) {
                return SurveyValidationStatusEnum.NOT_PARSED;
            }
            return SurveyValidationStatusEnum.VALID;
        }

        private ProjectStatusEnum projectStatus(boolean validationFailed) {
            // 优先级：解析失败 > 校验失败 > 未解析
            if (contractParseFailExists || surveyParseFailExists) {
                return ProjectStatusEnum.PARSE_FAILED;
            }
            if (validationFailed) {
                return ProjectStatusEnum.SURVEY_VALIDATION_FAILED;
            }

            boolean hasAnyFile = (contractFileCount != 0 || surveyReportFileCount != 0);
            if (!hasAnyFile) {
                return ProjectStatusEnum.UNPARSED;
            }
            if (contractParseStatus() == com.gov.landcheck.core.enums.ContractParseStatusEnum.UNPARSED
                    || surveyParseStatus() == com.gov.landcheck.core.enums.SurveyParseStatusEnum.UNPARSED
                    || contractParseStatus() == com.gov.landcheck.core.enums.ContractParseStatusEnum.NOT_EXIST
                    || surveyParseStatus() == com.gov.landcheck.core.enums.SurveyParseStatusEnum.NOT_EXIST) {
                return ProjectStatusEnum.UNPARSED;
            }
            // 至此合同/实测都解析完成，且校验也未失败
            return ProjectStatusEnum.READY;
        }
    }

    private java.util.Map<Long, FileStats> batchBuildFileStatsByProjectId(List<Long> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            return java.util.Map.of();
        }

        Query fileQuery = new Query(Criteria.where("project_id").in(projectIds)
                .and("file_context_type").in(com.gov.landcheck.core.enums.FileContextType.CONTRACT,
                        com.gov.landcheck.core.enums.FileContextType.SURVEY_REPORT));
        // 仅投影统计字段，减少对象反序列化开销
        fileQuery.fields()
                .include("project_id")
                .include("file_context_type")
                .include("file_state");

        List<FileRecord> fileRecords = mongoTemplate.find(fileQuery, FileRecord.class);

        java.util.Map<Long, FileStats> map = new java.util.HashMap<>();
        for (Long pid : projectIds) {
            map.put(pid, new FileStats());
        }
        if (fileRecords == null || fileRecords.isEmpty()) {
            return map;
        }

        for (FileRecord fr : fileRecords) {
            if (fr == null || fr.getProjectId() == null || fr.getFileContextType() == null) {
                continue;
            }
            FileStats stats = map.get(fr.getProjectId());
            if (stats == null) {
                stats = new FileStats();
                map.put(fr.getProjectId(), stats);
            }

            boolean parseFail = fr.getFileState() == com.gov.landcheck.core.enums.FileStateEnum.PARSE_FAIL
                    || fr.getFileState() == com.gov.landcheck.core.enums.FileStateEnum.UNPARSEABLE;
            boolean parseComplete = fr.getFileState() == com.gov.landcheck.core.enums.FileStateEnum.PARSE_COMPLETE;

            if (fr.getFileContextType() == com.gov.landcheck.core.enums.FileContextType.CONTRACT) {
                stats.contractFileCount = stats.contractFileCount + 1;
                if (parseFail) {
                    stats.contractParseFailExists = true;
                }
                if (!parseComplete) {
                    stats.contractUnparsedExists = true;
                }
            } else if (fr.getFileContextType() == com.gov.landcheck.core.enums.FileContextType.SURVEY_REPORT) {
                stats.surveyReportFileCount = stats.surveyReportFileCount + 1;
                if (parseFail) {
                    stats.surveyParseFailExists = true;
                }
                if (!parseComplete) {
                    stats.surveyUnparsedExists = true;
                }
            }
        }

        return map;
    }

    private java.util.Map<Long, Boolean> batchBuildSurveyValidationFailedMap(List<Long> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            return java.util.Map.of();
        }
        Query surveyQuery = new Query(Criteria.where("project_id").in(projectIds));
        surveyQuery.fields()
                .include("project_id")
                .include("is_parsed")
                .include("is_verified");

        List<SurveyReportInfo> list = mongoTemplate.find(surveyQuery, SurveyReportInfo.class);
        java.util.Map<Long, Boolean> map = new java.util.HashMap<>();
        for (Long pid : projectIds) {
            map.put(pid, false);
        }
        if (list == null || list.isEmpty()) {
            return map;
        }
        for (SurveyReportInfo report : list) {
            if (report == null || report.getProjectId() == null) {
                continue;
            }
            if (report.isValidationFailed()) {
                map.put(report.getProjectId(), true);
            }
        }
        return map;
    }

    private String checkProjectFilesAndTasksBeforeDelete(Long projectId) {
        Query fileQuery = new Query(Criteria.where("project_id").is(projectId));
        if (!mongoTemplate.exists(fileQuery, FileRecord.class)) {
            return null;
        }
        Query runningFileQuery = new Query(
                Criteria.where("project_id").is(projectId)
                        .and("file_state").in(
                                FileStateEnum.UPLOADING,
                                FileStateEnum.WAITING_POST_PROCESS,
                                FileStateEnum.PARSING,
                                FileStateEnum.PENDING));
        if (mongoTemplate.exists(runningFileQuery, FileRecord.class)) {
            return "该项目下存在正在进行的文件任务，请等待任务完成后再进行删除";
        }
        Query fileIdQuery = new Query(Criteria.where("project_id").is(projectId));
        fileIdQuery.fields().include("_id");
        List<FileRecord> projectFiles = mongoTemplate.find(fileIdQuery, FileRecord.class);
        if (!org.springframework.util.CollectionUtils.isEmpty(projectFiles)) {
            List<Long> fileIds = projectFiles.stream().map(FileRecord::getId).toList();
            Query runningJobQuery = new Query(
                    Criteria.where("file_record_id").in(fileIds)
                            .and("job_status").in(ParseJobStateEnum.PENDING, ParseJobStateEnum.RUNNING));
            if (mongoTemplate.exists(runningJobQuery, ParseJob.class)) {
                return "该项目下存在正在进行的文件任务，请等待任务完成后再进行删除";
            }
        }
        return "该项目下仍有文件，请先删除全部文件后再删除项目";
    }

    private void evictBeforeWrite(Set<String> directKeys) {
        cacheInvalidationService.evictImmediately(directKeys);
    }

    private void evictAfterWrite(Set<String> directKeys, String queryPattern) {
        cacheInvalidationService.evictTwice(directKeys, DOUBLE_DELETE_DELAY_MS);
        cacheInvalidationService.evictByPatternTwice(Set.of(queryPattern), DOUBLE_DELETE_DELAY_MS);
    }
}
