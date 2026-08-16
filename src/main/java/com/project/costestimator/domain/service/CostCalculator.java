package com.project.costestimator.domain.service;

import com.project.costestimator.domain.*;
import com.project.costestimator.domain.Resource;
import com.project.costestimator.domain.enums.CalculationBasis;
import com.project.costestimator.domain.enums.CostCategory;
import com.project.costestimator.domain.enums.FuelType;
import com.project.costestimator.dto.ApiModels.ActivityCostReport;
import com.project.costestimator.dto.ApiModels.EstimateCostReport;
import com.project.costestimator.dto.ApiModels.WbsCostReport;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Pure domain calculator. It has no repository or framework dependency and can be tested in isolation.
 */
public class CostCalculator {
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final WorkCalendarPolicy calendars = new WorkCalendarPolicy();

    public CostBreakdown calculateActivityCost(Activity activity) {
        CostBreakdown result = new CostBreakdown();
        EstimateVersion estimate = activity.getWbsItem().getEstimateVersion();

        for (ResourceAssignment assignment : activity.getResourceAssignments()) {
            addAssignmentCosts(result, estimate, activity, assignment);
        }
        activity.getAdditionalCostItems().forEach(item ->
                addByCategory(result, item.getCategory(), additionalAmount(item)));
        calculateTotal(result);
        return result;
    }

    public CostBreakdown calculateProjectCost(EstimateVersion estimate) {
        return calculateEstimateCostReport(estimate).total();
    }

    public EstimateCostReport calculateEstimateCostReport(EstimateVersion estimate) {
        List<WbsCostReport> wbsReports = estimate.getWbsItems().stream()
                .map(this::calculateWbsCostReport)
                .toList();
        CostBreakdown projectLevel = calculateProjectLevelCost(estimate);
        CostBreakdown estimateTotal = new CostBreakdown();
        wbsReports.forEach(wbs -> merge(estimateTotal, wbs.costs()));
        merge(estimateTotal, projectLevel);
        calculateTotal(estimateTotal);
        return new EstimateCostReport(estimateTotal, projectLevel, wbsReports);
    }

    private WbsCostReport calculateWbsCostReport(WbsItem wbs) {
        List<ActivityCostReport> activities = wbs.getActivities().stream()
                .map(activity -> new ActivityCostReport(
                        activity.getId(), activity.getCode(), activity.getName(),
                        calculateActivityCost(activity)))
                .toList();
        CostBreakdown total = new CostBreakdown();
        activities.forEach(activity -> merge(total, activity.costs()));
        calculateTotal(total);
        return new WbsCostReport(wbs.getId(), wbs.getCode(), wbs.getName(), total, activities);
    }

    private void addAssignmentCosts(CostBreakdown result, EstimateVersion estimate,
                                    Activity activity, ResourceAssignment assignment) {
        Resource resource = resourceOf(assignment);
        BigDecimal days = assignmentDays(assignment, activity);
        BigDecimal quantity = assignment instanceof ActivityMaterialAssignment material
                ? materialQuantity(material)
                : defaultOne(assignment.getQuantity());
        BigDecimal work = effectiveWork(assignment, days);
        LocalDate effectiveDate = assignment.getStartDate() == null
                ? activity.getPlannedStartDate()
                : assignment.getStartDate();

        for (ApplicableRate rate : ratesFor(estimate, resource, effectiveDate)) {
            BigDecimal amount = assignment instanceof ActivityEquipmentAssignment equipment
                    && rate.category() == CostCategory.FUEL
                    ? fuelAmount(equipment, rate, days)
                    : componentAmount(rate, quantity, work, days);
            addByCategory(result, rate.category(), amount);
            addTax(result, rate, amount);
        }

        if (assignment instanceof ActivityEquipmentAssignment equipment) {
            addCrewCosts(result, estimate, equipment, days);
        }
    }

    private CostBreakdown calculateProjectLevelCost(EstimateVersion estimate) {
        CostBreakdown result = new CostBreakdown();
        BigDecimal hoursPerDay = calendars.hoursPerDay(estimate.getProject().getWorkCalendar());

        for (ProjectStaffAssignment staff : estimate.getProjectStaffAssignments()) {
            addProjectStaffCosts(result, estimate, staff, hoursPerDay);
        }
        estimate.getProjectLevelCosts().forEach(item ->
                addByCategory(result, item.getCategory(), additionalAmount(item)));
        calculateTotal(result);
        return result;
    }

