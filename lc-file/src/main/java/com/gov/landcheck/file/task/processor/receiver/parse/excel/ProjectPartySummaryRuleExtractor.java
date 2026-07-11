package com.gov.landcheck.file.task.processor.receiver.parse.excel;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.gov.landcheck.core.bo.entity.ProjectPartyDeclaredTotals;
import com.gov.landcheck.file.task.processor.receiver.parse.excel.ProjectPartySummaryLabelSynonyms.AreaCategory;
import com.gov.landcheck.file.task.processor.receiver.parse.excel.ProjectPartySummaryLabelSynonyms.RowRole;

/**
 * 规则抽取项目方汇总 declared totals（模糊标签 + 同行 label-value 配对）。
 */
@Component
public class ProjectPartySummaryRuleExtractor {

    public RuleResult extract(ProjectPartySummaryRegion region) {
        if (region == null) {
            return RuleResult.failed("汇总区块为空");
        }
        ProjectPartyDeclaredTotals totals = new ProjectPartyDeclaredTotals();
        ProjectPartySummarySheetMatrix sheet = region.sheet();

        int categoryRows = 0;
        Set<RowRole> rolesSeen = EnumSet.noneOf(RowRole.class);

        for (int row = region.startRow(); row <= region.endRow(); row++) {
            if (assignOrphanDifferenceRow(sheet, region, row, totals)) {
                continue;
            }
            String rowText = sheet.rowText(row);
            if (!org.springframework.util.StringUtils.hasText(rowText)) {
                continue;
            }
            if (isHeaderOnlyRow(rowText)) {
                continue;
            }
            AreaCategory rowCategory = ProjectPartySummaryLabelSynonyms.detectAreaCategory(rowText);
            if (ProjectPartySummaryLabelSynonyms.isSummaryCategoryRow(rowText)) {
                categoryRows++;
            }

            for (int col = region.startCol(); col <= region.endCol(); col++) {
                String cell = sheet.cellAt(row, col);
                if (!org.springframework.util.StringUtils.hasText(cell)) {
                    continue;
                }
                if (ProjectPartySummaryLabelSynonyms.isNumericLike(cell)) {
                    continue;
                }
                RowRole role = ProjectPartySummaryLabelSynonyms.detectRowRole(cell);
                AreaCategory category = ProjectPartySummaryLabelSynonyms.detectAreaCategory(cell);
                if (category == AreaCategory.UNKNOWN) {
                    category = rowCategory;
                }
                if (role == RowRole.UNKNOWN && category != AreaCategory.UNKNOWN) {
                    role = RowRole.CONTRACT;
                }
                if (role == RowRole.UNKNOWN) {
                    continue;
                }
                rolesSeen.add(role);

                BigDecimal value = findNextNumeric(sheet, row, col + 1, region.endCol());
                if (value == null) {
                    continue;
                }
                assignValue(totals, category, role, value);
            }
        }

        stripDifferenceWithoutContractOrBuildable(totals);
        Set<AreaCategory> categoriesForGroups = categoriesWithAnyValueFromTotals(totals);

        boolean threeCategoryRows = categoryRows >= 3;
        boolean threeRoles = rolesSeen.contains(RowRole.CONTRACT)
                && rolesSeen.contains(RowRole.BUILDABLE)
                && rolesSeen.contains(RowRole.DIFFERENCE);
        boolean groupsOkFull = categoriesForGroups.contains(AreaCategory.TOTAL_BUILDING)
                && categoriesForGroups.contains(AreaCategory.COMMERCIAL)
                && categoriesForGroups.contains(AreaCategory.RESIDENTIAL);
        /** 仅声明建筑面积：商/住无任何字段，且建筑同时具备合同约定与计容两项（差值可为空或由后续行补全）。 */
        boolean groupsOkBuildingOnly = categoriesForGroups.size() == 1
                && categoriesForGroups.contains(AreaCategory.TOTAL_BUILDING)
                && totals.getContractAgreedTotalBuildingArea() != null
                && totals.getBuildableTotalBuildingArea() != null;
        boolean groupsOk = groupsOkFull || groupsOkBuildingOnly;

        double confidence = region.confidence();
        if (threeCategoryRows) {
            confidence = Math.min(1.0, confidence + 0.1);
        }
        if (groupsOkFull) {
            confidence = Math.min(1.0, confidence + 0.15);
        } else if (groupsOkBuildingOnly) {
            confidence = Math.min(1.0, confidence + 0.05);
        }

        if (!threeCategoryRows && !threeRoles) {
            return RuleResult.failed("未识别到完整三行汇总结构（categoryRows=" + categoryRows + ", roles=" + rolesSeen + ")");
        }
        if (!groupsOk) {
            return RuleResult.failed("建筑/商业/住宅三组未均提取到数值: " + categoriesForGroups, totals);
        }
        if (confidence < ProjectPartySummaryParseConstants.RULE_MIN_CONFIDENCE) {
            return RuleResult.failed("规则置信度不足: " + confidence);
        }
        if (!hasAnyTotalValue(totals)) {
            return RuleResult.failed("未提取到有效汇总数值");
        }
        return RuleResult.success(totals, confidence);
    }

