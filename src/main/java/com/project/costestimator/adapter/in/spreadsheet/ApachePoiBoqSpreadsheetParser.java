package com.project.costestimator.adapter.in.spreadsheet;

import com.project.costestimator.application.port.out.BoqSpreadsheetParserPort;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class ApachePoiBoqSpreadsheetParser implements BoqSpreadsheetParserPort {
    @Override
    public List<BoqImportRow> parse(byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("BOQ spreadsheet is empty");
        }
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("BOQ spreadsheet has no worksheet");
            }
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            List<BoqImportRow> rows = new ArrayList<>();
            for (Row row : sheet) {
                if (isBlank(row, formatter, evaluator)) {
                    continue;
                }
                String code = text(row.getCell(0), formatter, evaluator);
                String description = text(row.getCell(1), formatter, evaluator);
                String unit = text(row.getCell(2), formatter, evaluator);
                BigDecimal quantity = decimal(row.getCell(3), formatter, evaluator);
                String rowType = text(row.getCell(4), formatter, evaluator);
                if (isHeaderRow(code, description, rowType)) {
                    continue;
                }
                rows.add(new BoqImportRow(row.getRowNum() + 1, code, description, unit, quantity, rowType));
            }
            return rows;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("BOQ spreadsheet could not be read: " + exception.getMessage(), exception);
        }
    }

    private boolean isBlank(Row row, DataFormatter formatter, FormulaEvaluator evaluator) {
        for (int column = 0; column < 5; column++) {
            if (!text(row.getCell(column), formatter, evaluator).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private boolean isHeaderRow(String code, String description, String rowType) {
        String normalizedCode = normalize(code);
        String normalizedDescription = normalize(description);
        String normalizedType = normalize(rowType);
        return normalizedCode.contains("ITEMNO")
                || normalizedCode.equals("CODE")
                || normalizedDescription.equals("DESCRIPTION")
                || normalizedType.equals("ROWTYPE")
                || normalizedType.equals("TYPE");
    }

    private String text(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        return cell == null ? "" : formatter.formatCellValue(cell, evaluator).trim();
    }

    private BigDecimal decimal(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros();
        }
        String value = text(cell, formatter, evaluator).replace(" ", "");
        if (value.isBlank()) {
            return null;
        }
        if (value.contains(",") && value.contains(".")) {
            value = value.lastIndexOf(',') > value.lastIndexOf('.')
                    ? value.replace(".", "").replace(',', '.')
                    : value.replace(",", "");
        } else if (value.contains(",")) {
            value = value.replace(',', '.');
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }
}
