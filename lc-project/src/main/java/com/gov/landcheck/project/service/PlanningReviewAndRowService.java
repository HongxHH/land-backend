package com.gov.landcheck.project.service;

import com.gov.landcheck.core.bo.R.AjaxJson;
import com.gov.landcheck.project.dto.PlanningReviewFormQueryDTO;
import com.gov.landcheck.project.dto.PlanningReviewFormUpdateDTO;
import com.gov.landcheck.project.dto.PlanningReviewRowCreateDTO;
import com.gov.landcheck.project.dto.PlanningReviewRowQueryDTO;
import com.gov.landcheck.project.dto.PlanningReviewRowUpdateDTO;

/**
 * 规划复核表主表与子行 CRUD 与通用查询
 */
public interface PlanningReviewAndRowService {

    AjaxJson queryPlanningReviewForms(PlanningReviewFormQueryDTO queryDTO);

    AjaxJson queryPlanningReviewRows(PlanningReviewRowQueryDTO queryDTO);

    AjaxJson getPlanningReviewRowsByProjectAndFormId(Long projectId, Long formId);

    AjaxJson updatePlanningReviewForm(PlanningReviewFormUpdateDTO updateDTO);

    AjaxJson createPlanningReviewRow(PlanningReviewRowCreateDTO createDTO);

    AjaxJson updatePlanningReviewRow(PlanningReviewRowUpdateDTO updateDTO);

    AjaxJson deletePlanningReviewRow(Long rowId);
}
