package com.gov.landcheck.project.service;

import com.gov.landcheck.core.bo.R.AjaxJson;
import com.gov.landcheck.project.dto.ProjectPartySummaryFormQueryDTO;
import com.gov.landcheck.project.dto.ProjectPartySummaryFormUpdateDTO;

/**
 * 项目方实测汇总主表 CRUD 与通用查询
 */
public interface ProjectPartySummaryService {

    AjaxJson queryProjectPartySummaryForms(ProjectPartySummaryFormQueryDTO queryDTO);

    AjaxJson updateProjectPartySummaryForm(ProjectPartySummaryFormUpdateDTO updateDTO);
}
