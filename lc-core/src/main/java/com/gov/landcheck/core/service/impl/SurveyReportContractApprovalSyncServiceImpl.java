package com.gov.landcheck.core.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.gov.landcheck.core.bo.entity.ContractInfo;
import com.gov.landcheck.core.bo.entity.SurveyReportInfo;
import com.gov.landcheck.core.service.SurveyReportContractApprovalSyncService;

import lombok.extern.slf4j.Slf4j;

/**
 * 主合同合同编号：按 create_time、id 升序，取第一条「合同编号」非空的合同；多份非空且互不相等时打 WARN。
 */
@Slf4j
@Service
public class SurveyReportContractApprovalSyncServiceImpl implements SurveyReportContractApprovalSyncService {

    static final String CONTRACT_APPROVAL_NUMBER_FIELD = "contract_approval_number";

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public void syncAllSurveyReportsInProject(Long projectId) {
        if (projectId == null) {
            return;
        }
        String primary = resolvePrimaryContractNumberInternal(projectId);
        Query query = new Query(Criteria.where("project_id").is(projectId));
        Update update = new Update();
        if (StringUtils.hasText(primary)) {
            update.set(CONTRACT_APPROVAL_NUMBER_FIELD, primary.trim());
        } else {
            update.unset(CONTRACT_APPROVAL_NUMBER_FIELD);
        }
        mongoTemplate.updateMulti(query, update, SurveyReportInfo.class);
    }

    /**
     * 解析主合同编号；无则 null。
     */
    String resolvePrimaryContractNumberInternal(Long projectId) {
        Query contractQuery = new Query(Criteria.where("project_id").is(projectId));
        List<ContractInfo> contracts = mongoTemplate.find(contractQuery, ContractInfo.class);
        if (contracts == null || contracts.isEmpty()) {
            return null;
        }
        List<ContractInfo> sorted = new ArrayList<>(contracts);
        sorted.sort(Comparator
                .comparing(ContractInfo::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ContractInfo::getId, Comparator.nullsLast(Comparator.naturalOrder())));

        warnIfContractNumbersConflict(projectId, sorted);

        for (ContractInfo c : sorted) {
            if (c == null) {
                continue;
            }
            String n = c.getContractNumber();
            if (StringUtils.hasText(n)) {
                return n.trim();
            }
        }
        return null;
    }

    private void warnIfContractNumbersConflict(Long projectId, List<ContractInfo> sorted) {
        Set<String> distinct = new LinkedHashSet<>();
        List<String> idAndNum = new ArrayList<>();
        for (ContractInfo c : sorted) {
            if (c == null || c.getId() == null) {
                continue;
            }
            String n = c.getContractNumber();
            if (!StringUtils.hasText(n)) {
                continue;
            }
            String t = n.trim();
            distinct.add(t);
            idAndNum.add(c.getId() + "=" + t);
        }
        if (distinct.size() > 1) {
            log.warn(
                    "项目存在多份合同且合同编号不一致，实测报告「合同/批文编号」将仅同步为时间序首个非空编号。projectId={} contracts={}",
                    projectId, idAndNum);
        }
    }
}
