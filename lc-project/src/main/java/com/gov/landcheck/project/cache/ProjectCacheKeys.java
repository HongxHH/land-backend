package com.gov.landcheck.project.cache;

import com.gov.landcheck.core.config.cache.config.CacheProperties;
import com.gov.landcheck.core.config.cache.constant.CacheKeyPrefixes;
import com.gov.landcheck.core.config.cache.key.StableKeyGenerator;
import org.springframework.stereotype.Component;

@Component
public class ProjectCacheKeys {

    private final CacheProperties cacheProperties;
    private final StableKeyGenerator stableKeyGenerator;

    public ProjectCacheKeys(CacheProperties cacheProperties, StableKeyGenerator stableKeyGenerator) {
        this.cacheProperties = cacheProperties;
        this.stableKeyGenerator = stableKeyGenerator;
    }

    public String projectById(Long id) {
        return basePrefix() + ":byId:id:" + id;
    }

    public String contractsByProject(Long projectId) {
        return basePrefix() + ":contractsByProject:projectId:" + projectId;
    }

    public String contractWithParcels(Long contractId) {
        return basePrefix() + ":contractWithParcels:contractId:" + contractId;
    }

    /** 已解析实测报告 VO 列表（含 fileOriginalName），缓存值为 List&lt;SurveyReportInfoVO&gt; */
    public String parsedReportsByProject(Long projectId) {
        return basePrefix() + ":parsedReportVOsByProject:projectId:" + projectId;
    }

    public String roomsByProjectAndSurveyReport(Long projectId, Long surveyReportId) {
        return basePrefix() + ":roomsByProjectReport:" + projectId + ":" + surveyReportId;
    }

    public String planningReviewsByProject(Long projectId) {
        return basePrefix() + ":planningReviewsByProject:projectId:" + projectId;
    }

    public String projectPartySummariesByProject(Long projectId) {
        return basePrefix() + ":projectPartySummariesByProject:projectId:" + projectId;
    }

    public String capacityIndicatorsByProject(Long projectId) {
        return basePrefix() + ":capacityIndicatorsByProject:projectId:" + projectId;
    }

    public String areaComparisonByProject(Long projectId) {
        return basePrefix() + ":areaComparisonByProject:projectId:" + projectId;
    }

    public String allProjects() {
        return basePrefix() + ":all:list";
    }

    public String queryProjects(Object dto) {
        return queryPrefix() + ":projects:q:" + stableKeyGenerator.hashOf(dto);
    }

    public String queryProjectDetails(Object dto) {
        return queryPrefix() + ":projectsDetail:q:" + stableKeyGenerator.hashOf(dto);
    }

    public String queryContracts(Object dto) {
        return queryPrefix() + ":contracts:q:" + stableKeyGenerator.hashOf(dto);
    }

    public String querySurveyReports(Object dto) {
        return queryPrefix() + ":surveyReports:q:" + stableKeyGenerator.hashOf(dto);
    }

    public String queryRooms(Object dto) {
        return queryPrefix() + ":rooms:q:" + stableKeyGenerator.hashOf(dto);
    }

    public String queryPlanningReviewForms(Object dto) {
        return queryPrefix() + ":planningReviewForms:q:" + stableKeyGenerator.hashOf(dto);
    }

    public String queryPlanningReviewRows(Object dto) {
        return queryPrefix() + ":planningReviewRows:q:" + stableKeyGenerator.hashOf(dto);
    }

    public String queryProjectPartySummaryForms(Object dto) {
        return queryPrefix() + ":projectPartySummaryForms:q:" + stableKeyGenerator.hashOf(dto);
    }

    public String queryCapacityIndicatorForms(Object dto) {
        return queryPrefix() + ":capacityIndicatorForms:q:" + stableKeyGenerator.hashOf(dto);
    }

    public String planningReviewRowsByProjectAndForm(Long projectId, Long formId) {
        return basePrefix() + ":planningReviewRows:project:" + projectId + ":form:" + formId;
    }

    public String planningReviewRowListPatternByProject(Long projectId) {
        return basePrefix() + ":planningReviewRows:project:" + projectId + ":form:*";
    }

    public String queryPattern() {
        return queryPrefix() + ":*";
    }

    private String basePrefix() {
        return CacheKeyPrefixes.GLOBAL_PREFIX + ":" + cacheProperties.getEnv()
                + ":" + CacheKeyPrefixes.PROJECT_MODULE + ":" + cacheProperties.getKey().getVersion();
    }

    private String queryPrefix() {
        return basePrefix() + ":" + CacheKeyPrefixes.QUERY_PREFIX;
    }
}
