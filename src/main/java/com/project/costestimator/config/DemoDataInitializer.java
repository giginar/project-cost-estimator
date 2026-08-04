package com.project.costestimator.config;

import com.project.costestimator.domain.enums.*;
import com.project.costestimator.dto.ApiModels.*;
import com.project.costestimator.service.ProjectService;
import com.project.costestimator.service.ResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.demo-data", name = "enabled", havingValue = "true")
public class DemoDataInitializer implements ApplicationRunner {
    private final ProjectService projects;
    private final ResourceService resources;

    @Override
    public void run(ApplicationArguments args) {
        if (!projects.list().isEmpty()) return;

        var project = projects.create(new ProjectRequest(
                "MAR-001", "Marine Excavation — Phase 1", "Demo marine excavation project",
                LocalDate.of(2026, 8, 3), LocalDate.of(2026, 9, 26), "USD", ProjectStatus.DRAFT));
        UUID projectId = project.project().id();
        UUID estimateId = projects.addEstimate(projectId, new EstimateRequest("Baseline Estimate", "Initial demo estimate")).id();

        var preparation = addWbs(projectId, estimateId, "1", "Preparation", 1);
        var marineWorks = addWbs(projectId, estimateId, "2", "Marine Works", 2);
        var landOperations = addWbs(projectId, estimateId, "3", "Land Operations", 3);
        var closeout = addWbs(projectId, estimateId, "4", "Closeout", 4);

        var mobilization = addActivity(projectId, estimateId, new SeedActivity(preparation.id(), "1.1", "Site mobilization", ActivityType.MOBILIZATION, "2026-08-03", "2026-08-08"));
        var survey = addActivity(projectId, estimateId, new SeedActivity(preparation.id(), "1.2", "Bathymetric survey", ActivityType.WORK, "2026-08-06", "2026-08-14"));
        var areaA = addActivity(projectId, estimateId, new SeedActivity(marineWorks.id(), "2.1", "Dredging area A", ActivityType.WORK, "2026-08-12", "2026-08-28"));
        var areaB = addActivity(projectId, estimateId, new SeedActivity(marineWorks.id(), "2.2", "Dredging area B", ActivityType.WORK, "2026-08-24", "2026-09-11"));
        var transport = addActivity(projectId, estimateId, new SeedActivity(marineWorks.id(), "2.3", "Transport dredged material", ActivityType.WORK, "2026-08-17", "2026-09-08"));
        var grading = addActivity(projectId, estimateId, new SeedActivity(landOperations.id(), "3.1", "Disposal area grading", ActivityType.WORK, "2026-09-02", "2026-09-16"));
        var finalSurvey = addActivity(projectId, estimateId, new SeedActivity(closeout.id(), "4.1", "Final hydrographic survey", ActivityType.WORK, "2026-09-14", "2026-09-21"));
        var demobilization = addActivity(projectId, estimateId, new SeedActivity(closeout.id(), "4.2", "Demobilization", ActivityType.DEMOBILIZATION, "2026-09-21", "2026-09-26"));

        var operator = personnel("PER-001", "Dredge Operator", "Marine equipment operator", new BigDecimal("42"), CalculationBasis.PER_HOUR);
        var surveyor = personnel("PER-002", "Hydrographic Surveyor", "Hydrographic survey specialist", new BigDecimal("55"), CalculationBasis.PER_HOUR);
        var foreman = personnel("PER-003", "Site Foreman", "Marine works supervisor", new BigDecimal("480"), CalculationBasis.PER_DAY);
        var laborer = personnel("PER-004", "General Laborer", "General marine construction labor", new BigDecimal("28"), CalculationBasis.PER_HOUR);

        var dredger = equipment("EQ-001", "Cutter Suction Dredger", "Dredger", new BigDecimal("12500"), CalculationBasis.PER_DAY, FuelType.MARINE_DIESEL, new BigDecimal("180"), new BigDecimal("1.10"));
        var surveyBoat = equipment("EQ-002", "Hydrographic Survey Boat", "Survey vessel", new BigDecimal("1800"), CalculationBasis.PER_DAY, FuelType.MARINE_DIESEL, new BigDecimal("28"), new BigDecimal("1.10"));
        var excavator = equipment("EQ-003", "Long Reach Excavator", "Excavator", new BigDecimal("220"), CalculationBasis.PER_HOUR, FuelType.DIESEL, new BigDecimal("18"), new BigDecimal("1.05"));
        var truck = equipment("EQ-004", "Articulated Dump Truck", "Haul truck", new BigDecimal("135"), CalculationBasis.PER_HOUR, FuelType.DIESEL, new BigDecimal("12"), new BigDecimal("1.05"));

        var geotextile = material("MAT-001", "Marine Geotextile", "Geosynthetic", new BigDecimal("4.80"), UnitOfMeasure.SQUARE_METER);
        var fill = material("MAT-002", "Graded Fill", "Aggregate", new BigDecimal("21"), UnitOfMeasure.TON);
        var buoy = material("MAT-003", "Marker Buoy", "Marine marker", new BigDecimal("750"), UnitOfMeasure.PIECE);
        var ppe = material("MAT-004", "Marine PPE Set", "Safety equipment", new BigDecimal("95"), UnitOfMeasure.PIECE);

        assign(projectId, estimateId, mobilization, foreman.id(), new BigDecimal("1"), WorkUnit.PERSON_DAY);
        assign(projectId, estimateId, mobilization, truck.id(), new BigDecimal("2"), WorkUnit.EQUIPMENT_HOUR);
        assign(projectId, estimateId, mobilization, ppe.id(), new BigDecimal("8"), null);
        assign(projectId, estimateId, survey, surveyor.id(), new BigDecimal("2"), WorkUnit.PERSON_HOUR);
        assign(projectId, estimateId, survey, surveyBoat.id(), new BigDecimal("1"), WorkUnit.EQUIPMENT_HOUR);
        assign(projectId, estimateId, survey, buoy.id(), new BigDecimal("6"), null);
        assign(projectId, estimateId, areaA, operator.id(), new BigDecimal("2"), WorkUnit.PERSON_HOUR);
        assign(projectId, estimateId, areaA, foreman.id(), BigDecimal.ONE, WorkUnit.PERSON_DAY);
        assign(projectId, estimateId, areaA, dredger.id(), BigDecimal.ONE, WorkUnit.EQUIPMENT_HOUR);
        assign(projectId, estimateId, areaB, operator.id(), new BigDecimal("2"), WorkUnit.PERSON_HOUR);
        assign(projectId, estimateId, areaB, dredger.id(), BigDecimal.ONE, WorkUnit.EQUIPMENT_HOUR);
        assign(projectId, estimateId, transport, truck.id(), new BigDecimal("4"), WorkUnit.EQUIPMENT_HOUR);
        assign(projectId, estimateId, transport, laborer.id(), new BigDecimal("2"), WorkUnit.PERSON_HOUR);
        assign(projectId, estimateId, grading, excavator.id(), BigDecimal.ONE, WorkUnit.EQUIPMENT_HOUR);
        assign(projectId, estimateId, grading, fill.id(), new BigDecimal("850"), null);
        assign(projectId, estimateId, grading, geotextile.id(), new BigDecimal("1200"), null);
        assign(projectId, estimateId, finalSurvey, surveyor.id(), new BigDecimal("2"), WorkUnit.PERSON_HOUR);
        assign(projectId, estimateId, finalSurvey, surveyBoat.id(), BigDecimal.ONE, WorkUnit.EQUIPMENT_HOUR);
        assign(projectId, estimateId, demobilization, foreman.id(), BigDecimal.ONE, WorkUnit.PERSON_DAY);
        assign(projectId, estimateId, demobilization, truck.id(), new BigDecimal("2"), WorkUnit.EQUIPMENT_HOUR);
    }

