package com.project.costestimator.application.service;

import com.project.costestimator.application.port.in.BoqUseCase;
import com.project.costestimator.application.port.out.ProjectRepositoryPort;
import com.project.costestimator.application.port.out.BoqSpreadsheetParserPort;
import com.project.costestimator.application.port.out.BoqSpreadsheetParserPort.BoqImportRow;
import com.project.costestimator.application.service.support.ProjectFinder;
import com.project.costestimator.application.service.support.ProjectViewMapper;
import com.project.costestimator.domain.Activity;
import com.project.costestimator.domain.BoqItem;
import com.project.costestimator.domain.EstimateVersion;
import com.project.costestimator.domain.WbsItem;
import com.project.costestimator.domain.service.ActivitySchedulingPolicy;
import com.project.costestimator.domain.service.CurrencyConverter;
import com.project.costestimator.dto.ApiModels.BoqRequest;
import com.project.costestimator.dto.ApiModels.BoqImportIssue;
import com.project.costestimator.dto.ApiModels.BoqImportResult;
import com.project.costestimator.dto.ApiModels.BoqTraceabilityReport;
import com.project.costestimator.dto.ApiModels.BoqView;
import com.project.costestimator.exception.NotFoundException;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BoqApplicationService implements BoqUseCase {
    private final ProjectRepositoryPort projects;
    private final ProjectFinder finder;
    private final ProjectViewMapper views;
    private final CurrencyConverter currencies;
    private final ActivitySchedulingPolicy scheduling;
    private final BoqSpreadsheetParserPort spreadsheets;

    public BoqApplicationService(ProjectRepositoryPort projects,
                                ProjectFinder finder,
                                 ProjectViewMapper views,
                                 CurrencyConverter currencies,
                                 ActivitySchedulingPolicy scheduling,
                                 BoqSpreadsheetParserPort spreadsheets) {
        this.projects = projects;
        this.finder = finder;
        this.views = views;
        this.currencies = currencies;
        this.scheduling = scheduling;
        this.spreadsheets = spreadsheets;
    }

    @Override
    public BoqView addBoqItem(UUID projectId, UUID estimateId, BoqRequest request) {
        EstimateVersion estimate = finder.requireEstimate(projectId, estimateId);
        ensureUniqueCode(estimate, null, request.code());

        BoqItem item = new BoqItem();
        item.setId(UUID.randomUUID());
        item.setEstimateVersion(estimate);
        apply(item, estimate, request);
        estimate.getBoqItems().add(item);
        projects.save(estimate.getProject());
        return views.toBoqView(item);
    }

    @Override
    public List<BoqView> listBoqItems(UUID projectId, UUID estimateId) {
        return finder.requireEstimate(projectId, estimateId).getBoqItems().stream()
                .map(views::toBoqView)
                .toList();
    }

    @Override
    public BoqView updateBoqItem(UUID projectId, UUID estimateId, UUID boqId, BoqRequest request) {
        EstimateVersion estimate = finder.requireEstimate(projectId, estimateId);
        BoqItem item = finder.requireBoqItem(estimate, boqId);
        ensureUniqueCode(estimate, item, request.code());
        apply(item, estimate, request);
        projects.save(estimate.getProject());
        return views.toBoqView(item);
    }

    @Override
    public void deleteBoqItem(UUID projectId, UUID estimateId, UUID boqId) {
        EstimateVersion estimate = finder.requireEstimate(projectId, estimateId);
        boolean removed = estimate.getBoqItems().removeIf(item -> item.getId().equals(boqId));
        if (!removed) {
            throw new NotFoundException("BOQ item not found: " + boqId);
        }
        projects.save(estimate.getProject());
    }

    @Override
    public BoqTraceabilityReport boqTraceability(UUID projectId, UUID estimateId) {
        EstimateVersion estimate = finder.requireEstimate(projectId, estimateId);
        List<BoqView> items = estimate.getBoqItems().stream().map(views::toBoqView).toList();
        int linkedItems = (int) estimate.getBoqItems().stream()
                .filter(item -> item.getActivity() != null)
                .count();
        BigDecimal total = estimate.getBoqItems().stream()
                .map(item -> projectCurrencyValue(estimate, item))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new BoqTraceabilityReport(
                total, items.size(), linkedItems, items.size() - linkedItems, items);
    }

    @Override
    public BoqImportResult importSpreadsheet(UUID projectId, UUID estimateId, byte[] content, boolean preview) {
        EstimateVersion estimate = finder.requireEstimate(projectId, estimateId);
        List<BoqImportRow> rows = spreadsheets.parse(content);
        List<BoqImportIssue> issues = new ArrayList<>();
        List<PreparedImportRow> prepared = new ArrayList<>();
        Set<String> itemCodes = new HashSet<>();
        estimate.getBoqItems().forEach(item -> itemCodes.add(normalize(item.getCode())));
        Map<String, WbsItem> existingWbs = new HashMap<>();
        estimate.getWbsItems().forEach(wbs -> existingWbs.put(normalize(wbs.getCode()), wbs));
        Set<String> headersInFile = new HashSet<>();
        String currentHeaderCode = null;

        for (BoqImportRow row : rows) {
            ImportRowType type = importRowType(row.rowType());
            if (type == null) {
                issues.add(new BoqImportIssue(row.rowNumber(),
                        "Row type must be HEADER or BOQ_ITEM"));
                continue;
            }
            if (row.code() == null || row.code().isBlank()) {
                issues.add(new BoqImportIssue(row.rowNumber(), "Item number/code is required"));
                continue;
            }
            if (row.description() == null || row.description().isBlank()) {
                issues.add(new BoqImportIssue(row.rowNumber(), "Description is required"));
                continue;
            }

            String normalizedCode = normalize(row.code());
            if (type == ImportRowType.HEADER) {
                if (!headersInFile.add(normalizedCode) && !existingWbs.containsKey(normalizedCode)) {
                    issues.add(new BoqImportIssue(row.rowNumber(), "Duplicate header code: " + row.code()));
                    continue;
                }
                currentHeaderCode = row.code().trim();
                prepared.add(PreparedImportRow.header(row.rowNumber(), currentHeaderCode, row.description().trim()));
                continue;
            }

            if (currentHeaderCode == null) {
                issues.add(new BoqImportIssue(row.rowNumber(), "BOQ item must follow a HEADER row"));
                continue;
            }
            if (!itemCodes.add(normalizedCode)) {
                issues.add(new BoqImportIssue(row.rowNumber(), "Duplicate BOQ item code: " + row.code()));
                continue;
            }
            if (row.quantity() == null || row.quantity().signum() < 0) {
                issues.add(new BoqImportIssue(row.rowNumber(), "Quantity must be a non-negative number"));
                continue;
            }
            try {
                prepared.add(PreparedImportRow.item(
                        row.rowNumber(), row.code().trim(), row.description().trim(),
                        unit(row.unit()), row.quantity(), currentHeaderCode));
            } catch (IllegalArgumentException exception) {
                issues.add(new BoqImportIssue(row.rowNumber(), exception.getMessage()));
            }
        }

        int itemCount = (int) prepared.stream().filter(row -> row.type == ImportRowType.ITEM).count();
        int newWbsCount = (int) prepared.stream()
                .filter(row -> row.type == ImportRowType.HEADER)
                .map(row -> normalize(row.code))
                .distinct()
                .filter(code -> !existingWbs.containsKey(code))
                .count();
        if (preview || !issues.isEmpty()) {
            return new BoqImportResult(true, itemCount, newWbsCount, issues);
        }

        Map<String, WbsItem> importedWbs = new HashMap<>(existingWbs);
        int createdWbs = 0;
        int importedItems = 0;
        for (PreparedImportRow row : prepared) {
            if (row.type == ImportRowType.HEADER) {
                String key = normalize(row.code);
                if (!importedWbs.containsKey(key)) {
                    WbsItem wbs = new WbsItem();
                    wbs.setId(UUID.randomUUID());
                    wbs.setCode(row.code);
                    wbs.setName(row.description);
                    wbs.setDescription("Imported from BOQ spreadsheet");
                    wbs.setSequence(estimate.getWbsItems().size() + 1);
                    wbs.setEstimateVersion(estimate);
                    estimate.getWbsItems().add(wbs);
                    importedWbs.put(key, wbs);
                    createdWbs++;
                }
                continue;
            }

            BoqItem item = new BoqItem();
            item.setId(UUID.randomUUID());
            item.setCode(row.code);
            item.setDescription(row.description);
            item.setUnit(row.unit);
            item.setQuantity(row.quantity);
            item.setUnitPrice(BigDecimal.ZERO);
            item.setCurrency(estimate.getProject().getCurrency());
            item.setEstimateVersion(estimate);
            item.setWbsItem(importedWbs.get(normalize(row.headerCode)));
            estimate.getBoqItems().add(item);
            importedItems++;
        }
        projects.save(estimate.getProject());
        return new BoqImportResult(false, importedItems, createdWbs, List.of());
    }

    private void ensureUniqueCode(EstimateVersion estimate, BoqItem currentItem, String code) {
        boolean duplicate = estimate.getBoqItems().stream()
                .anyMatch(item -> item != currentItem && item.getCode().equalsIgnoreCase(code));
        if (duplicate) {
            throw new IllegalArgumentException("BOQ code already exists: " + code);
        }
    }

    private void apply(BoqItem item, EstimateVersion estimate, BoqRequest request) {
        WbsItem wbs = finder.requireWbs(estimate, request.wbsId());
        Activity activity = request.activityId() == null
                ? null
                : finder.requireActivity(estimate, request.activityId());
        if (activity != null && activity.getWbsItem() != wbs) {
            throw new IllegalArgumentException("BOQ activity must belong to the selected WBS");
        }

        item.setCode(request.code().trim());
        item.setDescription(request.description().trim());
        item.setUnit(request.unit());
        item.setQuantity(request.quantity());
        item.setUnitPrice(request.unitPrice());
        item.setCurrency(currencies.fromCode(request.currencyCode()));
        currencies.conversionRate(
                item.getCurrency(), estimate.getProject().getCurrency(),
                estimate.getProject().getUsdTryRate(), estimate.getProject().getEurTryRate());
        item.setWbsItem(wbs);
        item.setActivity(activity);

        if (activity != null) {
            activity.setPlannedQuantity(request.quantity());
            activity.setQuantityUnit(request.unit());
            if (activity.isAutoSchedule()) {
                scheduling.scheduleFromProduction(activity);
            }
            scheduling.applyDependencyConstraints(activity);
            scheduling.synchronizeAssignmentSchedule(activity);
            scheduling.rescheduleDependents(estimate, activity);
        }
    }

    private BigDecimal projectCurrencyValue(EstimateVersion estimate, BoqItem item) {
        BigDecimal value = item.getQuantity().multiply(item.getUnitPrice());
        BigDecimal conversionRate = currencies.conversionRate(
                item.getCurrency(), estimate.getProject().getCurrency(),
                estimate.getProject().getUsdTryRate(), estimate.getProject().getEurTryRate());
        return currencies.convert(value, conversionRate);
    }

    private ImportRowType importRowType(String value) {
        String normalized = normalize(value);
        if (normalized.equals("HEADER") || normalized.equals("BASLIK") || normalized.equals("WBS")) {
            return ImportRowType.HEADER;
        }
        if (normalized.equals("BOQITEM") || normalized.equals("ITEM") || normalized.equals("BOQ")) {
            return ImportRowType.ITEM;
        }
        return null;
    }

    private com.project.costestimator.domain.enums.UnitOfMeasure unit(String value) {
        String normalized = normalize(value);
        return switch (normalized) {
            case "ADET", "PC", "PCS", "PIECE" -> com.project.costestimator.domain.enums.UnitOfMeasure.PIECE;
            case "KG", "KILOGRAM" -> com.project.costestimator.domain.enums.UnitOfMeasure.KILOGRAM;
            case "TON", "TONNE" -> com.project.costestimator.domain.enums.UnitOfMeasure.TON;
            case "L", "LT", "LITER", "LITRE" -> com.project.costestimator.domain.enums.UnitOfMeasure.LITER;
            case "M", "METER", "METRE" -> com.project.costestimator.domain.enums.UnitOfMeasure.METER;
            case "M2", "SQM", "SQUAREMETER" -> com.project.costestimator.domain.enums.UnitOfMeasure.SQUARE_METER;
            case "M3", "CBM", "CUBICMETER" -> com.project.costestimator.domain.enums.UnitOfMeasure.CUBIC_METER;
            default -> throw new IllegalArgumentException("Unsupported unit: " + value);
        };
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String ascii = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('²', '2')
                .replace('³', '3')
                .replace('ı', 'i')
                .replace('İ', 'I');
        return ascii.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private enum ImportRowType { HEADER, ITEM }

    private static final class PreparedImportRow {
        private final ImportRowType type;
        private final int rowNumber;
        private final String code;
        private final String description;
        private final com.project.costestimator.domain.enums.UnitOfMeasure unit;
        private final BigDecimal quantity;
        private final String headerCode;

        private PreparedImportRow(ImportRowType type, int rowNumber, String code, String description,
                                  com.project.costestimator.domain.enums.UnitOfMeasure unit,
                                  BigDecimal quantity, String headerCode) {
            this.type = type;
            this.rowNumber = rowNumber;
            this.code = code;
            this.description = description;
            this.unit = unit;
            this.quantity = quantity;
            this.headerCode = headerCode;
        }

        private static PreparedImportRow header(int rowNumber, String code, String description) {
            return new PreparedImportRow(ImportRowType.HEADER, rowNumber, code, description,
                    null, null, null);
        }

        private static PreparedImportRow item(int rowNumber, String code, String description,
                                              com.project.costestimator.domain.enums.UnitOfMeasure unit,
                                              BigDecimal quantity, String headerCode) {
            return new PreparedImportRow(ImportRowType.ITEM, rowNumber, code, description,
                    unit, quantity, headerCode);
        }
    }
}
