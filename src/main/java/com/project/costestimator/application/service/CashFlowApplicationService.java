package com.project.costestimator.application.service;

import com.project.costestimator.application.port.in.CashFlowQuery;
import com.project.costestimator.application.service.support.ProjectFinder;
import com.project.costestimator.domain.*;
import com.project.costestimator.domain.enums.CostCodeType;
import com.project.costestimator.domain.service.CostCalculator;
import com.project.costestimator.domain.service.CurrencyConverter;
import com.project.costestimator.domain.service.WorkCalendarPolicy;
import com.project.costestimator.dto.ApiModels.CashFlowMonth;
import com.project.costestimator.dto.ApiModels.CashFlowReport;
import com.project.costestimator.dto.ApiModels.CostCodeAmount;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class CashFlowApplicationService implements CashFlowQuery {
    private static final int CALCULATION_SCALE = 10;

    private final ProjectFinder projects;
    private final CostCalculator calculator;
    private final CurrencyConverter currencies;
    private final WorkCalendarPolicy calendars = new WorkCalendarPolicy();

    public CashFlowApplicationService(ProjectFinder projects, CostCalculator calculator,
                                      CurrencyConverter currencies) {
        this.projects = projects;
        this.calculator = calculator;
        this.currencies = currencies;
    }

    @Override
    public CashFlowReport cashFlow(UUID projectId, UUID estimateId) {
        EstimateVersion estimate = projects.requireEstimate(projectId, estimateId);
        Project project = estimate.getProject();
        List<MonthAccumulator> months = monthsBetween(project.getPlannedStartDate(), project.getPlannedEndDate());

        estimate.getWbsItems().stream()
                .flatMap(wbs -> wbs.getActivities().stream())
                .forEach(activity -> distributeCost(
                        calculator.calculateActivityCost(activity), activity.getPlannedStartDate(),
                        activity.getPlannedEndDate(), project, months));

        CostBreakdown projectLevel = calculator.calculateEstimateCostReport(estimate).projectLevel();
        distributeCost(projectLevel, project.getPlannedStartDate(), project.getPlannedEndDate(), project, months);

        for (BoqItem item : estimate.getBoqItems()) {
            Activity activity = item.getActivity();
            LocalDate start = activity == null ? project.getPlannedStartDate() : activity.getPlannedStartDate();
            LocalDate end = activity == null ? project.getPlannedEndDate() : activity.getPlannedEndDate();
            BigDecimal value = item.getQuantity().multiply(item.getUnitPrice());
            BigDecimal conversionRate = currencies.conversionRate(
                    item.getCurrency(), project.getCurrency(), project.getUsdTryRate(), project.getEurTryRate());
            distribute(value.multiply(conversionRate), start, end, project, months,
                    (month, amount) -> month.income = month.income.add(amount));
        }

        BigDecimal cumulative = BigDecimal.ZERO;
        List<CashFlowMonth> views = new ArrayList<>();
        for (MonthAccumulator month : months) {
            BigDecimal income = money(month.income);
            BigDecimal expense = money(month.expenses.getTotalCost());
            BigDecimal net = income.subtract(expense);
            cumulative = cumulative.add(net);
            views.add(new CashFlowMonth(
                    month.month.toString(), income, expense, money(net), money(cumulative),
                    costCodeAmounts(project, month.expenses)));
        }

        BigDecimal totalIncome = views.stream().map(CashFlowMonth::income)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalExpense = views.stream().map(CashFlowMonth::expense)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CashFlowReport(
                money(totalIncome), money(totalExpense), money(totalIncome.subtract(totalExpense)), views);
    }

    private void distributeCost(CostBreakdown cost, LocalDate start, LocalDate end,
                                Project project, List<MonthAccumulator> months) {
        distributeField(cost.getPersonnelCost(), start, end, project, months, CostBreakdown::setPersonnelCost,
                CostBreakdown::getPersonnelCost);
        distributeField(cost.getEquipmentCost(), start, end, project, months, CostBreakdown::setEquipmentCost,
                CostBreakdown::getEquipmentCost);
        distributeField(cost.getFuelCost(), start, end, project, months, CostBreakdown::setFuelCost,
                CostBreakdown::getFuelCost);
        distributeField(cost.getMaterialCost(), start, end, project, months, CostBreakdown::setMaterialCost,
                CostBreakdown::getMaterialCost);
        distributeField(cost.getAccommodationCost(), start, end, project, months, CostBreakdown::setAccommodationCost,
                CostBreakdown::getAccommodationCost);
        distributeField(cost.getTransportationCost(), start, end, project, months, CostBreakdown::setTransportationCost,
                CostBreakdown::getTransportationCost);
        distributeField(cost.getOverheadCost(), start, end, project, months, CostBreakdown::setOverheadCost,
                CostBreakdown::getOverheadCost);
        distributeField(cost.getTaxCost(), start, end, project, months, CostBreakdown::setTaxCost,
                CostBreakdown::getTaxCost);
        distributeField(cost.getTotalCost(), start, end, project, months, CostBreakdown::setTotalCost,
                CostBreakdown::getTotalCost);
    }

    private void distributeField(BigDecimal value, LocalDate start, LocalDate end, Project project,
                                 List<MonthAccumulator> months,
                                 BiConsumer<CostBreakdown, BigDecimal> setter,
                                 Function<CostBreakdown, BigDecimal> getter) {
        distribute(value, start, end, project, months,
                (month, amount) -> setter.accept(month.expenses, getter.apply(month.expenses).add(amount)));
    }

    private void distribute(BigDecimal value, LocalDate start, LocalDate end, Project project,
                            List<MonthAccumulator> months, BiConsumer<MonthAccumulator, BigDecimal> consumer) {
        if (value == null || value.signum() == 0 || months.isEmpty()) {
            return;
        }
        LocalDate effectiveStart = start == null ? project.getPlannedStartDate() : start;
        LocalDate effectiveEnd = end == null ? project.getPlannedEndDate() : end;
        if (effectiveStart.isAfter(effectiveEnd)) {
            return;
        }

        List<WeightedMonth> weighted = months.stream()
                .map(month -> new WeightedMonth(month, overlapWorkingDays(month.month, effectiveStart, effectiveEnd, project)))
                .filter(entry -> entry.days.signum() > 0)
                .toList();
        BigDecimal totalDays = weighted.stream().map(WeightedMonth::days)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalDays.signum() == 0) {
            weighted = months.stream()
                    .map(month -> new WeightedMonth(month, overlapCalendarDays(month.month, effectiveStart, effectiveEnd)))
                    .filter(entry -> entry.days.signum() > 0)
                    .toList();
            totalDays = weighted.stream().map(WeightedMonth::days)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        if (totalDays.signum() == 0) {
            return;
        }

        BigDecimal allocated = BigDecimal.ZERO;
        for (int index = 0; index < weighted.size(); index++) {
            WeightedMonth entry = weighted.get(index);
            BigDecimal amount = index == weighted.size() - 1
                    ? value.subtract(allocated)
                    : value.multiply(entry.days).divide(totalDays, CALCULATION_SCALE, RoundingMode.HALF_UP);
            consumer.accept(entry.month, amount);
            allocated = allocated.add(amount);
        }
    }

    private BigDecimal overlapWorkingDays(YearMonth month, LocalDate start, LocalDate end, Project project) {
        LocalDate overlapStart = start.isAfter(month.atDay(1)) ? start : month.atDay(1);
        LocalDate overlapEnd = end.isBefore(month.atEndOfMonth()) ? end : month.atEndOfMonth();
        if (overlapStart.isAfter(overlapEnd)) {
            return BigDecimal.ZERO;
        }
        long days = 0;
        for (LocalDate date = overlapStart; !date.isAfter(overlapEnd); date = date.plusDays(1)) {
            if (calendars.isWorkingDay(date, project.getWorkCalendar())) {
                days++;
            }
        }
        return BigDecimal.valueOf(days);
    }

    private BigDecimal overlapCalendarDays(YearMonth month, LocalDate start, LocalDate end) {
        LocalDate overlapStart = start.isAfter(month.atDay(1)) ? start : month.atDay(1);
        LocalDate overlapEnd = end.isBefore(month.atEndOfMonth()) ? end : month.atEndOfMonth();
        return overlapStart.isAfter(overlapEnd)
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(java.time.temporal.ChronoUnit.DAYS.between(overlapStart, overlapEnd) + 1);
    }

    private List<MonthAccumulator> monthsBetween(LocalDate start, LocalDate end) {
        List<MonthAccumulator> months = new ArrayList<>();
        for (YearMonth month = YearMonth.from(start); !month.isAfter(YearMonth.from(end)); month = month.plusMonths(1)) {
            months.add(new MonthAccumulator(month));
        }
        return months;
    }

    private List<CostCodeAmount> costCodeAmounts(Project project, CostBreakdown cost) {
        return project.getCostCodes().stream()
                .filter(CostCode::isActive)
                .map(code -> new CostCodeAmount(
                        code.getId(), code.getCode(), code.getName(), code.getType(), money(amount(cost, code.getType()))))
                .filter(line -> line.amount().signum() != 0)
                .toList();
    }

    private BigDecimal amount(CostBreakdown cost, CostCodeType type) {
        return switch (type) {
            case PERSONNEL -> cost.getPersonnelCost();
            case EQUIPMENT -> cost.getEquipmentCost();
            case FUEL -> cost.getFuelCost();
            case MATERIAL -> cost.getMaterialCost();
            case ACCOMMODATION -> cost.getAccommodationCost();
            case TRANSPORTATION -> cost.getTransportationCost();
            case OVERHEAD -> cost.getOverheadCost();
            case TAX -> cost.getTaxCost();
        };
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static final class MonthAccumulator {
        private final YearMonth month;
        private BigDecimal income = BigDecimal.ZERO;
        private final CostBreakdown expenses = new CostBreakdown();

        private MonthAccumulator(YearMonth month) {
            this.month = month;
        }
    }

    private record WeightedMonth(MonthAccumulator month, BigDecimal days) {}
}
