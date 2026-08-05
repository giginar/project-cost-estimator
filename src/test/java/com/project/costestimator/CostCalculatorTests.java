package com.project.costestimator;

import com.project.costestimator.domain.*;
import com.project.costestimator.domain.enums.*;
import com.project.costestimator.service.CostCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CostCalculatorTests {
    private final CostCalculator calculator = new CostCalculator();
    private EstimateVersion estimate;
    private Activity activity;

    @BeforeEach
    void setUp() {
        Project project = new Project();
        project.setId(UUID.randomUUID()); project.setCode("TEST"); project.setCurrency(Currency.getInstance("USD"));
        estimate = new EstimateVersion();
        estimate.setId(UUID.randomUUID()); estimate.setProject(project); project.getEstimateVersions().add(estimate);
        WbsItem wbs = new WbsItem();
        wbs.setId(UUID.randomUUID()); wbs.setCode("1"); wbs.setName("Works"); wbs.setEstimateVersion(estimate); estimate.getWbsItems().add(wbs);
        activity = new Activity();
        activity.setId(UUID.randomUUID()); activity.setCode("A-1"); activity.setName("Activity");
        activity.setPlannedStartDate(LocalDate.of(2026, 1, 1)); activity.setPlannedEndDate(LocalDate.of(2026, 1, 3));
        activity.setWbsItem(wbs); wbs.getActivities().add(activity);
    }

    @Test
    void appliesHourlyWorkUtilizationValidityAndTax() {
        PersonnelResource person = personnel("PER-1");
        person.getCostComponents().add(cost(person, CostCategory.SALARY, CalculationBasis.PER_HOUR, "10", true, "20", null, null));
        person.getCostComponents().add(cost(person, CostCategory.SALARY, CalculationBasis.PER_HOUR, "999", false, "0", null, LocalDate.of(2025, 12, 31)));
        ActivityPersonnelAssignment assignment = new ActivityPersonnelAssignment();
        assignment.setId(UUID.randomUUID()); assignment.setActivity(activity); assignment.setPersonnelResource(person);
        assignment.setQuantity(new BigDecimal("2")); assignment.setPlannedWork(new BigDecimal("48")); assignment.setUtilizationRate(new BigDecimal("50"));
        activity.getResourceAssignments().add(assignment);

        CostBreakdown result = calculator.calculateActivityCost(activity);

        assertThat(result.getPersonnelCost()).isEqualByComparingTo("240");
        assertThat(result.getTaxCost()).isEqualByComparingTo("48");
        assertThat(result.getTotalCost()).isEqualByComparingTo("288");
    }

    @Test
    void calculatesEquipmentDayRateOperatingAndStandbyFuel() {
        EquipmentResource equipment = equipment("EQ-1");
        equipment.getCostComponents().add(cost(equipment, CostCategory.RENTAL, CalculationBasis.PER_DAY, "100", false, "0", null, null));
        equipment.getCostComponents().add(cost(equipment, CostCategory.FUEL, CalculationBasis.PER_UNIT, "2", false, "0", null, null));
        FuelConsumption fuel = new FuelConsumption();
        fuel.setId(UUID.randomUUID()); fuel.setConsumptionPerHour(new BigDecimal("5")); fuel.setStandbyConsumptionPerHour(BigDecimal.ONE); fuel.setEquipmentResource(equipment);
        equipment.getFuelConsumptions().add(fuel);
        ActivityEquipmentAssignment assignment = new ActivityEquipmentAssignment();
        assignment.setId(UUID.randomUUID()); assignment.setActivity(activity); assignment.setEquipmentResource(equipment);
        assignment.setQuantity(new BigDecimal("2")); assignment.setOperatingHoursPerDay(new BigDecimal("10")); assignment.setStandbyHoursPerDay(new BigDecimal("2")); assignment.setUtilizationRate(new BigDecimal("50"));
        activity.getResourceAssignments().add(assignment);

        CostBreakdown result = calculator.calculateActivityCost(activity);

        assertThat(result.getEquipmentCost()).isEqualByComparingTo("600");
        assertThat(result.getFuelCost()).isEqualByComparingTo("324");
        assertThat(result.getTotalCost()).isEqualByComparingTo("924");
    }

    @Test
    void multipliesEquipmentCrewByCrewAndEquipmentQuantity() {
        EquipmentResource equipment = equipment("EQ-2");
        PersonnelResource operator = personnel("PER-2");
        operator.getCostComponents().add(cost(operator, CostCategory.SALARY, CalculationBasis.PER_HOUR, "10", false, "0", null, null));
        ActivityEquipmentAssignment assignment = new ActivityEquipmentAssignment();
        assignment.setId(UUID.randomUUID()); assignment.setActivity(activity); assignment.setEquipmentResource(equipment);
        assignment.setQuantity(new BigDecimal("2")); assignment.setUtilizationRate(new BigDecimal("50"));
        EquipmentCrewAssignment crew = new EquipmentCrewAssignment();
        crew.setId(UUID.randomUUID()); crew.setEquipmentAssignment(assignment); crew.setPersonnelResource(operator);
        crew.setQuantity(new BigDecimal("3")); crew.setWorkingHoursPerDay(new BigDecimal("8")); assignment.getCrewAssignments().add(crew);
        activity.getResourceAssignments().add(assignment);

        CostBreakdown result = calculator.calculateActivityCost(activity);

        assertThat(result.getPersonnelCost()).isEqualByComparingTo("720");
    }

    @Test
    void appliesRequiredMaterialQuantityWasteAndTax() {
        MaterialResource material = material("MAT-1");
        material.getCostComponents().add(cost(material, CostCategory.MATERIAL, CalculationBasis.PER_UNIT, "2", true, "10", null, null));
        ActivityMaterialAssignment assignment = new ActivityMaterialAssignment();
        assignment.setId(UUID.randomUUID()); assignment.setActivity(activity); assignment.setMaterialResource(material);
        assignment.setQuantity(BigDecimal.ONE); assignment.setRequiredQuantity(new BigDecimal("100")); assignment.setWastePercentage(new BigDecimal("5"));
        activity.getResourceAssignments().add(assignment);

        CostBreakdown result = calculator.calculateActivityCost(activity);

        assertThat(result.getMaterialCost()).isEqualByComparingTo("210");
        assertThat(result.getTaxCost()).isEqualByComparingTo("21");
        assertThat(result.getTotalCost()).isEqualByComparingTo("231");
    }

    @Test
    void appliesProjectStaffQuantityAllocationCalendarHoursAndMonthlyRate() {
        WorkCalendar calendar = new WorkCalendar(); calendar.setWorkingHoursPerDay(new BigDecimal("10")); estimate.getProject().setWorkCalendar(calendar);
        PersonnelResource manager = personnel("PER-3");
        manager.getCostComponents().add(cost(manager, CostCategory.SALARY, CalculationBasis.PER_MONTH, "3000", false, "0", null, null));
        manager.getCostComponents().add(cost(manager, CostCategory.OVERTIME, CalculationBasis.PER_HOUR, "10", false, "0", null, null));
        ProjectStaffAssignment staff = new ProjectStaffAssignment();
        staff.setId(UUID.randomUUID()); staff.setEstimateVersion(estimate); staff.setPersonnelResource(manager);
        staff.setQuantity(new BigDecimal("2")); staff.setAllocationPercentage(new BigDecimal("50"));
        staff.setStartDate(LocalDate.of(2026, 1, 1)); staff.setEndDate(LocalDate.of(2026, 1, 30)); estimate.getProjectStaffAssignments().add(staff);

        CostBreakdown result = calculator.calculateProjectCost(estimate);

        assertThat(result.getPersonnelCost()).isEqualByComparingTo("6000");
        assertThat(result.getTotalCost()).isEqualByComparingTo("6000");
    }

    private CostComponent cost(Resource resource, CostCategory category, CalculationBasis basis, String price,
                               boolean taxable, String taxRate, LocalDate validFrom, LocalDate validTo) {
        CostComponent cost = new CostComponent();
        cost.setId(UUID.randomUUID()); cost.setResource(resource); cost.setCategory(category); cost.setCalculationBasis(basis);
        cost.setUnitPrice(new BigDecimal(price)); cost.setTaxable(taxable); cost.setTaxRate(new BigDecimal(taxRate));
        cost.setValidFrom(validFrom); cost.setValidTo(validTo); return cost;
    }
    private PersonnelResource personnel(String code) { PersonnelResource value = new PersonnelResource(); value.setId(UUID.randomUUID()); value.setCode(code); return value; }
    private EquipmentResource equipment(String code) { EquipmentResource value = new EquipmentResource(); value.setId(UUID.randomUUID()); value.setCode(code); return value; }
    private MaterialResource material(String code) { MaterialResource value = new MaterialResource(); value.setId(UUID.randomUUID()); value.setCode(code); return value; }
}
