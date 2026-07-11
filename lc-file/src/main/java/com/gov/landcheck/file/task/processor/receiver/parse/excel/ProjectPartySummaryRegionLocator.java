package com.gov.landcheck.file.task.processor.receiver.parse.excel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * 锚点定位项目方汇总底部三行区块。
 */
@Slf4j
@Component
public class ProjectPartySummaryRegionLocator {

    private static final Pattern SUMMARY_SHEET_NAME = Pattern.compile("汇总|声明|合计|实测");

    public ProjectPartySummaryRegion locate(List<ProjectPartySummarySheetMatrix> matrices) {
        if (matrices == null || matrices.isEmpty()) {
            throw new IllegalStateException("Excel 无有效工作表");
        }
        List<ProjectPartySummarySheetMatrix> ordered = orderSheets(matrices);
        ProjectPartySummaryRegion best = null;
        for (ProjectPartySummarySheetMatrix sheet : ordered) {
            ProjectPartySummaryRegion candidate = locateInSheet(sheet, true);
            if (candidate != null && (best == null || candidate.confidence() > best.confidence())) {
                best = candidate;
            }
            if (best != null && best.confidence() >= 0.9) {
                break;
            }
            candidate = locateInSheet(sheet, false);
            if (candidate != null && (best == null || candidate.confidence() > best.confidence())) {
                best = candidate;
            }
        }
        if (best == null) {
            best = fallbackTailRegion(ordered.get(ordered.size() - 1));
        }
        if (best == null) {
            throw new IllegalStateException("未定位到项目方声明汇总区块");
        }
        log.info("项目方汇总区块定位: sheet={}, rows=R{}-R{}, cols=C{}-C{}, confidence={}, categoryRows={}",
                best.sheetName(),
                best.sheet().excelRowNum(best.startRow()),
                best.sheet().excelRowNum(best.endRow()),
                best.startCol() + 1,
                best.endCol() + 1,
                best.confidence(),
                best.categoryRowCount());
        return best;
    }

    private List<ProjectPartySummarySheetMatrix> orderSheets(List<ProjectPartySummarySheetMatrix> matrices) {
        List<ProjectPartySummarySheetMatrix> named = new ArrayList<>();
        List<ProjectPartySummarySheetMatrix> others = new ArrayList<>();
        for (ProjectPartySummarySheetMatrix matrix : matrices) {
            if (SUMMARY_SHEET_NAME.matcher(matrix.sheetName()).find()) {
                named.add(matrix);
            } else {
                others.add(matrix);
            }
        }
        List<ProjectPartySummarySheetMatrix> ordered = new ArrayList<>(named);
        if (!others.isEmpty()) {
            ordered.add(others.get(others.size() - 1));
        }
        for (ProjectPartySummarySheetMatrix matrix : others.subList(0, Math.max(0, others.size() - 1))) {
            if (!ordered.contains(matrix)) {
                ordered.add(matrix);
            }
        }
        return ordered;
    }

