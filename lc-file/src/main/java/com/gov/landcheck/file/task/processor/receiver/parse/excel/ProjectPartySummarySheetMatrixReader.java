package com.gov.landcheck.file.task.processor.receiver.parse.excel;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 将 Excel 工作簿读为带合并展开的 Sheet 矩阵。
 */
@Component
public class ProjectPartySummarySheetMatrixReader {

    private final DataFormatter dataFormatter = new DataFormatter();

    public List<ProjectPartySummarySheetMatrix> read(byte[] workbookBytes) throws Exception {
        if (workbookBytes == null || workbookBytes.length == 0) {
            return List.of();
        }
        List<ProjectPartySummarySheetMatrix> matrices = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(workbookBytes))) {
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                if (sheet == null) {
                    continue;
                }
                ProjectPartySummarySheetMatrix matrix = readSheet(sheet, evaluator);
                if (!matrix.isEmptySheet()) {
                    matrices.add(matrix);
                }
            }
        }
        return matrices;
    }

    private ProjectPartySummarySheetMatrix readSheet(Sheet sheet, FormulaEvaluator evaluator) {
        int firstRow = sheet.getFirstRowNum();
        int lastRow = sheet.getLastRowNum();
        if (lastRow < firstRow) {
            return new ProjectPartySummarySheetMatrix(sheet.getSheetName(), new String[0][0], firstRow);
        }

        int maxCol = 0;
        for (int r = firstRow; r <= lastRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            short lastCellNum = row.getLastCellNum();
            for (int c = 0; c < lastCellNum; c++) {
                Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                if (cell == null) {
                    continue;
                }
                String value = formatCellValue(cell, evaluator);
                if (StringUtils.hasText(value)) {
                    maxCol = Math.max(maxCol, c + 1);
                }
            }
        }
        maxCol = Math.min(maxCol, 128);
        if (maxCol <= 0) {
            return new ProjectPartySummarySheetMatrix(sheet.getSheetName(), new String[0][0], firstRow);
        }

        int rowCount = lastRow - firstRow + 1;
        String[][] cells = new String[rowCount][maxCol];
        Map<String, String> mergedValues = buildMergedValueMap(sheet, evaluator);

        for (int r = firstRow; r <= lastRow; r++) {
            Row row = sheet.getRow(r);
            int rowIndex = r - firstRow;
            for (int c = 0; c < maxCol; c++) {
                String mergedKey = r + ":" + c;
                if (mergedValues.containsKey(mergedKey)) {
                    cells[rowIndex][c] = mergedValues.get(mergedKey);
                    continue;
                }
                if (row == null) {
                    cells[rowIndex][c] = "";
                    continue;
                }
                Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                cells[rowIndex][c] = cell == null ? "" : formatCellValue(cell, evaluator);
            }
        }
        return new ProjectPartySummarySheetMatrix(sheet.getSheetName(), cells, firstRow);
    }

    private String formatCellValue(Cell cell, FormulaEvaluator evaluator) {
        try {
            return dataFormatter.formatCellValue(cell, evaluator).trim();
        } catch (Exception ex) {
            return dataFormatter.formatCellValue(cell).trim();
        }
    }

    private Map<String, String> buildMergedValueMap(Sheet sheet, FormulaEvaluator evaluator) {
        Map<String, String> mergedValues = new HashMap<>();
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            CellRangeAddress region = sheet.getMergedRegion(i);
            Row anchorRow = sheet.getRow(region.getFirstRow());
            if (anchorRow == null) {
                continue;
            }
            Cell anchorCell = anchorRow.getCell(region.getFirstColumn(), Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            String anchorValue = anchorCell == null ? "" : formatCellValue(anchorCell, evaluator);
            if (!StringUtils.hasText(anchorValue)) {
                continue;
            }
            for (int r = region.getFirstRow(); r <= region.getLastRow(); r++) {
                for (int c = region.getFirstColumn(); c <= region.getLastColumn(); c++) {
                    mergedValues.put(r + ":" + c, anchorValue);
                }
            }
        }
        return mergedValues;
    }
}
