package com.gov.landcheck.file.service.parse;

import java.time.LocalDateTime;

import org.springframework.data.mongodb.core.query.Update;

/**
 * 实测报告 SurveyReportInfo 字段重置（重解析 / 校验失败回滚共用）。
 */
public final class SurveyReportFieldUpdates {

    private SurveyReportFieldUpdates() {
    }

    public static Update appendComputedStatsReset(Update update) {
        return update
                .set("is_verified", null)
                .unset("verification_error_reason")
                .set("has_unknown_usage", 0)
                .unset("unknown_usages")
                .unset("unknown_usage_count")
                .unset("pending_confirm_area")
                .unset("actual_total_building_area")
                .unset("actual_residential_area")
                .unset("actual_commercial_area")
                .unset("actual_management_area")
                .unset("actual_other_buildable_area")
                .unset("actual_community_area")
                .unset("actual_other_public_area")
                .unset("total_buildable_area")
                .unset("total_non_buildable_area")
                .unset("room_info_building_area_sum")
                .unset("room_info_inner_area_sum")
                .unset("room_info_balcony_area_sum")
                .unset("room_info_shared_area_sum");
    }

    public static Update reparseReset() {
        return appendComputedStatsReset(new Update()
                .set("is_parsed", 0)
                .set("update_time", LocalDateTime.now()));
    }
}
