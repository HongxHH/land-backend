package com.gov.landcheck.core.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gov.landcheck.core.bo.entity.RoomInfo;
import com.gov.landcheck.core.bo.entity.SurveyReportInfo;
import com.gov.landcheck.core.bo.entity.UsageConfig;
import com.gov.landcheck.core.common.ValidationResult;
import com.gov.landcheck.core.common.SurveyReportUsageLabels;
import com.gov.landcheck.core.enums.FloorAreaTypeEnum;
import com.gov.landcheck.core.service.SurveyReportCalculationService;
import com.gov.landcheck.core.service.UnknownUsageRecordService;
import com.gov.landcheck.core.service.UsageConfigService;

import lombok.extern.slf4j.Slf4j;

/**
 * 实测报告计算服务实现
 * 负责实测报告的用途统计计算和数据校验
 *
 * @author system
 * @date 2025/01/25
 */
@Slf4j
@Service
public class SurveyReportCalculationServiceImpl implements SurveyReportCalculationService {

    @Autowired
    private UsageConfigService usageConfigService;

    @Autowired
    private UnknownUsageRecordService unknownUsageRecordService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void calculateUsageSums(SurveyReportInfo surveyReportInfo, List<RoomInfo> roomInfos) {
        BigDecimal residentialArea = BigDecimal.ZERO;
        BigDecimal commercialArea = BigDecimal.ZERO;
        BigDecimal managementArea = BigDecimal.ZERO;
        BigDecimal otherBuildableArea = BigDecimal.ZERO;
        BigDecimal communityArea = BigDecimal.ZERO;
        BigDecimal otherPublicArea = BigDecimal.ZERO;
        BigDecimal pendingConfirmArea = BigDecimal.ZERO; // 待确认面积
        BigDecimal buildingAreaSum = BigDecimal.ZERO;
        BigDecimal innerAreaSum = BigDecimal.ZERO;
        BigDecimal balconyAreaSum = BigDecimal.ZERO;
        BigDecimal sharedAreaSum = BigDecimal.ZERO;

        // 计算户室各类面积之和
        for (RoomInfo roomInfo : roomInfos) {
            if (roomInfo.getBuildingArea() != null)
                buildingAreaSum = buildingAreaSum.add(roomInfo.getBuildingArea());
            if (roomInfo.getInnerArea() != null)
                innerAreaSum = innerAreaSum.add(roomInfo.getInnerArea());
            if (roomInfo.getBalconyArea() != null)
                balconyAreaSum = balconyAreaSum.add(roomInfo.getBalconyArea());
            if (roomInfo.getSharedArea() != null)
                sharedAreaSum = sharedAreaSum.add(roomInfo.getSharedArea());
        }
        surveyReportInfo.setRoomInfoBuildingAreaSum(buildingAreaSum.setScale(4, RoundingMode.HALF_UP));
        surveyReportInfo.setRoomInfoInnerAreaSum(innerAreaSum.setScale(4, RoundingMode.HALF_UP));
        surveyReportInfo.setRoomInfoBalconyAreaSum(balconyAreaSum.setScale(4, RoundingMode.HALF_UP));
        surveyReportInfo.setRoomInfoSharedAreaSum(sharedAreaSum.setScale(4, RoundingMode.HALF_UP));

        // 计算用途统计字段
        Map<String, String> unknownUsages = new HashMap<>(); // 收集未知用途，格式：房间号 -> 用途
        for (RoomInfo roomInfo : roomInfos) {
            String usage = roomInfo.getRoomUsage();
            BigDecimal buildingArea = roomInfo.getBuildingArea();

            if (buildingArea == null) {
                roomInfo.setIsCalculate(0);
                roomInfo.setUsageCategory("UNKNOWN");
                roomInfo.setFloorAreaType(FloorAreaTypeEnum.UNKNOWN);
                continue;
            }

            if (SurveyReportUsageLabels.isBlankUsage(usage)) {
                pendingConfirmArea = pendingConfirmArea.add(buildingArea);
                roomInfo.setIsCalculate(0);
                roomInfo.setUsageCategory("UNKNOWN");
                roomInfo.setFloorAreaType(FloorAreaTypeEnum.UNKNOWN);
                unknownUsages.put(roomInfo.getRoomNumber(), SurveyReportUsageLabels.MISSING_USAGE_LABEL);
                continue;
            }

            UsageConfig config = usageConfigService.matchUsageConfig(usage);

            if (config != null) {
                // 匹配到已配置的用途
                switch (config.getUsageCategory()) {
                    case "RESIDENTIAL" -> {
                        residentialArea = residentialArea.add(buildingArea);
                        roomInfo.setUsageCategory("RESIDENTIAL");
                        roomInfo.setFloorAreaType(FloorAreaTypeEnum.BUILDABLE);
                        roomInfo.setIsCalculate(1);
                    }
                    case "COMMERCIAL" -> {
                        commercialArea = commercialArea.add(buildingArea);
                        roomInfo.setUsageCategory("COMMERCIAL");
                        roomInfo.setFloorAreaType(FloorAreaTypeEnum.BUILDABLE);
                        roomInfo.setIsCalculate(1);
                    }
                    case "MANAGEMENT" -> {
                        managementArea = managementArea.add(buildingArea);
                        roomInfo.setUsageCategory("MANAGEMENT");
                        roomInfo.setFloorAreaType(FloorAreaTypeEnum.BUILDABLE);
                        roomInfo.setIsCalculate(1);
                    }
                    case "OTHER_BUILDABLE" -> {
                        otherBuildableArea = otherBuildableArea.add(buildingArea);
                        roomInfo.setUsageCategory("OTHER_BUILDABLE");
                        roomInfo.setFloorAreaType(FloorAreaTypeEnum.BUILDABLE);
                        roomInfo.setIsCalculate(1);
                    }
                    case "COMMUNITY" -> {
                        communityArea = communityArea.add(buildingArea);
                        roomInfo.setUsageCategory("COMMUNITY");
                        roomInfo.setFloorAreaType(FloorAreaTypeEnum.NON_BUILDABLE);
                        roomInfo.setIsCalculate(1);
                    }
                    case "OTHER_PUBLIC" -> {
                        otherPublicArea = otherPublicArea.add(buildingArea);
                        roomInfo.setUsageCategory("OTHER_PUBLIC");
                        roomInfo.setFloorAreaType(FloorAreaTypeEnum.NON_BUILDABLE);
                        roomInfo.setIsCalculate(1);
                    }
                    default -> {
                        // 未知的用途类别，按不计容处理
                        otherPublicArea = otherPublicArea.add(buildingArea);
                        roomInfo.setUsageCategory("OTHER_PUBLIC");
                        roomInfo.setFloorAreaType(FloorAreaTypeEnum.UNKNOWN);
                        roomInfo.setIsCalculate(1);
                    }
                }
            } else {
                // 未知用途处理
                pendingConfirmArea = pendingConfirmArea.add(buildingArea);
                roomInfo.setIsCalculate(0);
                roomInfo.setUsageCategory("UNKNOWN");
                roomInfo.setFloorAreaType(FloorAreaTypeEnum.UNKNOWN);
                unknownUsages.put(roomInfo.getRoomNumber(), usage);

                // 记录未知用途
                unknownUsageRecordService.recordUnknownUsage(
                        usage,
                        surveyReportInfo.getProjectId(),
                        surveyReportInfo.getFileRecordId(),
                        surveyReportInfo.getId(),
                        roomInfo.getId());
            }
        }

        // 设置各用途面积字段
        surveyReportInfo.setActualResidentialArea(residentialArea.setScale(4, RoundingMode.HALF_UP));
        surveyReportInfo.setActualCommercialArea(commercialArea.setScale(4, RoundingMode.HALF_UP));
        surveyReportInfo.setActualManagementRoomArea(managementArea.setScale(4, RoundingMode.HALF_UP));
        surveyReportInfo.setActualOtherBuildableArea(otherBuildableArea.setScale(4, RoundingMode.HALF_UP));
        surveyReportInfo.setActualCommunityArea(communityArea.setScale(4, RoundingMode.HALF_UP));
        surveyReportInfo.setActualOtherPublicArea(otherPublicArea.setScale(4, RoundingMode.HALF_UP));

        // 计算计容面积和不计容面积
        BigDecimal totalBuildableArea = residentialArea.add(commercialArea)
                .add(managementArea).add(otherBuildableArea);
        BigDecimal totalNonBuildableArea = communityArea.add(otherPublicArea);

        surveyReportInfo.setTotalBuildableArea(totalBuildableArea.setScale(4, RoundingMode.HALF_UP));
        surveyReportInfo.setTotalNonBuildableArea(totalNonBuildableArea.setScale(4, RoundingMode.HALF_UP));
        surveyReportInfo.setPendingConfirmArea(pendingConfirmArea.setScale(4, RoundingMode.HALF_UP));
        surveyReportInfo.setHasUnknownUsage(unknownUsages.isEmpty() ? 0 : 1);
        try {
            surveyReportInfo.setUnknownUsages(objectMapper.writeValueAsString(unknownUsages));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化未知用途失败", e);
        }
        surveyReportInfo.setUnknownUsageCount(unknownUsages.size());

        // 目前按照：计算实测报告总建筑面积 = 户室面积对照表的建筑面积总和
        surveyReportInfo.setActualTotalBuildingArea(surveyReportInfo.getRoomInfoBuildingAreaSum());
    }

