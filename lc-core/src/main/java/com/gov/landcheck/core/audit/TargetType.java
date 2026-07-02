package com.gov.landcheck.core.audit;

/**
 * 审计目标实体类型
 */
public enum TargetType {
    PROJECT("project"),
    CONTRACT("contract"),
    LAND_PARCEL("land_parcel"),
    ROOM_INFO("room_info"),
    SURVEY_REPORT("survey_report"),
    FILE("file"),
    FILE_ARCHIVE("file_archive"),
    USAGE_CONFIG("usage_config"),
    PLANNING_REVIEW_FORM("planning_review_form"),
    PLANNING_REVIEW_ROW("planning_review_row"),
    PROJECT_PARTY_SUMMARY_FORM("project_party_summary_form"),
    CAPACITY_INDICATOR_FORM("capacity_indicator_form");

    private final String value;

    TargetType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
