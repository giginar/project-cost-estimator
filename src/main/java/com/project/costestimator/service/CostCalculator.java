package com.project.costestimator.service;

import com.project.costestimator.domain.Activity;
import com.project.costestimator.domain.ActivityEquipmentAssignment;
import com.project.costestimator.domain.ActivityMaterialAssignment;
import com.project.costestimator.domain.ActivityPersonnelAssignment;
import com.project.costestimator.domain.AdditionalCostItem;
import com.project.costestimator.domain.CostComponent;
import com.project.costestimator.domain.CostBreakdown;
import com.project.costestimator.domain.EstimateVersion;
import com.project.costestimator.domain.Resource;
import com.project.costestimator.domain.ResourceAssignment;
import com.project.costestimator.domain.enums.CalculationBasis;
import com.project.costestimator.domain.enums.CostCategory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;

@Service
public class CostCalculator {
    public CostBreakdown calculateActivityCost(Activity activity) {
        CostBreakdown result = new CostBreakdown();
        for (ResourceAssignment assignment : activity.getResourceAssignments()) {
            Resource resource = resourceOf(assignment);
            for (CostComponent component : resource.getCostComponents()) {
                BigDecimal amount = assignment instanceof ActivityEquipmentAssignment equipment && component.getCategory() == CostCategory.FUEL
                        ? fuelAmount(equipment, component)
                        : componentAmount(component, assignment.getQuantity(), assignment.getPlannedWork(), activityDays(activity));
                addByCategory(result, component.getCategory(), amount);
                addTax(result, component, amount);
            }
            if (assignment instanceof ActivityEquipmentAssignment equipment) {
                equipment.getCrewAssignments().forEach(crew -> crew.getPersonnelResource().getCostComponents().forEach(component -> {
                    BigDecimal work = nullSafe(crew.getWorkingHoursPerDay()).multiply(activityDays(activity));
                    BigDecimal amount = componentAmount(component, crew.getQuantity(), work, activityDays(activity));
                    addByCategory(result, component.getCategory(), amount); addTax(result, component, amount);
                }));
            }
        }
        activity.getAdditionalCostItems().forEach(item -> addByCategory(result, item.getCategory(), additionalAmount(item)));
        total(result);
        return result;
    }