    private ProjectPartySummaryRegion locateInSheet(ProjectPartySummarySheetMatrix sheet, boolean tailOnly) {
        List<Integer> rows = sheet.nonEmptyRowIndexes();
        if (rows.isEmpty()) {
            return null;
        }
        int scanFrom = tailOnly
                ? Math.max(0, rows.get(rows.size() - 1) - ProjectPartySummaryParseConstants.TAIL_SCAN_ROWS)
                : 0;
        List<CandidateBlock> blocks = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i) < scanFrom) {
                continue;
            }
            List<Integer> consecutive = new ArrayList<>();
            consecutive.add(rows.get(i));
            for (int j = i + 1; j < rows.size() && rows.get(j) - rows.get(j - 1) <= 2; j++) {
                consecutive.add(rows.get(j));
            }
            if (consecutive.size() >= 3) {
                for (int start = 0; start <= consecutive.size() - 3; start++) {
                    int end = start + 2;
                    blocks.add(buildBlock(sheet, consecutive.get(start), consecutive.get(end)));
                }
            }
        }
        ProjectPartySummaryRegion region = blocks.stream()
                .map(CandidateBlock::toRegion)
                .filter(r -> r.confidence() > 0)
                .max(Comparator.comparingDouble(ProjectPartySummaryRegion::confidence))
                .orElse(null);
        if (region != null) {
            int last = sheet.rowCount() - 1;
            boolean tooHighInSheet = region.endRow() < last - ProjectPartySummaryParseConstants.TAIL_SCAN_ROWS;
            boolean headerLikeBlock = region.categoryRowCount() < 3 && region.endRow() < sheet.rowCount() / 5;
            if ((tooHighInSheet && region.confidence() < 0.85) || headerLikeBlock) {
                ProjectPartySummaryRegion tail = fallbackTailRegion(sheet);
                if (tail != null) {
                    return tail;
                }
            }
        }
        return region;
    }

    private CandidateBlock buildBlock(ProjectPartySummarySheetMatrix sheet, int startRow, int endRow) {
        int categoryRows = 0;
        int roleContract = 0;
        int roleBuildable = 0;
        int roleDifference = 0;
        boolean strongAnchor = false;
        int minCol = Integer.MAX_VALUE;
        int maxCol = -1;
        boolean hasNumber = false;

        for (int row = startRow; row <= endRow; row++) {
            String rowText = sheet.rowText(row);
            if (ProjectPartySummaryLabelSynonyms.containsStrongAnchor(rowText)) {
                strongAnchor = true;
            }
            if (ProjectPartySummaryLabelSynonyms.isSummaryCategoryRow(rowText)) {
                categoryRows++;
            }
            ProjectPartySummaryLabelSynonyms.RowRole role = ProjectPartySummaryLabelSynonyms.detectRowRole(rowText);
            switch (role) {
                case CONTRACT -> roleContract++;
                case BUILDABLE -> roleBuildable++;
                case DIFFERENCE -> roleDifference++;
                default -> {
                }
            }
            for (int col = 0; col < sheet.colCount(); col++) {
                String cell = sheet.cellAt(row, col);
                if (!StringUtils.hasText(cell)) {
                    continue;
                }
                minCol = Math.min(minCol, col);
                maxCol = Math.max(maxCol, col);
                if (ProjectPartySummaryNumberParser.parseFlexibleNumber(cell) != null) {
                    hasNumber = true;
                }
            }
        }

        double confidence = 0;
        if (categoryRows >= 3) {
            confidence += 0.55;
        } else if (categoryRows >= 2) {
            confidence += 0.35;
        }
        if (roleContract > 0 && roleBuildable > 0) {
            confidence += 0.2;
        }
        if (roleDifference > 0) {
            confidence += 0.05;
        }
        if (strongAnchor) {
            confidence += 0.15;
        }
        if (hasNumber) {
            confidence += 0.15;
        }
        int lastRowIndex = sheet.rowCount() - 1;
        int distanceFromBottom = lastRowIndex - endRow;
        if (distanceFromBottom <= ProjectPartySummaryParseConstants.TAIL_SCAN_ROWS) {
            confidence += 0.25 * (1.0 - ((double) distanceFromBottom
                    / Math.max(1, ProjectPartySummaryParseConstants.TAIL_SCAN_ROWS)));
        } else {
            confidence -= 0.25;
        }
        if (minCol == Integer.MAX_VALUE) {
            minCol = 0;
            maxCol = sheet.colCount() - 1;
        }
        int expand = ProjectPartySummaryParseConstants.REGION_EXPAND_ROWS;
        int regionStart = Math.max(0, startRow - expand);
        int regionEnd = Math.min(sheet.rowCount() - 1, endRow + expand);
        int regionStartCol = Math.max(0, minCol - 1);
        int regionEndCol = Math.min(sheet.colCount() - 1, maxCol + 1);
        return new CandidateBlock(sheet, regionStart, regionEnd, regionStartCol, regionEndCol,
                Math.min(1.0, confidence), categoryRows);
    }

    private ProjectPartySummaryRegion fallbackTailRegion(ProjectPartySummarySheetMatrix sheet) {
        List<Integer> rows = sheet.nonEmptyRowIndexes();
        if (rows.isEmpty()) {
            return null;
        }
        int last = rows.get(rows.size() - 1);
        int start = Math.max(0, last - ProjectPartySummaryParseConstants.TAIL_SCAN_ROWS);
        log.warn("项目方汇总未命中锚点，回退尾部窗口: sheet={}, fromRow=R{}", sheet.sheetName(), sheet.excelRowNum(start));
        int endCol = Math.max(0, Math.min(sheet.colCount() - 1, 30));
        return new ProjectPartySummaryRegion(sheet, start, last, 0, endCol, 0.25, 0);
    }

    private record CandidateBlock(
            ProjectPartySummarySheetMatrix sheet,
            int startRow,
            int endRow,
            int startCol,
            int endCol,
            double confidence,
            int categoryRowCount) {
        ProjectPartySummaryRegion toRegion() {
            return new ProjectPartySummaryRegion(sheet, startRow, endRow, startCol, endCol, confidence,
                    categoryRowCount);
        }
    }
}
