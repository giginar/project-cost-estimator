package com.project.costestimator.application.port.out;

import java.math.BigDecimal;
import java.util.List;

public interface BoqSpreadsheetParserPort {
    List<BoqImportRow> parse(byte[] content);

    record BoqImportRow(int rowNumber, String code, String description, String unit,
                        BigDecimal quantity, String rowType) {}
}
