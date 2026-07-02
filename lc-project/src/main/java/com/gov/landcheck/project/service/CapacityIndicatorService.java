package com.gov.landcheck.project.service;

import com.gov.landcheck.core.bo.R.AjaxJson;
import com.gov.landcheck.project.dto.CapacityIndicatorFormQueryDTO;
import com.gov.landcheck.project.dto.CapacityIndicatorFormUpdateDTO;

/**
 * 容量指标核查表 CRUD 与通用查询
 */
public interface CapacityIndicatorService {

    AjaxJson queryCapacityIndicatorForms(CapacityIndicatorFormQueryDTO queryDTO);

    AjaxJson updateCapacityIndicatorForm(CapacityIndicatorFormUpdateDTO updateDTO);
}
