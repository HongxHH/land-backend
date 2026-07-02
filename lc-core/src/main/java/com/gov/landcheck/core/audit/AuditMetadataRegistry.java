package com.gov.landcheck.core.audit;

import org.springframework.stereotype.Component;

import com.gov.landcheck.core.bo.entity.CapacityIndicatorInfo;
import com.gov.landcheck.core.bo.entity.ContractInfo;
import com.gov.landcheck.core.bo.entity.FileArchive;
import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.LandParcel;
import com.gov.landcheck.core.bo.entity.PlanningReviewForm;
import com.gov.landcheck.core.bo.entity.PlanningReviewRow;
import com.gov.landcheck.core.bo.entity.Project;
import com.gov.landcheck.core.bo.entity.ProjectPartySurveySummaryForm;
import com.gov.landcheck.core.bo.entity.RoomInfo;
import com.gov.landcheck.core.bo.entity.SurveyReportInfo;
import com.gov.landcheck.core.bo.entity.UsageConfig;

/**
 * 审计目标类型与实体 Class 的注册表，供切面根据 targetType 加载实体。
 */
@Component
public class AuditMetadataRegistry {

    public Class<?> getEntityClass(TargetType targetType) {
        return switch (targetType) {
            case PROJECT -> Project.class;
            case CONTRACT -> ContractInfo.class;
            case LAND_PARCEL -> LandParcel.class;
            case ROOM_INFO -> RoomInfo.class;
            case SURVEY_REPORT -> SurveyReportInfo.class;
            case FILE -> FileRecord.class;
            case FILE_ARCHIVE -> FileArchive.class;
            case USAGE_CONFIG -> UsageConfig.class;
            case PLANNING_REVIEW_FORM -> PlanningReviewForm.class;
            case PLANNING_REVIEW_ROW -> PlanningReviewRow.class;
            case PROJECT_PARTY_SUMMARY_FORM -> ProjectPartySurveySummaryForm.class;
            case CAPACITY_INDICATOR_FORM -> CapacityIndicatorInfo.class;
        };
    }
}
