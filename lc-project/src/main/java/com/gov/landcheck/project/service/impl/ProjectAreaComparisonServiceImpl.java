package com.gov.landcheck.project.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.gov.landcheck.core.bo.entity.CapacityIndicatorInfo;
import com.gov.landcheck.core.bo.entity.PlanningReviewRow;
import com.gov.landcheck.core.bo.entity.ProjectPartyDeclaredTotals;
import com.gov.landcheck.core.bo.entity.ProjectPartySurveySummaryForm;
import com.gov.landcheck.core.bo.entity.SurveyReportInfo;
import com.gov.landcheck.project.service.ContractAndLandParcelService;
import com.gov.landcheck.project.service.ProjectAreaComparisonService;
import com.gov.landcheck.project.vo.AreaComparisonCompletenessVO;
import com.gov.landcheck.project.vo.AreaLineVO;
import com.gov.landcheck.project.vo.AreaTripleLinesVO;
import com.gov.landcheck.project.vo.ContractProjectStatsVO;
import com.gov.landcheck.project.vo.ProjectAreaComparisonVO;

import jakarta.annotation.Resource;

@Service
public class ProjectAreaComparisonServiceImpl implements ProjectAreaComparisonService {

    private static final BigDecimal EPSILON = new BigDecimal("0.01");

    @Resource
    private MongoTemplate mongoTemplate;

    @Resource
    private ContractAndLandParcelService contractAndLandParcelService;

    @Override
    public ProjectAreaComparisonVO buildComparison(Long projectId) {
        ContractProjectStatsVO contractStats = loadContractStats(projectId);
        AreaTripleLinesVO systemCalculated = buildSystemCalculated(projectId, contractStats);
        AreaTripleLinesVO projectPartyDeclared = buildProjectPartyDeclared(projectId, contractStats);
        AreaTripleLinesVO planningCalculated = buildPlanningCalculated(projectId, contractStats);
        AreaTripleLinesVO capacityIndicatorCalculated = buildCapacityIndicatorCalculated(projectId, contractStats);

        ProjectAreaComparisonVO vo = new ProjectAreaComparisonVO();
        vo.setSystemCalculated(systemCalculated);
        vo.setProjectPartyDeclared(projectPartyDeclared);
        vo.setPlanningCalculated(planningCalculated);
        vo.setCapacityIndicatorCalculated(capacityIndicatorCalculated);
        vo.setDataCompleteness(buildCompleteness(systemCalculated, projectPartyDeclared, planningCalculated,
                capacityIndicatorCalculated));
        vo.setConsistencyFlags(buildConsistencyFlags(systemCalculated, projectPartyDeclared, planningCalculated));
        return vo;
    }

    private ContractProjectStatsVO loadContractStats(Long projectId) {
        Map<Long, ContractProjectStatsVO> map = contractAndLandParcelService
                .batchGetContractProjectStatsByProjectIds(List.of(projectId));
        return map.get(projectId);
    }

    private AreaTripleLinesVO buildSystemCalculated(Long projectId, ContractProjectStatsVO contractStats) {
        Query query = new Query(Criteria.where("project_id").is(projectId));
        List<SurveyReportInfo> reports = mongoTemplate.find(query, SurveyReportInfo.class);
        if (reports == null || reports.isEmpty()) {
            return buildTriple(contractStats, null, null, null);
        }
        // 与前端 useSurveySummary.calcMeasuredByRule 一致：项目维度汇总后，将物管+其它计容按商/住基底比例摊分
        BigDecimal totalCommercial = sumOrZero(reports, SurveyReportInfo::getActualCommercialArea);
        BigDecimal totalResidential = sumOrZero(reports, SurveyReportInfo::getActualResidentialArea);
        BigDecimal totalManagement = sumOrZero(reports, SurveyReportInfo::getActualManagementRoomArea);
        BigDecimal totalOtherBuildable = sumOrZero(reports, SurveyReportInfo::getActualOtherBuildableArea);
        BigDecimal pooledArea = totalManagement.add(totalOtherBuildable);
        BigDecimal base = totalCommercial.add(totalResidential);

        BigDecimal pooledToCommercial = BigDecimal.ZERO;
        if (base.compareTo(BigDecimal.ZERO) > 0) {
            pooledToCommercial = pooledArea.multiply(totalCommercial).divide(base, 4, RoundingMode.HALF_UP);
        }
        BigDecimal pooledToResidential = pooledArea.subtract(pooledToCommercial);

        BigDecimal commercial = totalCommercial.add(pooledToCommercial).setScale(4, RoundingMode.HALF_UP);
        BigDecimal residential = totalResidential.add(pooledToResidential).setScale(4, RoundingMode.HALF_UP);
        // 建筑面积行仍优先使用各报告已落库的计容合计，与户室汇总口径一致；缺省时用分项加总回退
        BigDecimal buildableTotal = sum(reports, SurveyReportInfo::getTotalBuildableArea);
        if (buildableTotal == null) {
            buildableTotal = totalCommercial.add(totalResidential).add(pooledArea);
        }
        return buildTriple(contractStats, buildableTotal, commercial, residential);
    }

