package com.gov.landcheck.core.service;

import com.gov.landcheck.core.bo.entity.RoomInfo;
import com.gov.landcheck.core.bo.entity.SurveyReportInfo;
import com.gov.landcheck.core.common.ValidationResult;

import java.util.List;

/**
 * 实测报告计算服务接口
 * 负责实测报告的用途统计计算和数据校验
 *
 * @author system
 * @date 2025/01/25
 */
public interface SurveyReportCalculationService {

    /**
     * 计算实测报告的用途统计字段
     * 根据房间信息计算各种用途的面积统计，并更新到实测报告中
     *
     * @param surveyReportInfo 实测报告信息
     * @param roomInfos 房间信息列表
     */
    void calculateUsageSums(SurveyReportInfo surveyReportInfo, List<RoomInfo> roomInfos);

    /**
     * 校验实测报告数据的一致性
     * 校验面积总和、计容面积等数据是否符合业务规则
     *
     * @param surveyReportInfo 实测报告信息
     * @return 校验结果
     */
    ValidationResult validateSurveyReport(SurveyReportInfo surveyReportInfo);

    /**
     * 计算并校验实测报告（组合操作）
     * 先计算用途统计，再进行数据校验
     *
     * @param surveyReportInfo 实测报告信息
     * @param roomInfos 房间信息列表
     * @return 校验结果
     */
    ValidationResult calculateAndValidate(SurveyReportInfo surveyReportInfo, List<RoomInfo> roomInfos);
}