    private void addProjectStaffCosts(CostBreakdown result, EstimateVersion estimate,
                                      ProjectStaffAssignment staff, BigDecimal hoursPerDay) {
        BigDecimal days = calendars.workingDays(
                staff.getStartDate(), staff.getEndDate(), BigDecimal.ONE,
                estimate.getProject().getWorkCalendar());
        BigDecimal allocation = percentage(staff.getAllocationPercentage());
        BigDecimal quantity = defaultOne(staff.getQuantity()).multiply(allocation);
        BigDecimal work = days.multiply(hoursPerDay).multiply(quantity);

        for (ApplicableRate rate : ratesFor(
                estimate, staff.getPersonnelResource(), staff.getStartDate())) {
            BigDecimal amount = componentAmount(rate, quantity, work, days);
            addByCategory(result, rate.category(), amount);
            addTax(result, rate, amount);
        }
    }

    private void addCrewCosts(CostBreakdown result, EstimateVersion estimate,
                              ActivityEquipmentAssignment equipment, BigDecimal days) {
        BigDecimal equipmentQuantity = defaultOne(equipment.getQuantity());
        BigDecimal utilization = percentage(equipment.getUtilizationRate());
        LocalDate effectiveDate = equipment.getStartDate() == null
                ? equipment.getActivity().getPlannedStartDate()
                : equipment.getStartDate();

        for (EquipmentCrewAssignment crew : equipment.getCrewAssignments()) {
            BigDecimal quantity = defaultOne(crew.getQuantity()).multiply(equipmentQuantity);
            BigDecimal work = zeroIfNull(crew.getWorkingHoursPerDay())
                    .multiply(days)
                    .multiply(quantity)
                    .multiply(utilization);
            for (ApplicableRate rate : ratesFor(
                    estimate, crew.getPersonnelResource(), effectiveDate)) {
                BigDecimal amount = componentAmount(rate, quantity, work, days);
                addByCategory(result, rate.category(), amount);
                addTax(result, rate, amount);
            }
        }
    }

    private List<ApplicableRate> ratesFor(EstimateVersion estimate, Resource resource,
                                          LocalDate effectiveDate) {
        List<EstimateResourceRate> snapshots = estimate.getResourceRates().stream()
                .filter(rate -> rate.getResourceId().equals(resource.getId()))
                .toList();
        List<ApplicableRate> rates;
        if (!snapshots.isEmpty()) {
            rates = snapshots.stream()
                    .map(ApplicableRate::from)
                    .filter(rate -> rate.isValidOn(effectiveDate))
                    .toList();
        } else {
            rates = resource.getCostComponents().stream()
                    .map(ApplicableRate::from)
                    .filter(rate -> rate.isValidOn(effectiveDate))
                    .toList();
        }
        if (!(resource instanceof EquipmentResource equipment)
                || rates.stream().anyMatch(rate -> rate.category() == CostCategory.FUEL)) {
            return rates;
        }

        Set<FuelType> consumedFuelTypes = equipment.getFuelConsumptions().stream()
                .map(FuelConsumption::getFuelType)
                .collect(java.util.stream.Collectors.toSet());
        List<ApplicableRate> generalFuelRates = estimate.getProject().getGeneralUnitPrices().stream()
                .filter(GeneralUnitPrice::isActive)
                .filter(price -> consumedFuelTypes.contains(price.getFuelType()))
                .map(ApplicableRate::from)
                .toList();
        return Stream.concat(rates.stream(), generalFuelRates.stream()).toList();
    }

    private BigDecimal componentAmount(ApplicableRate rate, BigDecimal quantity,
                                       BigDecimal work, BigDecimal days) {
        BigDecimal factor = switch (rate.calculationBasis()) {
            case PER_HOUR -> zeroIfNull(work);
            case PER_SHIFT, PER_DAY -> days.multiply(zeroIfNull(quantity));
            case PER_WEEK -> days.divide(BigDecimal.valueOf(7), 6, RoundingMode.HALF_UP)
                    .multiply(zeroIfNull(quantity));
            case PER_MONTH -> days.divide(BigDecimal.valueOf(30), 6, RoundingMode.HALF_UP)
                    .multiply(zeroIfNull(quantity));
            case PER_UNIT -> zeroIfNull(quantity);
            case FIXED -> BigDecimal.ONE;
            case PERCENTAGE -> BigDecimal.ZERO;
        };
        return zeroIfNull(rate.unitPrice()).multiply(factor);
    }

