package com.gov.landcheck.file.task.processor.receiver.parse.excel;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

import com.gov.landcheck.core.bo.entity.ProjectPartyDeclaredTotals;

/**
 * 校验 LLM/规则抽取的数值是否能在 Excel 锚点区块中找到对应单元格，防止幻觉编造。
 */
@Component
public class ProjectPartySummaryGroundingValidator {

    public GroundingResult validate(ProjectPartyDeclaredTotals totals, ProjectPartySummaryRegion region) {
        if (totals == null) {
            return GroundingResult.ok();
        }
        if (region == null) {
            return GroundingResult.fail("汇总区块为空，无法校验数值来源");
        }

        Set<BigDecimal> regionNumbers = collectRegionNumbers(region);
        List<String> ungrounded = new ArrayList<>();

        checkField(ungrounded, "contract_agreed_total_building_area",
                totals.getContractAgreedTotalBuildingArea(), regionNumbers);
        checkField(ungrounded, "buildable_total_building_area",
                totals.getBuildableTotalBuildingArea(), regionNumbers);
        checkField(ungrounded, "difference_total_building_area",
                totals.getDifferenceTotalBuildingArea(), regionNumbers);
        checkField(ungrounded, "contract_agreed_commercial_area",
                totals.getContractAgreedCommercialArea(), regionNumbers);
        checkField(ungrounded, "buildable_commercial_area",
                totals.getBuildableCommercialArea(), regionNumbers);
        checkField(ungrounded, "difference_commercial_area",
                totals.getDifferenceCommercialArea(), regionNumbers);
        checkField(ungrounded, "contract_agreed_residential_area",
                totals.getContractAgreedResidentialArea(), regionNumbers);
        checkField(ungrounded, "buildable_residential_area",
                totals.getBuildableResidentialArea(), regionNumbers);
        checkField(ungrounded, "difference_residential_area",
                totals.getDifferenceResidentialArea(), regionNumbers);

        if (ungrounded.isEmpty()) {
            return GroundingResult.ok();
        }
        return GroundingResult.fail("以下字段数值在 Excel 片段中未找到，疑似编造: " + String.join("、", ungrounded));
    }

    private static void checkField(
            List<String> ungrounded,
            String fieldName,
            BigDecimal value,
            Set<BigDecimal> regionNumbers) {
        if (value == null) {
            return;
        }
        if (!matchesRegionNumber(value, regionNumbers)) {
            ungrounded.add(fieldName + "=" + value);
        }
    }

    private static Set<BigDecimal> collectRegionNumbers(ProjectPartySummaryRegion region) {
        Set<BigDecimal> numbers = new HashSet<>();
        ProjectPartySummarySheetMatrix sheet = region.sheet();
        for (int row = region.startRow(); row <= region.endRow(); row++) {
            for (int col = region.startCol(); col <= region.endCol(); col++) {
                BigDecimal parsed = ProjectPartySummaryNumberParser.parseFlexibleNumber(sheet.cellAt(row, col));
                if (parsed != null) {
                    numbers.add(normalize(parsed));
                }
            }
        }
        return numbers;
    }

    private static boolean matchesRegionNumber(BigDecimal value, Set<BigDecimal> regionNumbers) {
        BigDecimal normalized = normalize(value);
        for (BigDecimal candidate : regionNumbers) {
            if (normalized.compareTo(candidate) == 0) {
                return true;
            }
        }
        return false;
    }

    private static BigDecimal normalize(BigDecimal value) {
        return value.stripTrailingZeros();
    }

    public record GroundingResult(boolean grounded, String errorMessage) {
        public static GroundingResult ok() {
            return new GroundingResult(true, null);
        }

        public static GroundingResult fail(String message) {
            return new GroundingResult(false, message);
        }
    }
}