    public CostBreakdown calculateProjectCost(EstimateVersion estimate) {
        CostBreakdown result = new CostBreakdown();
        estimate.getWbsItems().stream().flatMap(w -> w.getActivities().stream()).map(this::calculateActivityCost).forEach(c -> merge(result, c));
        estimate.getProjectStaffAssignments().forEach(staff -> staff.getPersonnelResource().getCostComponents().forEach(component -> {
            BigDecimal days = staff.getStartDate() != null && staff.getEndDate() != null ? BigDecimal.valueOf(ChronoUnit.DAYS.between(staff.getStartDate(), staff.getEndDate()) + 1) : BigDecimal.ONE;
            BigDecimal quantity = nullSafe(staff.getQuantity()).multiply(nullSafe(staff.getAllocationPercentage()).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
            BigDecimal amount = componentAmount(component, quantity, days.multiply(BigDecimal.valueOf(8)), days);
            addByCategory(result, component.getCategory(), amount); addTax(result, component, amount);
        }));
        estimate.getProjectLevelCosts().forEach(item -> addByCategory(result, item.getCategory(), additionalAmount(item)));
        total(result); return result;
    }

    private Resource resourceOf(ResourceAssignment a) { return a instanceof ActivityEquipmentAssignment x ? x.getEquipmentResource() : a instanceof ActivityPersonnelAssignment x ? x.getPersonnelResource() : ((ActivityMaterialAssignment)a).getMaterialResource(); }
    private BigDecimal activityDays(Activity a) { if (a.getPlannedStartDate() != null && a.getPlannedEndDate() != null) return BigDecimal.valueOf(ChronoUnit.DAYS.between(a.getPlannedStartDate(), a.getPlannedEndDate()) + 1); return nullSafe(a.getPlannedDuration()).max(BigDecimal.ONE); }
    private BigDecimal componentAmount(CostComponent c, BigDecimal quantity, BigDecimal work, BigDecimal days) { BigDecimal factor = switch (c.getCalculationBasis()) { case PER_HOUR -> nullSafe(work); case PER_SHIFT, PER_DAY -> days.multiply(nullSafe(quantity)); case PER_WEEK -> days.divide(BigDecimal.valueOf(7), 6, RoundingMode.HALF_UP).multiply(nullSafe(quantity)); case PER_MONTH -> days.divide(BigDecimal.valueOf(30), 6, RoundingMode.HALF_UP).multiply(nullSafe(quantity)); case PER_UNIT -> nullSafe(quantity); case FIXED -> BigDecimal.ONE; case PERCENTAGE -> BigDecimal.ZERO; }; return nullSafe(c.getUnitPrice()).multiply(factor); }
    private BigDecimal fuelAmount(ActivityEquipmentAssignment assignment, CostComponent component) { BigDecimal consumption = assignment.getEquipmentResource().getFuelConsumptions().stream().map(fuel -> nullSafe(fuel.getConsumptionPerHour())).reduce(BigDecimal.ZERO, BigDecimal::add); return nullSafe(component.getUnitPrice()).multiply(consumption).multiply(nullSafe(assignment.getPlannedWork())); }
    private BigDecimal additionalAmount(AdditionalCostItem i) { return nullSafe(i.getQuantity()).multiply(nullSafe(i.getUnitPrice())); }
    private void addTax(CostBreakdown r, CostComponent c, BigDecimal amount) { if (c.isTaxable()) r.setTaxCost(r.getTaxCost().add(amount.multiply(nullSafe(c.getTaxRate())).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP))); }
    private void addByCategory(CostBreakdown r, CostCategory category, BigDecimal amount) { if (category == null) return; switch (category) { case SALARY, OVERTIME -> r.setPersonnelCost(r.getPersonnelCost().add(amount)); case RENTAL, DEPRECIATION, MAINTENANCE, INSURANCE -> r.setEquipmentCost(r.getEquipmentCost().add(amount)); case FUEL -> r.setFuelCost(r.getFuelCost().add(amount)); case MATERIAL -> r.setMaterialCost(r.getMaterialCost().add(amount)); case ACCOMMODATION, FOOD -> r.setAccommodationCost(r.getAccommodationCost().add(amount)); case TRANSPORTATION, MOBILIZATION, DEMOBILIZATION -> r.setTransportationCost(r.getTransportationCost().add(amount)); default -> r.setOverheadCost(r.getOverheadCost().add(amount)); } }
    private void merge(CostBreakdown a, CostBreakdown b) { a.setPersonnelCost(a.getPersonnelCost().add(b.getPersonnelCost())); a.setEquipmentCost(a.getEquipmentCost().add(b.getEquipmentCost())); a.setFuelCost(a.getFuelCost().add(b.getFuelCost())); a.setMaterialCost(a.getMaterialCost().add(b.getMaterialCost())); a.setAccommodationCost(a.getAccommodationCost().add(b.getAccommodationCost())); a.setTransportationCost(a.getTransportationCost().add(b.getTransportationCost())); a.setOverheadCost(a.getOverheadCost().add(b.getOverheadCost())); a.setTaxCost(a.getTaxCost().add(b.getTaxCost())); }
    private void total(CostBreakdown r) { r.setTotalCost(r.getPersonnelCost().add(r.getEquipmentCost()).add(r.getFuelCost()).add(r.getMaterialCost()).add(r.getAccommodationCost()).add(r.getTransportationCost()).add(r.getOverheadCost()).add(r.getTaxCost())); }
    private BigDecimal nullSafe(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