    private WbsView addWbs(UUID projectId, UUID estimateId, String code, String name, int sequence) {
        return projects.addWbs(projectId, estimateId, new WbsRequest(code, name, null, sequence, null));
    }

    private ActivityView addActivity(UUID projectId, UUID estimateId, SeedActivity seed) {
        LocalDate start = LocalDate.parse(seed.start());
        LocalDate end = LocalDate.parse(seed.end());
        BigDecimal duration = BigDecimal.valueOf(ChronoUnit.DAYS.between(start, end) + 1);
        return projects.addActivity(projectId, estimateId, seed.wbsId(), new ActivityRequest(
                seed.code(), seed.name(), null, seed.type(), null, null,
                duration, DurationUnit.DAY, start, end));
    }

    private ResourceView personnel(String code, String name, String profession, BigDecimal price, CalculationBasis basis) {
        var resource = resources.createPersonnel(new PersonnelRequest(code, name, null, profession, null, SkillLevel.EXPERIENCED, true));
        addCost(resource.id(), CostCategory.SALARY, "Base salary", price, basis, basis == CalculationBasis.PER_HOUR ? UnitOfMeasure.HOUR : UnitOfMeasure.DAY);
        return resources.get(resource.id());
    }

    private ResourceView equipment(String code, String name, String type, BigDecimal price, CalculationBasis basis, FuelType fuelType, BigDecimal consumption, BigDecimal fuelPrice) {
        var resource = resources.createEquipment(new EquipmentRequest(code, name, null, type, null, null, null, null, false));
        addCost(resource.id(), CostCategory.RENTAL, "Equipment rate", price, basis, basis == CalculationBasis.PER_HOUR ? UnitOfMeasure.HOUR : UnitOfMeasure.DAY);
        addCost(resource.id(), CostCategory.FUEL, "Fuel unit price", fuelPrice, CalculationBasis.PER_UNIT, UnitOfMeasure.LITER);
        resources.addFuel(resource.id(), new FuelRequest(fuelType, consumption, UnitOfMeasure.LITER));
        return resources.get(resource.id());
    }

    private ResourceView material(String code, String name, String type, BigDecimal price, UnitOfMeasure unit) {
        var resource = resources.createMaterial(new MaterialRequest(code, name, null, type, unit));
        addCost(resource.id(), CostCategory.MATERIAL, "Unit price", price, CalculationBasis.PER_UNIT, unit);
        return resources.get(resource.id());
    }

    private void addCost(UUID resourceId, CostCategory category, String name, BigDecimal price, CalculationBasis basis, UnitOfMeasure unit) {
        resources.addCost(resourceId, new CostRequest(category, name, basis, price, unit, false, BigDecimal.ZERO, null, null));
    }

    private void assign(UUID projectId, UUID estimateId, ActivityView activity, UUID resourceId, BigDecimal quantity, WorkUnit workUnit) {
        BigDecimal days = activity.plannedDuration();
        boolean material = workUnit == null;
        projects.assignResource(projectId, estimateId, activity.id(), new AssignmentRequest(
                resourceId, quantity, material ? BigDecimal.ZERO : days.multiply(BigDecimal.valueOf(8)).multiply(quantity), workUnit,
                BigDecimal.valueOf(100), activity.plannedStartDate(), activity.plannedEndDate(), false,
                PersonnelAssignmentType.DIRECT_LABOR, BigDecimal.valueOf(8), BigDecimal.ZERO,
                material ? quantity : null, BigDecimal.ZERO));
    }

    private record SeedActivity(UUID wbsId, String code, String name, ActivityType type, String start, String end) {}
}
