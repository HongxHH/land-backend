package com.gov.landcheck.project.service;

import com.gov.landcheck.project.vo.ProjectAreaComparisonVO;

public interface ProjectAreaComparisonService {

    ProjectAreaComparisonVO buildComparison(Long projectId);
}
