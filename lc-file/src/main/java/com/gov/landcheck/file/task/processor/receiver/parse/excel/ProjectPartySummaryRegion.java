package com.gov.landcheck.file.task.processor.receiver.parse.excel;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.util.StringUtils;

import com.gov.landcheck.file.task.processor.receiver.parse.excel.ProjectPartySummaryLabelSynonyms.RowRole;

/**
 * 项目方汇总锚点区块（矩阵内 0-based 索引，含 sheet 引用）。
 */
public record ProjectPartySummaryRegion(
        ProjectPartySummarySheetMatrix sheet,
        int startRow,
        int endRow,
        int startCol,
        int endCol,
        double confidence,
        int categoryRowCount) {

    public String sheetName() {
        return sheet.sheetName();
    }

    /**
     * 片段中是否可能存在「项目方声明汇总」三行结构；用于决定是否调用 LLM，避免在无汇总表时从明细行幻觉抽取。
     */
    public boolean hasLikelySummaryBlock() {
        if (categoryRowCount >= 2) {
            return true;
        }
        Set<RowRole> roles = EnumSet.noneOf(RowRole.class);
        for (int row = startRow; row <= endRow; row++) {
            String rowText = sheet.rowText(row);
            if (!StringUtils.hasText(rowText)) {
                continue;
            }
            RowRole role = ProjectPartySummaryLabelSynonyms.detectRowRole(rowText);
            if (role != RowRole.UNKNOWN) {
                roles.add(role);
            }
        }
        return roles.contains(RowRole.CONTRACT)
                && roles.contains(RowRole.BUILDABLE)
                && roles.contains(RowRole.DIFFERENCE);
    }

    public String toPromptText() {
        StringBuilder sb = new StringBuilder(512);
        sb.append("## SHEET: ").append(sheet.sheetName()).append('\n');
        for (int row = startRow; row <= endRow; row++) {
            sb.append("R").append(sheet.excelRowNum(row)).append(": ");
            boolean any = false;
            for (int col = startCol; col <= endCol; col++) {
                String value = sheet.cellAt(row, col);
                if (!StringUtils.hasText(value)) {
                    continue;
                }
                any = true;
                sb.append("C").append(col + 1).append("=").append(value).append(" | ");
            }
            if (any) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }
}
