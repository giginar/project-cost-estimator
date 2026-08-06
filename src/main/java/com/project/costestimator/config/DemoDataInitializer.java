package com.project.costestimator.config;

import com.project.costestimator.application.port.in.AssignmentUseCase;
import com.project.costestimator.application.port.in.BoqUseCase;
import com.project.costestimator.application.port.in.PlanningUseCase;
import com.project.costestimator.application.port.in.PricingUseCase;
import com.project.costestimator.application.port.in.ProjectUseCase;
import com.project.costestimator.application.port.in.ResourceCatalogUseCase;
import com.project.costestimator.domain.enums.*;
import com.project.costestimator.dto.ApiModels.*;
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
    private final ProjectUseCase projects;
    private final PlanningUseCase planning;
    private final AssignmentUseCase assignments;
    private final BoqUseCase boq;
    private final PricingUseCase pricing;
    private final ResourceCatalogUseCase resources;

    @Override
    public void run(ApplicationArguments args) {
        if (!projects.list().isEmpty()) return;

        var project = projects.create(new ProjectRequest(
                "MAR-001", "Marine Excavation — Phase 1", "Demo marine excavation project",
                LocalDate.of(2026, 8, 3), LocalDate.of(2026, 9, 26), "USD", "en", ProjectStatus.DRAFT, null, null));
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

        planning.updateActivityPlanning(projectId, estimateId, areaA.id(), new ActivityPlanningRequest(new BigDecimal("13000"), UnitOfMeasure.CUBIC_METER, new BigDecimal("1000"), true, LocalDate.of(2026, 8, 12)));
        planning.updateActivityPlanning(projectId, estimateId, areaB.id(), new ActivityPlanningRequest(new BigDecimal("14000"), UnitOfMeasure.CUBIC_METER, new BigDecimal("1000"), true, LocalDate.of(2026, 8, 24)));
        planning.addDependency(projectId, estimateId, areaB.id(), new DependencyRequest(areaA.id(), DependencyType.START_TO_START, 8));
        boq.addBoqItem(projectId, estimateId, new BoqRequest("BOQ-2.1", "Dredging area A", UnitOfMeasure.CUBIC_METER, new BigDecimal("13000"), new BigDecimal("18.50"), "USD", marineWorks.id(), areaA.id()));
        boq.addBoqItem(projectId, estimateId, new BoqRequest("BOQ-2.2", "Dredging area B", UnitOfMeasure.CUBIC_METER, new BigDecimal("14000"), new BigDecimal("19.25"), "USD", marineWorks.id(), areaB.id()));
        boq.addBoqItem(projectId, estimateId, new BoqRequest("BOQ-3.1", "Disposal area grading", UnitOfMeasure.CUBIC_METER, new BigDecimal("850"), new BigDecimal("7.80"), "USD", landOperations.id(), grading.id()));
        seedPricing(projectId, estimateId);

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
        var dredgerAssignment = assign(projectId, estimateId, areaA, dredger.id(), BigDecimal.ONE, WorkUnit.EQUIPMENT_HOUR);
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
        assignments.addCrew(projectId, estimateId, dredgerAssignment.id(), new CrewRequest(operator.id(), "Dredge operator", BigDecimal.ONE, BigDecimal.valueOf(8), true));
        assignments.addStaff(projectId, estimateId, new StaffRequest(foreman.id(), "Project site manager", BigDecimal.ONE, BigDecimal.valueOf(50), LocalDate.of(2026, 8, 3), LocalDate.of(2026, 9, 26)));

        seedDeepwaterPort(operator, surveyor, foreman, laborer, dredger, surveyBoat, excavator, truck, geotextile, fill, buoy, ppe);
    }

    private void seedDeepwaterPort(
            ResourceView operator, ResourceView surveyor, ResourceView foreman, ResourceView laborer,
            ResourceView dredger, ResourceView surveyBoat, ResourceView excavator, ResourceView truck,
            ResourceView geotextile, ResourceView fill, ResourceView buoy, ResourceView ppe) {
        var project = projects.create(new ProjectRequest(
                "PORT-2027", "Aegean Deepwater Port Expansion",
                "Large-scale deepwater port expansion including dredging, breakwaters, quay works and marine systems",
                LocalDate.of(2027, 1, 12), LocalDate.of(2027, 11, 30), "USD", "en", ProjectStatus.ACTIVE, null, null));
        UUID projectId = project.project().id();
        UUID estimateId = projects.addEstimate(projectId, new EstimateRequest("Contract Baseline", "Approved construction baseline for the port expansion")).id();

        var engineering = addWbs(projectId, estimateId, "1", "Engineering & Mobilization", 1);
        var dredging = addWbs(projectId, estimateId, "2", "Capital Dredging", 2);
        var breakwater = addWbs(projectId, estimateId, "3", "Breakwater Construction", 3);
        var quay = addWbs(projectId, estimateId, "4", "Quay & Revetment Works", 4);
        var marineSystems = addWbs(projectId, estimateId, "5", "Marine Systems", 5);
        var handover = addWbs(projectId, estimateId, "6", "Testing & Handover", 6);

        var detailedSurvey = addActivity(projectId, estimateId, new SeedActivity(engineering.id(), "1.1", "Detailed marine survey", ActivityType.WORK, "2027-01-12", "2027-01-30"));
        var offshoreMobilization = addActivity(projectId, estimateId, new SeedActivity(engineering.id(), "1.2", "Offshore fleet mobilization", ActivityType.MOBILIZATION, "2027-01-25", "2027-02-14"));
        var channelDredging = addActivity(projectId, estimateId, new SeedActivity(dredging.id(), "2.1", "Access channel dredging", ActivityType.WORK, "2027-02-10", "2027-05-20"));
        var basinDredging = addActivity(projectId, estimateId, new SeedActivity(dredging.id(), "2.2", "Harbor basin dredging", ActivityType.WORK, "2027-03-15", "2027-06-30"));
        var dredgedTransport = addActivity(projectId, estimateId, new SeedActivity(dredging.id(), "2.3", "Dredged material transport", ActivityType.WORK, "2027-02-15", "2027-07-05"));
        var seabedPreparation = addActivity(projectId, estimateId, new SeedActivity(breakwater.id(), "3.1", "Breakwater seabed preparation", ActivityType.WORK, "2027-04-01", "2027-05-15"));
        var coreRock = addActivity(projectId, estimateId, new SeedActivity(breakwater.id(), "3.2", "Core rock placement", ActivityType.WORK, "2027-05-01", "2027-08-20"));
        var armorLayer = addActivity(projectId, estimateId, new SeedActivity(breakwater.id(), "3.3", "Armor layer installation", ActivityType.WORK, "2027-07-01", "2027-10-05"));
        var quayExcavation = addActivity(projectId, estimateId, new SeedActivity(quay.id(), "4.1", "Quay foundation excavation", ActivityType.WORK, "2027-05-20", "2027-07-10"));
        var gradedFill = addActivity(projectId, estimateId, new SeedActivity(quay.id(), "4.2", "Geotextile and graded fill", ActivityType.WORK, "2027-06-15", "2027-08-15"));
        var quayBackfill = addActivity(projectId, estimateId, new SeedActivity(quay.id(), "4.3", "Quay backfilling", ActivityType.WORK, "2027-08-01", "2027-10-10"));
        var navigationBuoys = addActivity(projectId, estimateId, new SeedActivity(marineSystems.id(), "5.1", "Navigation buoy installation", ActivityType.WORK, "2027-09-01", "2027-10-10"));
        var finalSurvey = addActivity(projectId, estimateId, new SeedActivity(marineSystems.id(), "5.2", "Final hydrographic survey", ActivityType.WORK, "2027-10-15", "2027-11-05"));
        var asBuilt = addActivity(projectId, estimateId, new SeedActivity(handover.id(), "6.1", "As-built verification", ActivityType.WORK, "2027-11-01", "2027-11-18"));
        var demobilization = addActivity(projectId, estimateId, new SeedActivity(handover.id(), "6.2", "Fleet demobilization", ActivityType.DEMOBILIZATION, "2027-11-15", "2027-11-30"));

        assign(projectId, estimateId, detailedSurvey, surveyor.id(), new BigDecimal("3"), WorkUnit.PERSON_HOUR);
        assign(projectId, estimateId, detailedSurvey, surveyBoat.id(), new BigDecimal("2"), WorkUnit.EQUIPMENT_HOUR);
        assign(projectId, estimateId, offshoreMobilization, foreman.id(), new BigDecimal("2"), WorkUnit.PERSON_DAY);
        assign(projectId, estimateId, offshoreMobilization, truck.id(), new BigDecimal("5"), WorkUnit.EQUIPMENT_HOUR);
        assign(projectId, estimateId, offshoreMobilization, ppe.id(), new BigDecimal("24"), null);
        assign(projectId, estimateId, channelDredging, operator.id(), new BigDecimal("4"), WorkUnit.PERSON_HOUR);
        var channelDredger = assign(projectId, estimateId, channelDredging, dredger.id(), new BigDecimal("2"), WorkUnit.EQUIPMENT_HOUR);
        assign(projectId, estimateId, basinDredging, operator.id(), new BigDecimal("5"), WorkUnit.PERSON_HOUR);
        assign(projectId, estimateId, basinDredging, dredger.id(), new BigDecimal("3"), WorkUnit.EQUIPMENT_HOUR);
        assign(projectId, estimateId, dredgedTransport, truck.id(), new BigDecimal("8"), WorkUnit.EQUIPMENT_HOUR);
        assign(projectId, estimateId, dredgedTransport, laborer.id(), new BigDecimal("4"), WorkUnit.PERSON_HOUR);
        assign(projectId, estimateId, seabedPreparation, excavator.id(), new BigDecimal("2"), WorkUnit.EQUIPMENT_HOUR);
        assign(projectId, estimateId, seabedPreparation, geotextile.id(), new BigDecimal("8500"), null);
        assign(projectId, estimateId, coreRock, fill.id(), new BigDecimal("18500"), null);
        assign(projectId, estimateId, coreRock, truck.id(), new BigDecimal("8"), WorkUnit.EQUIPMENT_HOUR);
        assign(projectId, estimateId, armorLayer, fill.id(), new BigDecimal("9600"), null);
        assign(projectId, estimateId, armorLayer, excavator.id(), new BigDecimal("3"), WorkUnit.EQUIPMENT_HOUR);
        assign(projectId, estimateId, quayExcavation, dredger.id(), BigDecimal.ONE, WorkUnit.EQUIPMENT_HOUR);
        assign(projectId, estimateId, quayExcavation, excavator.id(), new BigDecimal("2"), WorkUnit.EQUIPMENT_HOUR);
        assign(projectId, estimateId, gradedFill, geotextile.id(), new BigDecimal("12000"), null);
        assign(projectId, estimateId, gradedFill, fill.id(), new BigDecimal("14000"), null);
        assign(projectId, estimateId, quayBackfill, fill.id(), new BigDecimal("22000"), null);
        assign(projectId, estimateId, quayBackfill, truck.id(), new BigDecimal("10"), WorkUnit.EQUIPMENT_HOUR);
        assign(projectId, estimateId, navigationBuoys, buoy.id(), new BigDecimal("18"), null);
        assign(projectId, estimateId, navigationBuoys, surveyBoat.id(), BigDecimal.ONE, WorkUnit.EQUIPMENT_HOUR);
        assign(projectId, estimateId, finalSurvey, surveyor.id(), new BigDecimal("3"), WorkUnit.PERSON_HOUR);
        assign(projectId, estimateId, finalSurvey, surveyBoat.id(), new BigDecimal("2"), WorkUnit.EQUIPMENT_HOUR);
        assign(projectId, estimateId, asBuilt, surveyor.id(), new BigDecimal("2"), WorkUnit.PERSON_HOUR);
        assign(projectId, estimateId, demobilization, foreman.id(), new BigDecimal("2"), WorkUnit.PERSON_DAY);
        assign(projectId, estimateId, demobilization, truck.id(), new BigDecimal("5"), WorkUnit.EQUIPMENT_HOUR);

        assignments.addCrew(projectId, estimateId, channelDredger.id(), new CrewRequest(operator.id(), "Lead dredge operator", new BigDecimal("2"), BigDecimal.valueOf(8), true));
        assignments.addStaff(projectId, estimateId, new StaffRequest(foreman.id(), "Marine construction manager", BigDecimal.ONE, BigDecimal.valueOf(75), LocalDate.of(2027, 1, 12), LocalDate.of(2027, 11, 30)));
        seedPricing(projectId, estimateId);
    }

    private void seedPricing(UUID projectId, UUID estimateId) {
        pricing.addPricingRule(projectId, estimateId, new PricingRuleRequest(PricingRuleType.OVERHEAD, "Head office overhead", new BigDecimal("5"), PricingBase.ESTIMATED_COST, 1, true));
        pricing.addPricingRule(projectId, estimateId, new PricingRuleRequest(PricingRuleType.RISK, "Project risk", new BigDecimal("3"), PricingBase.RUNNING_TOTAL, 2, true));
        pricing.addPricingRule(projectId, estimateId, new PricingRuleRequest(PricingRuleType.PROFIT, "Target profit", new BigDecimal("12"), PricingBase.RUNNING_TOTAL, 3, true));
    }

    private WbsView addWbs(UUID projectId, UUID estimateId, String code, String name, int sequence) {
        return projects.addWbs(projectId, estimateId, new WbsRequest(code, name, null, sequence, null));
    }

    private ActivityView addActivity(UUID projectId, UUID estimateId, SeedActivity seed) {
        LocalDate start = LocalDate.parse(seed.start());
        LocalDate end = LocalDate.parse(seed.end());
        BigDecimal duration = BigDecimal.valueOf(ChronoUnit.DAYS.between(start, end) + 1);
        return planning.addActivity(projectId, estimateId, seed.wbsId(), new ActivityRequest(
                seed.code(), seed.name(), null, seed.type(), null, null,
                duration, DurationUnit.DAY, start, end, null, false));
    }

    private ResourceView personnel(String code, String name, String profession, BigDecimal price, CalculationBasis basis) {
        var resource = resources.createPersonnel(new PersonnelRequest(code, name, name + " assigned to marine construction activities", profession, name, SkillLevel.EXPERIENCED, true));
        addCost(resource.id(), CostCategory.SALARY, "Base salary", price, basis, basis == CalculationBasis.PER_HOUR ? UnitOfMeasure.HOUR : UnitOfMeasure.DAY);
        return resources.get(resource.id());
    }

    private ResourceView equipment(String code, String name, String type, BigDecimal price, CalculationBasis basis, FuelType fuelType, BigDecimal consumption, BigDecimal fuelPrice) {
        var resource = resources.createEquipment(new EquipmentRequest(code, name, name + " used by the demo marine project", type, "MarineWorks Co.", code + "-2026", BigDecimal.ONE, UnitOfMeasure.PIECE, false));
        addCost(resource.id(), CostCategory.RENTAL, "Equipment rate", price, basis, basis == CalculationBasis.PER_HOUR ? UnitOfMeasure.HOUR : UnitOfMeasure.DAY);
        addCost(resource.id(), CostCategory.FUEL, "Fuel unit price", fuelPrice, CalculationBasis.PER_UNIT, UnitOfMeasure.LITER);
        addCost(resource.id(), CostCategory.MAINTENANCE, "Planned maintenance", price.multiply(new BigDecimal("0.08")), basis, basis == CalculationBasis.PER_HOUR ? UnitOfMeasure.HOUR : UnitOfMeasure.DAY);
        addCost(resource.id(), CostCategory.INSURANCE, "Equipment insurance", price.multiply(new BigDecimal("0.03")), basis, basis == CalculationBasis.PER_HOUR ? UnitOfMeasure.HOUR : UnitOfMeasure.DAY);
        resources.addFuel(resource.id(), new FuelRequest(fuelType, consumption, BigDecimal.ZERO, UnitOfMeasure.LITER));
        return resources.get(resource.id());
    }

    private ResourceView material(String code, String name, String type, BigDecimal price, UnitOfMeasure unit) {
        var resource = resources.createMaterial(new MaterialRequest(code, name, name + " consumed by project activities", type, unit));
        addCost(resource.id(), CostCategory.MATERIAL, "Unit price", price, CalculationBasis.PER_UNIT, unit);
        return resources.get(resource.id());
    }

    private void addCost(UUID resourceId, CostCategory category, String name, BigDecimal price, CalculationBasis basis, UnitOfMeasure unit) {
        resources.addCost(resourceId, new CostRequest(category, name, basis, price, unit, false, BigDecimal.ZERO, null, null, "USD"));
    }

    private AssignmentView assign(UUID projectId, UUID estimateId, ActivityView activity, UUID resourceId, BigDecimal quantity, WorkUnit workUnit) {
        BigDecimal days = activity.plannedDuration();
        boolean material = workUnit == null;
        return assignments.assignResource(projectId, estimateId, activity.id(), new AssignmentRequest(
                resourceId, quantity, material ? BigDecimal.ZERO : days.multiply(BigDecimal.valueOf(8)).multiply(quantity), workUnit,
                BigDecimal.valueOf(100), activity.plannedStartDate(), activity.plannedEndDate(), false,
                PersonnelAssignmentType.DIRECT_LABOR, BigDecimal.valueOf(8), BigDecimal.ZERO,
                material ? quantity : null, BigDecimal.ZERO));
    }

    private record SeedActivity(UUID wbsId, String code, String name, ActivityType type, String start, String end) {}
}
