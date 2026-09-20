package com.gov.landcheck.file.service.parse;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.mongodb.core.query.Update;

import com.gov.landcheck.core.bo.entity.SurveyReportInfo;

/**
 * 实测报告 SurveyReportInfo 字段重置（重解析 / 校验失败回滚共用）。
 */
public final class SurveyReportFieldUpdates {

    /** Update $unset 与实体清零共用，避免两套字段列表漂移。 */
    static final List<String> COMPUTED_STATS_UNSET_FIELDS = List.of(
            "verification_error_reason",
            "unknown_usages",
            "unknown_usage_count",
            "pending_confirm_area",
            "actual_total_building_area",
            "actual_residential_area",
            "actual_commercial_area",
            "actual_management_area",
            "actual_other_buildable_area",
            "actual_community_area",
            "actual_other_public_area",
            "total_buildable_area",
            "total_non_buildable_area",
            "room_info_building_area_sum",
            "room_info_inner_area_sum",
            "room_info_balcony_area_sum",
            "room_info_shared_area_sum");

    private SurveyReportFieldUpdates() {
    }

    public static Update appendComputedStatsReset(Update update) {
        update.set("is_verified", null).set("has_unknown_usage", 0);
        for (String field : COMPUTED_STATS_UNSET_FIELDS) {
            update.unset(field);
        }
        return update;
    }

    /**
     * 回填已有实测时清空上一轮校验结论与计算字段。
     * 不改 is_parsed、不改 OCR from_ocr 合计；is_verified 必须为 null 而不是 0。
     */
    public static void applyComputedStatsReset(SurveyReportInfo info) {
        if (info == null) {
            return;
        }
        info.setIsVerified(null);
        info.setVerificationErrorReason(null);
        info.setHasUnknownUsage(0);
        info.setUnknownUsages(null);
        info.setUnknownUsageCount(null);
        info.setPendingConfirmArea(null);
        info.setActualTotalBuildingArea(null);
        info.setActualResidentialArea(null);
        info.setActualCommercialArea(null);
        info.setActualManagementRoomArea(null);
        info.setActualOtherBuildableArea(null);
        info.setActualCommunityArea(null);
        info.setActualOtherPublicArea(null);
        info.setTotalBuildableArea(null);
        info.setTotalNonBuildableArea(null);
        info.setRoomInfoBuildingAreaSum(null);
        info.setRoomInfoInnerAreaSum(null);
        info.setRoomInfoBalconyAreaSum(null);
        info.setRoomInfoSharedAreaSum(null);
    }

    public static Update reparseReset() {
        return appendComputedStatsReset(new Update()
                .set("is_parsed", 0)
                .set("update_time", LocalDateTime.now()));
    }
}