    private BigDecimal fuelAmount(ActivityEquipmentAssignment assignment, ApplicableRate rate,
                                  BigDecimal days) {
        BigDecimal quantity = defaultOne(assignment.getQuantity());
        BigDecimal operatingHours = operatingHours(assignment, days, quantity)
                .multiply(percentage(assignment.getUtilizationRate()));
        BigDecimal standbyHours = isPositive(assignment.getStandbyHoursPerDay())
                ? days.multiply(assignment.getStandbyHoursPerDay()).multiply(quantity)
                : BigDecimal.ZERO;
        var consumptions = assignment.getEquipmentResource().getFuelConsumptions().stream()
                .filter(fuel -> rate.fuelType() == null || fuel.getFuelType() == rate.fuelType())
                .toList();
        BigDecimal operatingConsumption = consumptions.stream()
                .map(fuel -> zeroIfNull(fuel.getConsumptionPerHour()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal standbyConsumption = consumptions.stream()
                .map(fuel -> zeroIfNull(fuel.getStandbyConsumptionPerHour()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal consumed = operatingConsumption.multiply(operatingHours)
                .add(standbyConsumption.multiply(standbyHours));
        return zeroIfNull(rate.unitPrice()).multiply(consumed);
    }

    private BigDecimal operatingHours(ActivityEquipmentAssignment assignment,
                                      BigDecimal days, BigDecimal quantity) {
        if (isPositive(assignment.getOperatingHoursPerDay())) {
            return days.multiply(assignment.getOperatingHoursPerDay()).multiply(quantity);
        }
        return zeroIfNull(assignment.getPlannedWork());
    }

    private BigDecimal effectiveWork(ResourceAssignment assignment, BigDecimal days) {
        BigDecimal rawWork;
        if (assignment instanceof ActivityEquipmentAssignment equipment
                && isPositive(equipment.getOperatingHoursPerDay())) {
            rawWork = days.multiply(equipment.getOperatingHoursPerDay())
                    .multiply(defaultOne(assignment.getQuantity()));
        } else {
            rawWork = zeroIfNull(assignment.getPlannedWork());
        }
        return rawWork.multiply(percentage(assignment.getUtilizationRate()));
    }

    private BigDecimal materialQuantity(ActivityMaterialAssignment assignment) {
        BigDecimal requiredQuantity = isPositiveOrZero(assignment.getRequiredQuantity())
                ? assignment.getRequiredQuantity()
                : defaultOne(assignment.getQuantity());
        return requiredQuantity.multiply(
                BigDecimal.ONE.add(percentageOrZero(assignment.getWastePercentage())));
    }

    private Resource resourceOf(ResourceAssignment assignment) {
        if (assignment instanceof ActivityEquipmentAssignment equipment) {
            return equipment.getEquipmentResource();
        }
        if (assignment instanceof ActivityPersonnelAssignment personnel) {
            return personnel.getPersonnelResource();
        }
        return ((ActivityMaterialAssignment) assignment).getMaterialResource();
    }

    private BigDecimal assignmentDays(ResourceAssignment assignment, Activity activity) {
        return calendars.workingDays(
                assignment.getStartDate(), assignment.getEndDate(), activityDays(activity),
                activity.getWbsItem().getEstimateVersion().getProject().getWorkCalendar());
    }

    private BigDecimal activityDays(Activity activity) {
        return calendars.workingDays(
                activity.getPlannedStartDate(), activity.getPlannedEndDate(),
                zeroIfNull(activity.getPlannedDuration()).max(BigDecimal.ONE),
                activity.getWbsItem().getEstimateVersion().getProject().getWorkCalendar());
    }

    private BigDecimal percentage(BigDecimal value) {
        return value == null
                ? BigDecimal.ONE
                : value.divide(ONE_HUNDRED, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal percentageOrZero(BigDecimal value) {
        return value == null
                ? BigDecimal.ZERO
                : value.divide(ONE_HUNDRED, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal additionalAmount(AdditionalCostItem item) {
        return zeroIfNull(item.getQuantity()).multiply(zeroIfNull(item.getUnitPrice()));
    }

    private void addTax(CostBreakdown result, ApplicableRate rate, BigDecimal amount) {
        if (!rate.taxable()) {
            return;
        }
        BigDecimal tax = amount.multiply(zeroIfNull(rate.taxRate()))
                .divide(ONE_HUNDRED, 6, RoundingMode.HALF_UP);
        result.setTaxCost(result.getTaxCost().add(tax));
    }

    private void addByCategory(CostBreakdown result, CostCategory category, BigDecimal amount) {
        if (category == null) {
            return;
        }
        switch (category) {
            case SALARY, OVERTIME ->
                    result.setPersonnelCost(result.getPersonnelCost().add(amount));
            case RENTAL, DEPRECIATION, MAINTENANCE, INSURANCE ->
                    result.setEquipmentCost(result.getEquipmentCost().add(amount));
            case FUEL -> result.setFuelCost(result.getFuelCost().add(amount));
            case MATERIAL -> result.setMaterialCost(result.getMaterialCost().add(amount));
            case ACCOMMODATION, FOOD ->
                    result.setAccommodationCost(result.getAccommodationCost().add(amount));
            case TRANSPORTATION, MOBILIZATION, DEMOBILIZATION ->
                    result.setTransportationCost(result.getTransportationCost().add(amount));
            default -> result.setOverheadCost(result.getOverheadCost().add(amount));
        }
    }

    private void merge(CostBreakdown target, CostBreakdown source) {
        target.setPersonnelCost(target.getPersonnelCost().add(source.getPersonnelCost()));
        target.setEquipmentCost(target.getEquipmentCost().add(source.getEquipmentCost()));
        target.setFuelCost(target.getFuelCost().add(source.getFuelCost()));
        target.setMaterialCost(target.getMaterialCost().add(source.getMaterialCost()));
        target.setAccommodationCost(target.getAccommodationCost().add(source.getAccommodationCost()));
        target.setTransportationCost(target.getTransportationCost().add(source.getTransportationCost()));
        target.setOverheadCost(target.getOverheadCost().add(source.getOverheadCost()));
        target.setTaxCost(target.getTaxCost().add(source.getTaxCost()));
    }

    private void calculateTotal(CostBreakdown result) {
        result.setTotalCost(
                result.getPersonnelCost()
                        .add(result.getEquipmentCost())
                        .add(result.getFuelCost())
                        .add(result.getMaterialCost())
                        .add(result.getAccommodationCost())
                        .add(result.getTransportationCost())
                        .add(result.getOverheadCost())
                        .add(result.getTaxCost()));
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal defaultOne(BigDecimal value) {
        return value == null ? BigDecimal.ONE : value;
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private boolean isPositiveOrZero(BigDecimal value) {
        return value != null && value.signum() >= 0;
    }

    private record ApplicableRate(
            CostCategory category,
            CalculationBasis calculationBasis,
            BigDecimal unitPrice,
            boolean taxable,
            BigDecimal taxRate,
            LocalDate validFrom,
            LocalDate validTo,
            FuelType fuelType) {
        private static ApplicableRate from(CostComponent component) {
            return new ApplicableRate(
                    component.getCategory(), component.getCalculationBasis(), component.getUnitPrice(),
                    component.isTaxable(), component.getTaxRate(),
                    component.getValidFrom(), component.getValidTo(), null);
        }

        private static ApplicableRate from(EstimateResourceRate rate) {
            return new ApplicableRate(
                    rate.getCategory(), rate.getCalculationBasis(), rate.getUnitPrice(),
                    rate.isTaxable(), rate.getTaxRate(), rate.getValidFrom(), rate.getValidTo(), null);
        }

        private static ApplicableRate from(GeneralUnitPrice price) {
            return new ApplicableRate(
                    CostCategory.FUEL, CalculationBasis.PER_UNIT, price.getUnitPrice(),
                    false, BigDecimal.ZERO, null, null, price.getFuelType());
        }

        private boolean isValidOn(LocalDate date) {
            return date == null
                    || (validFrom == null || !date.isBefore(validFrom))
                    && (validTo == null || !date.isAfter(validTo));
        }
    }
}
