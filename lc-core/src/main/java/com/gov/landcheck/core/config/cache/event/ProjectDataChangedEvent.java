package com.gov.landcheck.core.config.cache.event;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * 跨模块的项目域数据变更事件：用于触发 lc-project 侧缓存失效（合同/实测报告/房间等）。
 *
 * <p>
 * 设计目标：
 * <ul>
 * <li>lc-file 只发布事件，不依赖 lc-project 的缓存 key 细节</li>
 * <li>lc-project 监听事件并执行精准/批量的 cache evict</li>
 * <li>可兼容仅知道 projectId 的场景（例如删除/批处理）</li>
 * </ul>
 */
public record ProjectDataChangedEvent(
        Long projectId, // 项目ID
        Long contractId, // 合同ID
        Long surveyReportId, // 实测报告ID
        Set<ProjectDataType> types, // 变更类型
        boolean evictQueryCaches // 是否清除查询缓存
) {

    public ProjectDataChangedEvent {
        if (types == null) {
            types = Collections.emptySet();
        } else if (!(types instanceof EnumSet<?>)) {
            types = Collections.unmodifiableSet(EnumSet.copyOf(types));
        }
    }

    public static ProjectDataChangedEvent contractChanged(Long projectId, Long contractId) {
        return new ProjectDataChangedEvent(projectId, contractId, null, EnumSet.of(ProjectDataType.CONTRACT), true);
    }

    public static ProjectDataChangedEvent surveyReportChanged(Long projectId, Long surveyReportId,
            boolean roomsChanged) {
        EnumSet<ProjectDataType> eventTypes = EnumSet.of(ProjectDataType.SURVEY_REPORT);
        if (roomsChanged) {
            eventTypes.add(ProjectDataType.ROOM);
        }
        return new ProjectDataChangedEvent(projectId, null, surveyReportId, eventTypes, true);
    }

    /** 规划复核表主表或行数据变更，用于失效项目侧相关缓存 */
    public static ProjectDataChangedEvent planningReviewChanged(Long projectId) {
        return new ProjectDataChangedEvent(projectId, null, null, EnumSet.of(ProjectDataType.PLANNING_REVIEW), true);
    }

    /** 项目方汇总数据变更，用于失效项目侧相关缓存 */
    public static ProjectDataChangedEvent projectPartySummaryChanged(Long projectId, Long formId) {
        return new ProjectDataChangedEvent(projectId, formId, null, EnumSet.of(ProjectDataType.PROJECT_PARTY_SUMMARY),
                true);
    }

    /** 容量指标核查表数据变更，用于失效项目侧相关缓存 */
    public static ProjectDataChangedEvent capacityIndicatorChanged(Long projectId) {
        return new ProjectDataChangedEvent(projectId, null, null, EnumSet.of(ProjectDataType.CAPACITY_INDICATOR), true);
    }

    public boolean hasType(ProjectDataType type) {
        return types != null && types.contains(type);
    }

    public boolean hasAnyId() {
        return projectId != null || contractId != null || surveyReportId != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof ProjectDataChangedEvent that))
            return false;
        return evictQueryCaches == that.evictQueryCaches
                && Objects.equals(projectId, that.projectId)
                && Objects.equals(contractId, that.contractId)
                && Objects.equals(surveyReportId, that.surveyReportId)
                && Objects.equals(types, that.types);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectId, contractId, surveyReportId, types, evictQueryCaches);
    }
}
