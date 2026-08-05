package com.project.costestimator.service;

import com.project.costestimator.domain.*;
import com.project.costestimator.domain.Resource;
import com.project.costestimator.domain.enums.CalculationBasis;
import com.project.costestimator.domain.enums.CostCategory;
import com.project.costestimator.dto.ApiModels.ActivityCostReport;
import com.project.costestimator.dto.ApiModels.EstimateCostReport;
import com.project.costestimator.dto.ApiModels.WbsCostReport;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class CostCalculator {
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    public CostBreakdown calculateActivityCost(Activity activity) {
        CostBreakdown result = new CostBreakdown();
        EstimateVersion estimate = activity.getWbsItem().getEstimateVersion();
        for (ResourceAssignment assignment : activity.getResourceAssignments()) {
            Resource resource = resourceOf(assignment);
            BigDecimal days = assignmentDays(assignment, activity);
            BigDecimal quantity = assignment instanceof ActivityMaterialAssignment material
                    ? materialQuantity(material) : defaultOne(assignment.getQuantity());
            BigDecimal work = effectiveWork(assignment, days);
            LocalDate effectiveDate = assignment.getStartDate() == null ? activity.getPlannedStartDate() : assignment.getStartDate();
            for (ApplicableRate rate : ratesFor(estimate, resource, effectiveDate)) {
                BigDecimal amount = assignment instanceof ActivityEquipmentAssignment equipment && rate.category() == CostCategory.FUEL
                        ? fuelAmount(equipment, rate, days)
                        : componentAmount(rate, quantity, work, days);
                addByCategory(result, rate.category(), amount);
                addTax(result, rate, amount);
            }
            if (assignment instanceof ActivityEquipmentAssignment equipment) addCrewCosts(result, estimate, equipment, days);
        }
        activity.getAdditionalCostItems().forEach(item -> addByCategory(result, item.getCategory(), additionalAmount(item)));
        total(result);
        return result;
    }

    public CostBreakdown calculateProjectCost(EstimateVersion estimate) {
        return calculateEstimateCostReport(estimate).total();
    }

    public EstimateCostReport calculateEstimateCostReport(EstimateVersion estimate) {
        var wbsReports = estimate.getWbsItems().stream().map(wbs -> {
            var activities = wbs.getActivities().stream()
                    .map(activity -> new ActivityCostReport(activity.getId(), activity.getCode(), activity.getName(), calculateActivityCost(activity)))
                    .toList();
            CostBreakdown wbsTotal = new CostBreakdown();
            activities.forEach(activity -> merge(wbsTotal, activity.costs()));
            total(wbsTotal);
            return new WbsCostReport(wbs.getId(), wbs.getCode(), wbs.getName(), wbsTotal, activities);
        }).toList();

        CostBreakdown projectLevel = calculateProjectLevelCost(estimate);
        CostBreakdown estimateTotal = new CostBreakdown();
        wbsReports.forEach(wbs -> merge(estimateTotal, wbs.costs()));
        merge(estimateTotal, projectLevel);
        total(estimateTotal);
        return new EstimateCostReport(estimateTotal, projectLevel, wbsReports);
    }

    private CostBreakdown calculateProjectLevelCost(EstimateVersion estimate) {
        CostBreakdown result = new CostBreakdown();
        BigDecimal hoursPerDay = projectHoursPerDay(estimate);
        estimate.getProjectStaffAssignments().forEach(staff -> {
            BigDecimal days = workingDays(staff.getStartDate(), staff.getEndDate(), BigDecimal.ONE, estimate.getProject().getWorkCalendar());
            BigDecimal allocation = percentage(staff.getAllocationPercentage());
            BigDecimal quantity = defaultOne(staff.getQuantity()).multiply(allocation);
            BigDecimal work = days.multiply(hoursPerDay).multiply(quantity);
            ratesFor(estimate, staff.getPersonnelResource(), staff.getStartDate()).forEach(rate -> {
                BigDecimal amount = componentAmount(rate, quantity, work, days);
                addByCategory(result, rate.category(), amount);
                addTax(result, rate, amount);
            });
        });
        estimate.getProjectLevelCosts().forEach(item -> addByCategory(result, item.getCategory(), additionalAmount(item)));
        total(result);
        return result;
    }

    private void addCrewCosts(CostBreakdown result, EstimateVersion estimate, ActivityEquipmentAssignment equipment, BigDecimal days) {
        BigDecimal equipmentQuantity = defaultOne(equipment.getQuantity());
        BigDecimal utilization = percentage(equipment.getUtilizationRate());
        equipment.getCrewAssignments().forEach(crew -> {
            BigDecimal quantity = defaultOne(crew.getQuantity()).multiply(equipmentQuantity);
            BigDecimal work = nullSafe(crew.getWorkingHoursPerDay()).multiply(days).multiply(quantity).multiply(utilization);
            LocalDate effectiveDate = equipment.getStartDate() == null ? equipment.getActivity().getPlannedStartDate() : equipment.getStartDate();
            ratesFor(estimate, crew.getPersonnelResource(), effectiveDate).forEach(rate -> {
                BigDecimal amount = componentAmount(rate, quantity, work, days);
                addByCategory(result, rate.category(), amount);
                addTax(result, rate, amount);
            });
        });
    }

    private List<ApplicableRate> ratesFor(EstimateVersion estimate, Resource resource, LocalDate effectiveDate) {
        List<EstimateResourceRate> snapshots = estimate.getResourceRates().stream()
                .filter(rate -> rate.getResourceId().equals(resource.getId())).toList();
        if (!snapshots.isEmpty()) return snapshots.stream().map(ApplicableRate::from)
                .filter(rate -> rate.isValidOn(effectiveDate)).toList();
        return resource.getCostComponents().stream().map(ApplicableRate::from)
                .filter(rate -> rate.isValidOn(effectiveDate)).toList();
    }

    private BigDecimal componentAmount(ApplicableRate rate, BigDecimal quantity, BigDecimal work, BigDecimal days) {
        BigDecimal factor = switch (rate.calculationBasis()) {
            case PER_HOUR -> nullSafe(work);
            case PER_SHIFT, PER_DAY -> days.multiply(nullSafe(quantity));
            case PER_WEEK -> days.divide(BigDecimal.valueOf(7), 6, RoundingMode.HALF_UP).multiply(nullSafe(quantity));
            case PER_MONTH -> days.divide(BigDecimal.valueOf(30), 6, RoundingMode.HALF_UP).multiply(nullSafe(quantity));
            case PER_UNIT -> nullSafe(quantity);
            case FIXED -> BigDecimal.ONE;
            case PERCENTAGE -> BigDecimal.ZERO;
        };
        return nullSafe(rate.unitPrice()).multiply(factor);
    }

    private BigDecimal fuelAmount(ActivityEquipmentAssignment assignment, ApplicableRate rate, BigDecimal days) {
        BigDecimal quantity = defaultOne(assignment.getQuantity());
        BigDecimal utilization = percentage(assignment.getUtilizationRate());
        BigDecimal operatingHours = positive(assignment.getOperatingHoursPerDay())
                ? days.multiply(assignment.getOperatingHoursPerDay()).multiply(quantity)
                : nullSafe(assignment.getPlannedWork());
        operatingHours = operatingHours.multiply(utilization);
        BigDecimal standbyHours = positive(assignment.getStandbyHoursPerDay())
                ? days.multiply(assignment.getStandbyHoursPerDay()).multiply(quantity) : BigDecimal.ZERO;
        BigDecimal operatingConsumption = assignment.getEquipmentResource().getFuelConsumptions().stream()
                .map(fuel -> nullSafe(fuel.getConsumptionPerHour())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal standbyConsumption = assignment.getEquipmentResource().getFuelConsumptions().stream()
                .map(fuel -> nullSafe(fuel.getStandbyConsumptionPerHour())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal consumed = operatingConsumption.multiply(operatingHours).add(standbyConsumption.multiply(standbyHours));
        return nullSafe(rate.unitPrice()).multiply(consumed);
    }

    private BigDecimal effectiveWork(ResourceAssignment assignment, BigDecimal days) {
        BigDecimal rawWork;
        if (assignment instanceof ActivityEquipmentAssignment equipment && positive(equipment.getOperatingHoursPerDay()))
            rawWork = days.multiply(equipment.getOperatingHoursPerDay()).multiply(defaultOne(assignment.getQuantity()));
        else rawWork = nullSafe(assignment.getPlannedWork());
        return rawWork.multiply(percentage(assignment.getUtilizationRate()));
    }

    private BigDecimal materialQuantity(ActivityMaterialAssignment assignment) {
        BigDecimal required = positiveOrZero(assignment.getRequiredQuantity()) ? assignment.getRequiredQuantity() : defaultOne(assignment.getQuantity());
        return required.multiply(BigDecimal.ONE.add(percentageOrZero(assignment.getWastePercentage())));
    }

    private Resource resourceOf(ResourceAssignment assignment) {
        return assignment instanceof ActivityEquipmentAssignment equipment ? equipment.getEquipmentResource()
                : assignment instanceof ActivityPersonnelAssignment personnel ? personnel.getPersonnelResource()
                : ((ActivityMaterialAssignment) assignment).getMaterialResource();
    }

    private BigDecimal assignmentDays(ResourceAssignment assignment, Activity activity) {
        return workingDays(assignment.getStartDate(), assignment.getEndDate(), activityDays(activity), activity.getWbsItem().getEstimateVersion().getProject().getWorkCalendar());
    }

    private BigDecimal activityDays(Activity activity) {
        return workingDays(activity.getPlannedStartDate(), activity.getPlannedEndDate(), nullSafe(activity.getPlannedDuration()).max(BigDecimal.ONE), activity.getWbsItem().getEstimateVersion().getProject().getWorkCalendar());
    }

    private BigDecimal workingDays(LocalDate start, LocalDate end, BigDecimal fallback, WorkCalendar calendar) {
        if (start == null || end == null) return fallback;
        if (calendar == null || calendar.getWorkingDaysPerWeek() == 0) return BigDecimal.valueOf(ChronoUnit.DAYS.between(start, end) + 1);
        int daysPerWeek = calendar.getWorkingDaysPerWeek();
        long count = 0; for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) if (date.getDayOfWeek().getValue() <= daysPerWeek) count++;
        return BigDecimal.valueOf(Math.max(1, count));
    }

    private BigDecimal projectHoursPerDay(EstimateVersion estimate) {
        WorkCalendar calendar = estimate.getProject().getWorkCalendar();
        return calendar == null || !positive(calendar.getWorkingHoursPerDay()) ? BigDecimal.valueOf(8) : calendar.getWorkingHoursPerDay();
    }

    private BigDecimal percentage(BigDecimal value) {
        return value == null ? BigDecimal.ONE : value.divide(ONE_HUNDRED, 8, RoundingMode.HALF_UP);
    }
    private BigDecimal percentageOrZero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value.divide(ONE_HUNDRED, 8, RoundingMode.HALF_UP); }

    private BigDecimal additionalAmount(AdditionalCostItem item) { return nullSafe(item.getQuantity()).multiply(nullSafe(item.getUnitPrice())); }
    private void addTax(CostBreakdown result, ApplicableRate rate, BigDecimal amount) { if (rate.taxable()) result.setTaxCost(result.getTaxCost().add(amount.multiply(nullSafe(rate.taxRate())).divide(ONE_HUNDRED, 6, RoundingMode.HALF_UP))); }
    private void addByCategory(CostBreakdown result, CostCategory category, BigDecimal amount) { if (category == null) return; switch (category) { case SALARY, OVERTIME -> result.setPersonnelCost(result.getPersonnelCost().add(amount)); case RENTAL, DEPRECIATION, MAINTENANCE, INSURANCE -> result.setEquipmentCost(result.getEquipmentCost().add(amount)); case FUEL -> result.setFuelCost(result.getFuelCost().add(amount)); case MATERIAL -> result.setMaterialCost(result.getMaterialCost().add(amount)); case ACCOMMODATION, FOOD -> result.setAccommodationCost(result.getAccommodationCost().add(amount)); case TRANSPORTATION, MOBILIZATION, DEMOBILIZATION -> result.setTransportationCost(result.getTransportationCost().add(amount)); default -> result.setOverheadCost(result.getOverheadCost().add(amount)); } }
    private void merge(CostBreakdown target, CostBreakdown source) { target.setPersonnelCost(target.getPersonnelCost().add(source.getPersonnelCost())); target.setEquipmentCost(target.getEquipmentCost().add(source.getEquipmentCost())); target.setFuelCost(target.getFuelCost().add(source.getFuelCost())); target.setMaterialCost(target.getMaterialCost().add(source.getMaterialCost())); target.setAccommodationCost(target.getAccommodationCost().add(source.getAccommodationCost())); target.setTransportationCost(target.getTransportationCost().add(source.getTransportationCost())); target.setOverheadCost(target.getOverheadCost().add(source.getOverheadCost())); target.setTaxCost(target.getTaxCost().add(source.getTaxCost())); }
    private void total(CostBreakdown result) { result.setTotalCost(result.getPersonnelCost().add(result.getEquipmentCost()).add(result.getFuelCost()).add(result.getMaterialCost()).add(result.getAccommodationCost()).add(result.getTransportationCost()).add(result.getOverheadCost()).add(result.getTaxCost())); }
    private BigDecimal nullSafe(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private BigDecimal defaultOne(BigDecimal value) { return value == null ? BigDecimal.ONE : value; }
    private boolean positive(BigDecimal value) { return value != null && value.signum() > 0; }
    private boolean positiveOrZero(BigDecimal value) { return value != null && value.signum() >= 0; }

    private record ApplicableRate(CostCategory category, CalculationBasis calculationBasis, BigDecimal unitPrice,
                                  boolean taxable, BigDecimal taxRate, LocalDate validFrom, LocalDate validTo) {
        static ApplicableRate from(CostComponent component) { return new ApplicableRate(component.getCategory(), component.getCalculationBasis(), component.getUnitPrice(), component.isTaxable(), component.getTaxRate(), component.getValidFrom(), component.getValidTo()); }
        static ApplicableRate from(EstimateResourceRate rate) { return new ApplicableRate(rate.getCategory(), rate.getCalculationBasis(), rate.getUnitPrice(), rate.isTaxable(), rate.getTaxRate(), rate.getValidFrom(), rate.getValidTo()); }
        boolean isValidOn(LocalDate date) { return date == null || (validFrom == null || !date.isBefore(validFrom)) && (validTo == null || !date.isAfter(validTo)); }
    }
}