    /**
     * 部分模板将三列差值放在独立一行（仅数字、无「差值」字样）。至少三个数字才按列映射为建筑/商业/住宅差值，避免两列备注数字误判。
     */
    private static boolean assignOrphanDifferenceRow(
            ProjectPartySummarySheetMatrix sheet,
            ProjectPartySummaryRegion region,
            int row,
            ProjectPartyDeclaredTotals totals) {
        String rowText = sheet.rowText(row);
        if (!org.springframework.util.StringUtils.hasText(rowText)) {
            return false;
        }
        if (rowText.contains("合同") || rowText.contains("计容") || rowText.contains("差值")) {
            return false;
        }
        java.util.List<BigDecimal> numericValues = new java.util.ArrayList<>();
        for (int col = region.startCol(); col <= region.endCol(); col++) {
            String cell = sheet.cellAt(row, col);
            if (!ProjectPartySummaryLabelSynonyms.isNumericLike(cell)) {
                continue;
            }
            BigDecimal parsed = ProjectPartySummaryNumberParser.parseFlexibleNumber(cell);
            if (parsed == null) {
                continue;
            }
            numericValues.add(parsed);
        }
        if (numericValues.size() < 3) {
            return false;
        }
        AreaCategory[] order = { AreaCategory.TOTAL_BUILDING, AreaCategory.COMMERCIAL, AreaCategory.RESIDENTIAL };
        for (int i = 0; i < numericValues.size() && i < order.length; i++) {
            assignValue(totals, order[i], RowRole.DIFFERENCE, numericValues.get(i));
        }
        return true;
    }

    /**
     * 差值语义依赖「合同约定 / 计容」至少一侧；无基底时丢弃差值，避免孤儿数字行污染商/住差值。
     */
    private static void stripDifferenceWithoutContractOrBuildable(ProjectPartyDeclaredTotals totals) {
        if (totals == null) {
            return;
        }
        if (totals.getContractAgreedTotalBuildingArea() == null && totals.getBuildableTotalBuildingArea() == null) {
            totals.setDifferenceTotalBuildingArea(null);
        }
        if (totals.getContractAgreedCommercialArea() == null && totals.getBuildableCommercialArea() == null) {
            totals.setDifferenceCommercialArea(null);
        }
        if (totals.getContractAgreedResidentialArea() == null && totals.getBuildableResidentialArea() == null) {
            totals.setDifferenceResidentialArea(null);
        }
    }

