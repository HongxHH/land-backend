package com.gov.landcheck.project.service;

import com.gov.landcheck.core.bo.R.AjaxJson;
import com.gov.landcheck.project.dto.ProjectQueryDTO;
import com.gov.landcheck.project.dto.ProjectUpdateDTO;

/**
 * 项目相关服务（项目查询、创建、更新、刷新实测报告、删除）
 */
public interface ProjectService {

    AjaxJson getProjectById(Long projectId);


    AjaxJson queryProjects(ProjectQueryDTO queryDTO);

    AjaxJson queryProjectDetails(ProjectQueryDTO queryDTO);

    AjaxJson createProject(String projectName, String projectTime);

    AjaxJson updateProject(ProjectUpdateDTO updateDTO);

    AjaxJson refreshProjectSurveyReports(Long projectId);

    AjaxJson queryAreaComparison(Long projectId);

    AjaxJson deleteProject(Long projectId);
}
