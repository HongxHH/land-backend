package com.gov.landcheck.project.service;

import com.gov.landcheck.core.bo.R.AjaxJson;
import com.gov.landcheck.project.dto.RoomInfoCreateDTO;
import com.gov.landcheck.project.dto.RoomInfoQueryDTO;
import com.gov.landcheck.project.dto.RoomInfoUpdateDTO;
import com.gov.landcheck.project.dto.SurveyReportInfoQueryDTO;
import com.gov.landcheck.project.dto.SurveyReportInfoUpdateDTO;

/**
 * 实测报告与户室面积对照表相关服务（查询、更新、创建、删除、刷新）
 */
public interface SurveyReportAndRoomService {

    AjaxJson getParsedSurveyReportInfoByProjectId(Long projectId);

    AjaxJson getRoomInfoByProjectIdAndSurveyReportId(Long projectId, Long surveyReportId);

    AjaxJson querySurveyReportInfos(SurveyReportInfoQueryDTO queryDTO);

    AjaxJson queryRoomInfos(RoomInfoQueryDTO queryDTO);

    AjaxJson updateSurveyReportInfo(SurveyReportInfoUpdateDTO updateDTO);

    AjaxJson createRoomInfo(RoomInfoCreateDTO createDTO);

    AjaxJson updateRoomInfo(RoomInfoUpdateDTO updateDTO);

    AjaxJson deleteRoomInfo(Long roomId);

    AjaxJson refreshSurveyReport(Long surveyReportId);
}
