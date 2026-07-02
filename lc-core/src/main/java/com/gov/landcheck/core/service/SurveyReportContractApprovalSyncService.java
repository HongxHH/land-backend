package com.gov.landcheck.core.service;

/**
 * 将项目「主合同」合同编号同步到该项目下所有
 * {@link com.gov.landcheck.core.bo.entity.SurveyReportInfo} 的冗余字段。
 */
public interface SurveyReportContractApprovalSyncService {

    /**
     * 按主合同规则解析合同编号，并对该项目下全部实测报告执行 updateMulti（有值 set，无值 unset）。
     *
     * @param projectId 项目 ID，null 时直接返回
     */
    void syncAllSurveyReportsInProject(Long projectId);
}
