package com.gov.landcheck.file.task.processor.receiver.parse.excel;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gov.landcheck.core.bo.entity.ProjectPartyDeclaredTotals;

import lombok.extern.slf4j.Slf4j;

/**
 * 校验并转换项目方汇总抽取 JSON（仅 totals）。
 */
@Slf4j
@Component
public class ProjectPartySummarySchemaValidator {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ValidatedResult validateAndConvert(String rawResponse) {
        return validateAndConvert(rawResponse, false);
    }

    /**
     * @param allowEmpty true 时允许 totals 全空（表示文件中确实无汇总数值，非解析失败）
     */
    public ValidatedResult validateAndConvert(String rawResponse, boolean allowEmpty) {
        try {
            String json = extractJson(rawResponse);
            JsonNode root = objectMapper.readTree(json);

            ProjectPartyDeclaredTotals totals = parseTotals(root.path("totals"));
            if (!hasAnyTotalValue(totals)) {
                if (allowEmpty) {
                    return ValidatedResult.validEmpty();
                }
                return ValidatedResult.invalid("totals为空，未提取到底部汇总数据");
            }
            return ValidatedResult.valid(totals);
        } catch (Exception ex) {
            log.warn("项目方汇总JSON校验失败: {}", ex.getMessage());
            return ValidatedResult.invalid("JSON解析失败: " + ex.getMessage());
        }
    }

    static boolean hasAnyTotalValue(ProjectPartyDeclaredTotals totals) {
        if (totals == null) {
            return false;
        }
        return totals.getContractAgreedTotalBuildingArea() != null
                || totals.getBuildableTotalBuildingArea() != null
                || totals.getDifferenceTotalBuildingArea() != null
                || totals.getContractAgreedCommercialArea() != null
                || totals.getBuildableCommercialArea() != null
                || totals.getDifferenceCommercialArea() != null
                || totals.getContractAgreedResidentialArea() != null
                || totals.getBuildableResidentialArea() != null
                || totals.getDifferenceResidentialArea() != null;
    }

    private ProjectPartyDeclaredTotals parseTotals(JsonNode totalsNode) {
        ProjectPartyDeclaredTotals totals = new ProjectPartyDeclaredTotals();
        if (totalsNode == null || totalsNode.isMissingNode()) {
            return totals;
        }
        totals.setContractAgreedTotalBuildingArea(
                parseNumberNode(totalsNode.path("contract_agreed_total_building_area")));
        totals.setBuildableTotalBuildingArea(parseNumberNode(totalsNode.path("buildable_total_building_area")));
        totals.setDifferenceTotalBuildingArea(parseNumberNode(totalsNode.path("difference_total_building_area")));
        totals.setContractAgreedCommercialArea(parseNumberNode(totalsNode.path("contract_agreed_commercial_area")));
        totals.setBuildableCommercialArea(parseNumberNode(totalsNode.path("buildable_commercial_area")));
        totals.setDifferenceCommercialArea(parseNumberNode(totalsNode.path("difference_commercial_area")));
        totals.setContractAgreedResidentialArea(parseNumberNode(totalsNode.path("contract_agreed_residential_area")));
        totals.setBuildableResidentialArea(parseNumberNode(totalsNode.path("buildable_residential_area")));
        totals.setDifferenceResidentialArea(parseNumberNode(totalsNode.path("difference_residential_area")));
        return totals;
    }

    private String extractJson(String response) {
        if (!StringUtils.hasText(response)) {
            return "{}";
        }
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return "{}";
        }
        return response.substring(start, end + 1);
    }

    private BigDecimal parseNumberNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        return parseFlexibleNumber(node.asText());
    }

    private BigDecimal parseFlexibleNumber(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String normalized = raw.trim()
                .replace("㎡", "")
                .replace("m²", "")
                .replace("，", ",")
                .replace(",", "")
                .replace(" ", "");
        if ("--".equals(normalized) || "-".equals(normalized) || "null".equalsIgnoreCase(normalized)) {
            return null;
        }
        if (normalized.startsWith("(") && normalized.endsWith(")")) {
            normalized = "-" + normalized.substring(1, normalized.length() - 1);
        }
        return ProjectPartySummaryNumberParser.parseFlexibleNumber(normalized);
    }

    public record ValidatedResult(
            boolean valid,
            boolean empty,
            String errorMessage,
            ProjectPartyDeclaredTotals totals) {
        public static ValidatedResult invalid(String message) {
            return new ValidatedResult(false, false, message, new ProjectPartyDeclaredTotals());
        }

        public static ValidatedResult valid(ProjectPartyDeclaredTotals totals) {
            return new ValidatedResult(true, false, null, totals);
        }

        public static ValidatedResult validEmpty() {
            return new ValidatedResult(true, true, null, null);
        }
    }
}