    private AreaTripleLinesVO buildProjectPartyDeclared(Long projectId, ContractProjectStatsVO contractStats) {
        Query query = new Query(Criteria.where("project_id").is(projectId))
                .with(Sort.by(Sort.Direction.DESC, "update_time"))
                .limit(1);
        ProjectPartySurveySummaryForm form = mongoTemplate.findOne(query, ProjectPartySurveySummaryForm.class);
        if (form == null) {
            return null;
        }
        ProjectPartyDeclaredTotals totals = form.getDeclaredTotals();
        if (totals == null) {
            totals = new ProjectPartyDeclaredTotals();
        }
        if (totals.getDifferenceTotalBuildingArea() == null) {
            totals.setDifferenceTotalBuildingArea(
                    diff(totals.getContractAgreedTotalBuildingArea(), totals.getBuildableTotalBuildingArea()));
        }
        if (totals.getDifferenceCommercialArea() == null) {
            totals.setDifferenceCommercialArea(
                    diff(totals.getContractAgreedCommercialArea(), totals.getBuildableCommercialArea()));
        }
        if (totals.getDifferenceResidentialArea() == null) {
            totals.setDifferenceResidentialArea(
                    diff(totals.getContractAgreedResidentialArea(), totals.getBuildableResidentialArea()));
        }

        AreaTripleLinesVO lines = new AreaTripleLinesVO();
        lines.setTotalBuilding(
                line(totals.getContractAgreedTotalBuildingArea(), totals.getBuildableTotalBuildingArea()));
        lines.setCommercial(line(totals.getContractAgreedCommercialArea(), totals.getBuildableCommercialArea()));
        lines.setResidential(line(totals.getContractAgreedResidentialArea(), totals.getBuildableResidentialArea()));

        // 若项目方文件缺合同约定值，回退到系统合同口径，保证对比可读性
        if (contractStats != null) {
            fillContractFallback(lines, contractStats.getTotalArea(), contractStats.getCommercialArea(),
                    contractStats.getResidentialArea());
        }
        return lines;
    }

    private AreaTripleLinesVO buildPlanningCalculated(Long projectId, ContractProjectStatsVO contractStats) {
        Query query = new Query(Criteria.where("project_id").is(projectId));
        List<PlanningReviewRow> rows = mongoTemplate.find(query, PlanningReviewRow.class);
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        BigDecimal buildableTotal = sum(rows, row -> add(row.getFarAboveGround(), row.getFarBelowGround()));
        BigDecimal commercial = sum(rows, row -> add(row.getNrAboveCommercial(), row.getNrBelowCommercial()));
        BigDecimal residential = sum(rows, row -> add(row.getResidentialResidentialArea(),
                row.getResidentialHotelApartmentArea(), row.getResidentialOtherArea()));
        return buildTriple(contractStats, buildableTotal, commercial, residential);
    }

    private AreaTripleLinesVO buildCapacityIndicatorCalculated(Long projectId, ContractProjectStatsVO contractStats) {
        Query query = new Query(Criteria.where("project_id").is(projectId))
                .with(Sort.by(Sort.Direction.DESC, "update_time"))
                .limit(1);
        CapacityIndicatorInfo info = mongoTemplate.findOne(query, CapacityIndicatorInfo.class);
        if (info == null) {
            return null;
        }
        return buildTriple(contractStats, info.getTotalArea(), info.getCommercialArea(), info.getResidentialArea());
    }

    private AreaTripleLinesVO buildTriple(ContractProjectStatsVO contractStats,
            BigDecimal buildableTotal,
            BigDecimal commercial,
            BigDecimal residential) {
        BigDecimal contractTotal = contractStats == null ? null : contractStats.getTotalArea();
        BigDecimal contractCommercial = contractStats == null ? null : contractStats.getCommercialArea();
        BigDecimal contractResidential = contractStats == null ? null : contractStats.getResidentialArea();

        AreaTripleLinesVO lines = new AreaTripleLinesVO();
        lines.setTotalBuilding(line(contractTotal, buildableTotal));
        lines.setCommercial(line(contractCommercial, commercial));
        lines.setResidential(line(contractResidential, residential));
        return lines;
    }

    private AreaLineVO line(BigDecimal contract, BigDecimal buildable) {
        AreaLineVO line = new AreaLineVO();
        line.setContractAgreedArea(contract);
        line.setBuildableArea(buildable);
        line.setDifference(diff(contract, buildable));
        return line;
    }

    private AreaComparisonCompletenessVO buildCompleteness(AreaTripleLinesVO system,
            AreaTripleLinesVO projectParty,
            AreaTripleLinesVO planning,
            AreaTripleLinesVO capacityIndicator) {
        AreaComparisonCompletenessVO completeness = new AreaComparisonCompletenessVO();
        completeness.setSystemCalculatedAvailable(hasAnyBuildable(system));
        completeness.setProjectPartyDeclaredAvailable(hasAnyBuildable(projectParty));
        completeness.setPlanningCalculatedAvailable(hasAnyBuildable(planning));
        completeness.setCapacityIndicatorCalculatedAvailable(hasAnyBuildable(capacityIndicator));
        return completeness;
    }