    private static Set<AreaCategory> categoriesWithAnyValueFromTotals(ProjectPartyDeclaredTotals totals) {
        Set<AreaCategory> set = EnumSet.noneOf(AreaCategory.class);
        if (totals.getContractAgreedTotalBuildingArea() != null
                || totals.getBuildableTotalBuildingArea() != null
                || totals.getDifferenceTotalBuildingArea() != null) {
            set.add(AreaCategory.TOTAL_BUILDING);
        }
        if (totals.getContractAgreedCommercialArea() != null
                || totals.getBuildableCommercialArea() != null
                || totals.getDifferenceCommercialArea() != null) {
            set.add(AreaCategory.COMMERCIAL);
        }
        if (totals.getContractAgreedResidentialArea() != null
                || totals.getBuildableResidentialArea() != null
                || totals.getDifferenceResidentialArea() != null) {
            set.add(AreaCategory.RESIDENTIAL);
        }
        return set;
    }

    private static boolean isHeaderOnlyRow(String rowText) {
        String n = ProjectPartySummaryLabelSynonyms.normalize(rowText);
        return (n.contains("a-b") || n.equals("ab")) && !n.contains("合同") && !n.contains("计容");
    }

    private static BigDecimal findNextNumeric(ProjectPartySummarySheetMatrix sheet, int row, int fromCol, int endCol) {
        for (int col = fromCol; col <= endCol; col++) {
            String cell = sheet.cellAt(row, col);
            if (!org.springframework.util.StringUtils.hasText(cell)) {
                continue;
            }
            if (ProjectPartySummaryLabelSynonyms.isNumericLike(cell)) {
                return ProjectPartySummaryNumberParser.parseFlexibleNumber(cell);
            }
            if (ProjectPartySummaryLabelSynonyms.detectRowRole(cell) != RowRole.UNKNOWN
                    || ProjectPartySummaryLabelSynonyms.detectAreaCategory(cell) != AreaCategory.UNKNOWN) {
                break;
            }
        }
        return null;
    }

    private static void assignValue(ProjectPartyDeclaredTotals totals, AreaCategory category, RowRole role,
            BigDecimal value) {
        if (category == AreaCategory.UNKNOWN || role == RowRole.UNKNOWN || value == null) {
            return;
        }
        switch (category) {
            case TOTAL_BUILDING -> {
                switch (role) {
                    case CONTRACT -> totals.setContractAgreedTotalBuildingArea(value);
                    case BUILDABLE -> totals.setBuildableTotalBuildingArea(value);
                    case DIFFERENCE -> totals.setDifferenceTotalBuildingArea(value);
                    default -> {
                    }
                }
            }
            case COMMERCIAL -> {
                switch (role) {
                    case CONTRACT -> totals.setContractAgreedCommercialArea(value);
                    case BUILDABLE -> totals.setBuildableCommercialArea(value);
                    case DIFFERENCE -> totals.setDifferenceCommercialArea(value);
                    default -> {
                    }
                }
            }
            case RESIDENTIAL -> {
                switch (role) {
                    case CONTRACT -> totals.setContractAgreedResidentialArea(value);
                    case BUILDABLE -> totals.setBuildableResidentialArea(value);
                    case DIFFERENCE -> totals.setDifferenceResidentialArea(value);
                    default -> {
                    }
                }
            }
            default -> {
            }
        }
    }

    static boolean hasAnyTotalValue(ProjectPartyDeclaredTotals totals) {
        return totals != null && totals.hasAnyDeclaredField();
    }

    public record RuleResult(
            boolean success,
            String reason,
            ProjectPartyDeclaredTotals totals,
            double confidence) {
        public static RuleResult failed(String reason) {
            return failed(reason, new ProjectPartyDeclaredTotals());
        }

        public static RuleResult failed(String reason, ProjectPartyDeclaredTotals totals) {
            return new RuleResult(false, reason,
                    totals != null ? totals : new ProjectPartyDeclaredTotals(), 0);
        }

        public static RuleResult success(ProjectPartyDeclaredTotals totals, double confidence) {
            return new RuleResult(true, null, totals, confidence);
        }
    }
}
