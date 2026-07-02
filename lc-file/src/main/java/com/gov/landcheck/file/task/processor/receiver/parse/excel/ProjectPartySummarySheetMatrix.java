package com.gov.landcheck.file.task.processor.receiver.parse.excel;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.util.StringUtils;

/**
 * Excel sheet 二维矩阵（合并单元格已展开）。
 */
public record ProjectPartySummarySheetMatrix(
        String sheetName,
        String[][] cells,
        int firstRowNum) {

    public int rowCount() {
        return cells.length;
    }

    public int colCount() {
        return cells.length == 0 ? 0 : cells[0].length;
    }

    public String cellAt(int rowIndex, int colIndex) {
        if (rowIndex < 0 || rowIndex >= cells.length || colIndex < 0 || colIndex >= colCount()) {
            return "";
        }
        String value = cells[rowIndex][colIndex];
        return value == null ? "" : value.trim();
    }

    /** 1-based Excel 行号 */
    public int excelRowNum(int rowIndex) {
        return firstRowNum + rowIndex + 1;
    }

    public String rowText(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= cells.length) {
            return "";
        }
        return IntStream.range(0, colCount())
                .mapToObj(col -> cellAt(rowIndex, col))
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(" "));
    }

    public List<Integer> nonEmptyRowIndexes() {
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < cells.length; i++) {
            if (StringUtils.hasText(rowText(i))) {
                indexes.add(i);
            }
        }
        return indexes;
    }

    public boolean isEmptySheet() {
        return nonEmptyRowIndexes().isEmpty();
    }
}