    private boolean hasAnyBuildable(AreaTripleLinesVO lines) {
        if (lines == null) {
            return false;
        }
        return (lines.getTotalBuilding() != null && lines.getTotalBuilding().getBuildableArea() != null)
                || (lines.getCommercial() != null && lines.getCommercial().getBuildableArea() != null)
                || (lines.getResidential() != null && lines.getResidential().getBuildableArea() != null);
    }

    private List<String> buildConsistencyFlags(AreaTripleLinesVO system,
            AreaTripleLinesVO projectParty,
            AreaTripleLinesVO planning) {
        List<String> flags = new ArrayList<>();
        compareLine(flags, "建筑面积", getBuildable(system, "total"), getBuildable(projectParty, "total"), "系统口径", "项目方声明");
        compareLine(flags, "商业面积", getBuildable(system, "commercial"), getBuildable(projectParty, "commercial"), "系统口径",
                "项目方声明");
        compareLine(flags, "住宅面积", getBuildable(system, "residential"), getBuildable(projectParty, "residential"),
                "系统口径", "项目方声明");

        compareLine(flags, "建筑面积", getBuildable(system, "total"), getBuildable(planning, "total"), "系统口径", "规划口径");
        compareLine(flags, "商业面积", getBuildable(system, "commercial"), getBuildable(planning, "commercial"), "系统口径",
                "规划口径");
        compareLine(flags, "住宅面积", getBuildable(system, "residential"), getBuildable(planning, "residential"), "系统口径",
                "规划口径");
        return flags;
    }

    private BigDecimal getBuildable(AreaTripleLinesVO lines, String key) {
        if (lines == null) {
            return null;
        }
        return switch (key) {
            case "total" -> lines.getTotalBuilding() == null ? null : lines.getTotalBuilding().getBuildableArea();
            case "commercial" -> lines.getCommercial() == null ? null : lines.getCommercial().getBuildableArea();
            case "residential" -> lines.getResidential() == null ? null : lines.getResidential().getBuildableArea();
            default -> null;
        };
    }

    private void compareLine(List<String> flags, String label,
            BigDecimal left, BigDecimal right,
            String leftName, String rightName) {
        if (left == null || right == null) {
            return;
        }
        BigDecimal delta = left.subtract(right).abs();
        if (delta.compareTo(EPSILON) > 0) {
            flags.add(label + leftName + "与" + rightName + "不一致，差值=" + delta);
        }
    }

    private <T> BigDecimal sum(List<T> rows, java.util.function.Function<T, BigDecimal> getter) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        boolean has = false;
        for (T row : rows) {
            BigDecimal value = getter.apply(row);
            if (value != null) {
                has = true;
                total = total.add(value);
            }
        }
        return has ? total : null;
    }

    /** 与前端 Number(x||0) 一致：缺失按 0，用于商/住摊分基底与池子汇总 */
    private BigDecimal sumOrZero(List<SurveyReportInfo> reports,
            java.util.function.Function<SurveyReportInfo, BigDecimal> getter) {
        if (reports == null || reports.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (SurveyReportInfo row : reports) {
            BigDecimal value = getter.apply(row);
            if (value != null) {
                total = total.add(value);
            }
        }
        return total;
    }

    private BigDecimal add(BigDecimal... values) {
        BigDecimal total = BigDecimal.ZERO;
        boolean has = false;
        for (BigDecimal value : values) {
            if (value != null) {
                has = true;
                total = total.add(value);
            }
        }
        return has ? total : null;
    }

    private BigDecimal diff(BigDecimal contract, BigDecimal buildable) {
        if (contract == null || buildable == null) {
            return null;
        }
        return contract.subtract(buildable);
    }

    private void fillContractFallback(AreaTripleLinesVO lines,
            BigDecimal totalContract,
            BigDecimal commercialContract,
            BigDecimal residentialContract) {
        if (lines.getTotalBuilding() != null && lines.getTotalBuilding().getContractAgreedArea() == null) {
            lines.getTotalBuilding().setContractAgreedArea(totalContract);
            lines.getTotalBuilding().setDifference(diff(totalContract, lines.getTotalBuilding().getBuildableArea()));
        }
        if (lines.getCommercial() != null && lines.getCommercial().getContractAgreedArea() == null) {
            lines.getCommercial().setContractAgreedArea(commercialContract);
            lines.getCommercial().setDifference(diff(commercialContract, lines.getCommercial().getBuildableArea()));
        }
        if (lines.getResidential() != null && lines.getResidential().getContractAgreedArea() == null) {
            lines.getResidential().setContractAgreedArea(residentialContract);
            lines.getResidential().setDifference(diff(residentialContract, lines.getResidential().getBuildableArea()));
        }
    }
}