    @Override
    public ValidationResult validateSurveyReport(SurveyReportInfo surveyReportInfo) {
        StringBuilder blockingReasons = new StringBuilder();
        StringBuilder ocrNonBuildingColumnHints = new StringBuilder();

        // 校验建筑面积总和（与 OCR 对照，不通过则整份报告校验失败）
        if (!compareBigDecimal(surveyReportInfo.getRoomInfoBuildingAreaSum(),
                surveyReportInfo.getRoomInfoBuildingAreaSumFromOcr())) {
            blockingReasons.append("建筑面积总和(")
                    .append(surveyReportInfo.getRoomInfoBuildingAreaSum())
                    .append(")与OCR识别结果(")
                    .append(surveyReportInfo.getRoomInfoBuildingAreaSumFromOcr())
                    .append(")不一致; ");
        }

        // 套内 / 阳台 / 分摊与 OCR 对照：常因印章遮挡等导致 OCR 偏差，仅记录说明，不阻断校验通过
        if (!compareBigDecimal(surveyReportInfo.getRoomInfoInnerAreaSum(),
                surveyReportInfo.getRoomInfoInnerAreaSumFromOcr())) {
            ocrNonBuildingColumnHints.append("套内面积总和(")
                    .append(surveyReportInfo.getRoomInfoInnerAreaSum())
                    .append(")与OCR识别结果(")
                    .append(surveyReportInfo.getRoomInfoInnerAreaSumFromOcr())
                    .append(")不一致; ");
        }
        if (!compareBigDecimal(surveyReportInfo.getRoomInfoBalconyAreaSum(),
                surveyReportInfo.getRoomInfoBalconyAreaSumFromOcr())) {
            ocrNonBuildingColumnHints.append("阳台面积总和(")
                    .append(surveyReportInfo.getRoomInfoBalconyAreaSum())
                    .append(")与OCR识别结果(")
                    .append(surveyReportInfo.getRoomInfoBalconyAreaSumFromOcr())
                    .append(")不一致; ");
        }
        if (!compareBigDecimal(surveyReportInfo.getRoomInfoSharedAreaSum(),
                surveyReportInfo.getRoomInfoSharedAreaSumFromOcr())) {
            ocrNonBuildingColumnHints.append("分摊面积总和(")
                    .append(surveyReportInfo.getRoomInfoSharedAreaSum())
                    .append(")与OCR识别结果(")
                    .append(surveyReportInfo.getRoomInfoSharedAreaSumFromOcr())
                    .append(")不一致; ");
        }

        // 校验计容面积 + 不计容面积 + 待确认面积 = 建筑面积总和
        BigDecimal totalCalculatedArea = BigDecimal.ZERO;
        if (surveyReportInfo.getTotalBuildableArea() != null) {
            totalCalculatedArea = totalCalculatedArea.add(surveyReportInfo.getTotalBuildableArea());
        }
        if (surveyReportInfo.getTotalNonBuildableArea() != null) {
            totalCalculatedArea = totalCalculatedArea.add(surveyReportInfo.getTotalNonBuildableArea());
        }
        if (surveyReportInfo.getPendingConfirmArea() != null) {
            totalCalculatedArea = totalCalculatedArea.add(surveyReportInfo.getPendingConfirmArea());
        }

        if (!compareBigDecimal(totalCalculatedArea, surveyReportInfo.getRoomInfoBuildingAreaSum())) {
            blockingReasons.append("计容面积 + 不计容面积 + 待确认面积 (")
                    .append(totalCalculatedArea)
                    .append(")与建筑面积总和(")
                    .append(surveyReportInfo.getRoomInfoBuildingAreaSum())
                    .append(")不一致; ");
        }

        boolean blockingFailed = blockingReasons.length() > 0;
        if (blockingFailed) {
            if (ocrNonBuildingColumnHints.length() > 0) {
                blockingReasons.append("[以下 OCR 差异不阻断通过] ").append(ocrNonBuildingColumnHints);
            }
            return ValidationResult.failure(blockingReasons.toString());
        }
        if (ocrNonBuildingColumnHints.length() > 0) {
            return new ValidationResult(true,
                    "[以下 OCR 差异不阻断通过] " + ocrNonBuildingColumnHints);
        }
        return ValidationResult.success();
    }

    @Override
    public ValidationResult calculateAndValidate(SurveyReportInfo surveyReportInfo, List<RoomInfo> roomInfos) {
        if (surveyReportInfo != null && surveyReportInfo.getFileRecordId() != null) {
            unknownUsageRecordService.deleteByFileRecordId(surveyReportInfo.getFileRecordId());
        }
        calculateUsageSums(surveyReportInfo, roomInfos);
        return validateSurveyReport(surveyReportInfo);
    }

    /**
     * 比较两个BigDecimal是否相等（允许小数点后4位精度）
     */
    private boolean compareBigDecimal(BigDecimal val1, BigDecimal val2) {
        if (val1 == null && val2 == null) {
            return true;
        }
        if (val1 == null || val2 == null) {
            return false;
        }
        return val1.setScale(4, RoundingMode.HALF_UP)
                .compareTo(val2.setScale(4, RoundingMode.HALF_UP)) == 0;
    }
}