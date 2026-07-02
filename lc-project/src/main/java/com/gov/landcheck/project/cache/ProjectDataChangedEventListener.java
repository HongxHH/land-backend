package com.gov.landcheck.project.cache;

import java.util.HashSet;
import java.util.Set;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.gov.landcheck.core.config.cache.event.ProjectDataChangedEvent;
import com.gov.landcheck.core.config.cache.event.ProjectDataType;
import com.gov.landcheck.core.config.cache.service.CacheInvalidationService;

import lombok.extern.slf4j.Slf4j;

/**
 * 监听 lc-file 等模块发布的项目域数据变更事件，并执行 lc-project 侧缓存失效。
 */
@Slf4j
@Component
public class ProjectDataChangedEventListener {

    private static final long DOUBLE_DELETE_DELAY_MS = 500L;

    private final CacheInvalidationService cacheInvalidationService;
    private final ProjectCacheKeys projectCacheKeys;

    public ProjectDataChangedEventListener(CacheInvalidationService cacheInvalidationService,
            ProjectCacheKeys projectCacheKeys) {
        this.cacheInvalidationService = cacheInvalidationService;
        this.projectCacheKeys = projectCacheKeys;
    }

    @Async
    @EventListener
    public void onProjectDataChanged(ProjectDataChangedEvent event) {
        if (event == null || !event.hasAnyId() || event.types() == null || event.types().isEmpty()) {
            return;
        }

        Set<String> directKeys = new HashSet<>();

        if (event.hasType(ProjectDataType.CONTRACT)) {
            if (event.contractId() != null) {
                directKeys.add(projectCacheKeys.contractWithParcels(event.contractId()));
            }
            if (event.projectId() != null) {
                directKeys.add(projectCacheKeys.contractsByProject(event.projectId()));
                directKeys.add(projectCacheKeys.areaComparisonByProject(event.projectId()));
                directKeys.add(projectCacheKeys.parsedReportsByProject(event.projectId()));
            }
        }

        if (event.hasType(ProjectDataType.SURVEY_REPORT) || event.hasType(ProjectDataType.ROOM)) {
            if (event.projectId() != null) {
                directKeys.add(projectCacheKeys.parsedReportsByProject(event.projectId()));
                directKeys.add(projectCacheKeys.areaComparisonByProject(event.projectId()));
            }
            if (event.projectId() != null && event.surveyReportId() != null) {
                directKeys
                        .add(projectCacheKeys.roomsByProjectAndSurveyReport(event.projectId(), event.surveyReportId()));
            }
        }

        if (event.hasType(ProjectDataType.PLANNING_REVIEW) && event.projectId() != null) {
            directKeys.add(projectCacheKeys.planningReviewsByProject(event.projectId()));
            directKeys.add(projectCacheKeys.areaComparisonByProject(event.projectId()));
        }

        if (event.hasType(ProjectDataType.PROJECT_PARTY_SUMMARY) && event.projectId() != null) {
            directKeys.add(projectCacheKeys.projectPartySummariesByProject(event.projectId()));
            directKeys.add(projectCacheKeys.areaComparisonByProject(event.projectId()));
        }

        if (event.hasType(ProjectDataType.CAPACITY_INDICATOR) && event.projectId() != null) {
            directKeys.add(projectCacheKeys.capacityIndicatorsByProject(event.projectId()));
            directKeys.add(projectCacheKeys.areaComparisonByProject(event.projectId()));
        }

        if (directKeys.isEmpty()) {
            return;
        }

        Set<String> patterns = new HashSet<>();
        if (event.evictQueryCaches()) {
            patterns.add(projectCacheKeys.queryPattern());
        }
        if (event.hasType(ProjectDataType.PLANNING_REVIEW) && event.projectId() != null) {
            patterns.add(projectCacheKeys.planningReviewRowListPatternByProject(event.projectId()));
        }

        runEvictionWithRetry(event, directKeys, patterns);
    }

    private void runEvictionWithRetry(ProjectDataChangedEvent event, Set<String> directKeys, Set<String> patterns) {
        try {
            doEviction(directKeys, patterns);
        } catch (Exception ex) {
            log.error("Handle ProjectDataChangedEvent failed (first attempt), event={}", event, ex);
            try {
                doEviction(directKeys, patterns);
            } catch (Exception ex2) {
                log.error("Handle ProjectDataChangedEvent failed (retry), event={}", event, ex2);
            }
        }
    }

    private void doEviction(Set<String> directKeys, Set<String> patterns) {
        cacheInvalidationService.evictTwice(directKeys, DOUBLE_DELETE_DELAY_MS);
        if (!patterns.isEmpty()) {
            cacheInvalidationService.evictByPatternTwice(patterns, DOUBLE_DELETE_DELAY_MS);
        }
    }
}
