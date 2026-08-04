package com.project.costestimator.service;

import com.project.costestimator.domain.*;
import com.project.costestimator.domain.Resource;
import com.project.costestimator.domain.enums.*;
import com.project.costestimator.dto.ApiModels.*;
import com.project.costestimator.exception.NotFoundException;
import com.project.costestimator.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProjectService {
    private final ProjectRepository projects;
    private final ResourceService resources;

    public ProjectDetail create(ProjectRequest request) {
        validateDates(request.plannedStartDate(), request.plannedEndDate());
        var project = new Project(); project.setId(UUID.randomUUID()); apply(project, request);
        projects.save(project); return detail(project);
    }
    public List<ProjectSummary> list() { return projects.findAll().stream().map(this::summary).toList(); }
    public ProjectDetail get(UUID id) { return detail(requireProject(id)); }
    public ProjectDetail update(UUID id, ProjectRequest request) {
        validateDates(request.plannedStartDate(), request.plannedEndDate());
        var project = requireProject(id); Currency targetCurrency = Currency.getInstance(request.currencyCode().toUpperCase());
        BigDecimal usdTryRate = request.usdTryRate() != null ? request.usdTryRate() : project.getUsdTryRate();
        BigDecimal eurTryRate = request.eurTryRate() != null ? request.eurTryRate() : project.getEurTryRate();
        if (!targetCurrency.equals(project.getCurrency())) {
            if (usdTryRate == null || eurTryRate == null)
                throw new IllegalArgumentException("USD/TRY and EUR/TRY rates are required when changing currency");
            convertPrices(project, conversionRate(project.getCurrency(), targetCurrency, usdTryRate, eurTryRate), project.getCurrency(), targetCurrency);
        }
        apply(project, request);
        project.setUsdTryRate(usdTryRate);
        project.setEurTryRate(eurTryRate);
        projects.save(project);
        return detail(project);
    }
    public void delete(UUID id) { requireProject(id); projects.deleteById(id); }

    public EstimateView addEstimate(UUID projectId, EstimateRequest request) {
        Project project = requireProject(projectId); var estimate = new EstimateVersion(); estimate.setId(UUID.randomUUID());
        estimate.setName(request.name()); estimate.setDescription(request.description()); estimate.setVersionNumber(project.getEstimateVersions().size() + 1);
        estimate.setStatus(EstimateStatus.DRAFT); estimate.setCreatedAt(LocalDateTime.now()); estimate.setProject(project); project.getEstimateVersions().add(estimate); return estimateView(estimate);
    }
    public WbsView addWbs(UUID projectId, UUID estimateId, WbsRequest request) {
        EstimateVersion estimate = requireEstimate(projectId, estimateId); var wbs = new WbsItem(); wbs.setId(UUID.randomUUID());
        wbs.setCode(request.code()); wbs.setName(request.name()); wbs.setDescription(request.description()); wbs.setSequence(request.sequence() == null ? estimate.getWbsItems().size() + 1 : request.sequence());
        wbs.setEstimateVersion(estimate);
        if (request.parentId() != null) { var parent = requireWbs(estimate, request.parentId()); wbs.setParent(parent); parent.getChildren().add(wbs); }
        estimate.getWbsItems().add(wbs); return wbsView(wbs);
    }
    public ActivityView addActivity(UUID projectId, UUID estimateId, UUID wbsId, ActivityRequest request) {
        WbsItem wbs = requireWbs(requireEstimate(projectId, estimateId), wbsId); validateDates(request.plannedStartDate(), request.plannedEndDate());
        var a = new Activity(); a.setId(UUID.randomUUID()); a.setCode(request.code()); a.setName(request.name()); a.setDescription(request.description());
        a.setType(request.type() == null ? ActivityType.WORK : request.type()); a.setPlannedQuantity(request.plannedQuantity()); a.setQuantityUnit(request.quantityUnit());
        a.setPlannedDuration(request.plannedDuration()); a.setDurationUnit(request.durationUnit()); a.setPlannedStartDate(request.plannedStartDate()); a.setPlannedEndDate(request.plannedEndDate());
        a.setWbsItem(wbs); wbs.getActivities().add(a); return activityView(a);
    }
    public ActivityView updateActivity(UUID projectId, UUID estimateId, UUID activityId, ActivityRequest request) {
        Activity activity = requireActivity(requireEstimate(projectId, estimateId), activityId);
        validateDates(request.plannedStartDate(), request.plannedEndDate());
        activity.setCode(request.code()); activity.setName(request.name()); activity.setDescription(request.description());
        activity.setType(request.type() == null ? ActivityType.WORK : request.type());
        activity.setPlannedQuantity(request.plannedQuantity()); activity.setQuantityUnit(request.quantityUnit());
        activity.setPlannedDuration(request.plannedDuration()); activity.setDurationUnit(request.durationUnit());
        activity.setPlannedStartDate(request.plannedStartDate()); activity.setPlannedEndDate(request.plannedEndDate());
        synchronizeAssignmentSchedule(activity);
        return activityView(activity);
    }
    public AssignmentView assignResource(UUID projectId, UUID estimateId, UUID activityId, AssignmentRequest request) {
        Activity activity = requireActivity(requireEstimate(projectId, estimateId), activityId); Resource resource = resources.require(request.resourceId(), Resource.class);
        if (activity.getResourceAssignments().stream().map(this::assignmentView).anyMatch(existing -> existing.resourceId().equals(request.resourceId())))
            throw new IllegalArgumentException("Resource is already assigned to this activity");
        ResourceAssignment assignment;
        if (resource instanceof EquipmentResource equipment) { var e = new ActivityEquipmentAssignment(); e.setEquipmentResource(equipment); e.setOperatingHoursPerDay(request.operatingHoursPerDay()); e.setStandbyHoursPerDay(request.standbyHoursPerDay()); assignment = e; }
        else if (resource instanceof PersonnelResource personnel) { var p = new ActivityPersonnelAssignment(); p.setPersonnelResource(personnel); p.setAssignmentType(request.personnelAssignmentType() == null ? PersonnelAssignmentType.DIRECT_LABOR : request.personnelAssignmentType()); assignment = p; }
        else if (resource instanceof MaterialResource material) { var m = new ActivityMaterialAssignment(); m.setMaterialResource(material); m.setRequiredQuantity(request.requiredQuantity()); m.setWastePercentage(request.wastePercentage()); assignment = m; }
        else throw new IllegalArgumentException("Unsupported resource type");
        assignment.setId(UUID.randomUUID()); assignment.setActivity(activity); assignment.setQuantity(request.quantity()); assignment.setPlannedWork(request.plannedWork()); assignment.setWorkUnit(request.workUnit());
        assignment.setUtilizationRate(request.utilizationRate()); assignment.setStartDate(request.startDate()); assignment.setEndDate(request.endDate()); assignment.setOvertimeAllowed(Boolean.TRUE.equals(request.overtimeAllowed()));
        activity.getResourceAssignments().add(assignment); return assignmentView(assignment);
    }
    public void unassignResource(UUID projectId, UUID estimateId, UUID activityId, UUID assignmentId) {
        Activity activity = requireActivity(requireEstimate(projectId, estimateId), activityId);
        boolean removed = activity.getResourceAssignments().removeIf(assignment -> assignment.getId().equals(assignmentId));
        if (!removed) throw new NotFoundException("Assignment not found: " + assignmentId);
    }
    public CrewView addCrew(UUID projectId, UUID estimateId, UUID assignmentId, CrewRequest request) {
        ActivityEquipmentAssignment assignment = requireEquipmentAssignment(requireEstimate(projectId, estimateId), assignmentId);
        PersonnelResource person = resources.require(request.personnelResourceId(), PersonnelResource.class); var crew = new EquipmentCrewAssignment(); crew.setId(UUID.randomUUID());
        crew.setPersonnelResource(person); crew.setEquipmentAssignment(assignment); crew.setRoleName(request.roleName()); crew.setQuantity(request.quantity()); crew.setWorkingHoursPerDay(request.workingHoursPerDay()); crew.setMandatory(!Boolean.FALSE.equals(request.mandatory()));
        assignment.getCrewAssignments().add(crew); return crewView(crew);
    }
    public StaffView addStaff(UUID projectId, UUID estimateId, StaffRequest request) {
        EstimateVersion estimate = requireEstimate(projectId, estimateId); PersonnelResource person = resources.require(request.personnelResourceId(), PersonnelResource.class);
        validateDates(request.startDate(), request.endDate()); var staff = new ProjectStaffAssignment(); staff.setId(UUID.randomUUID()); staff.setEstimateVersion(estimate); staff.setPersonnelResource(person);
        staff.setProjectRole(request.projectRole()); staff.setQuantity(request.quantity()); staff.setAllocationPercentage(request.allocationPercentage()); staff.setStartDate(request.startDate()); staff.setEndDate(request.endDate());
        estimate.getProjectStaffAssignments().add(staff); return staffView(staff);
    }

    public EstimateVersion requireEstimate(UUID projectId, UUID id) { return requireProject(projectId).getEstimateVersions().stream().filter(e -> e.getId().equals(id)).findFirst().orElseThrow(() -> new NotFoundException("Estimate not found: " + id)); }
    public Activity requireActivity(EstimateVersion e, UUID id) { return e.getWbsItems().stream().flatMap(w -> w.getActivities().stream()).filter(a -> a.getId().equals(id)).findFirst().orElseThrow(() -> new NotFoundException("Activity not found: " + id)); }
    private Project requireProject(UUID id) { return projects.findById(id).orElseThrow(() -> new NotFoundException("Project not found: " + id)); }
    private WbsItem requireWbs(EstimateVersion e, UUID id) { return e.getWbsItems().stream().filter(w -> w.getId().equals(id)).findFirst().orElseThrow(() -> new NotFoundException("WBS item not found: " + id)); }
    private ActivityEquipmentAssignment requireEquipmentAssignment(EstimateVersion e, UUID id) { return e.getWbsItems().stream().flatMap(w -> w.getActivities().stream()).flatMap(a -> a.getResourceAssignments().stream()).filter(x -> x.getId().equals(id)).filter(ActivityEquipmentAssignment.class::isInstance).map(ActivityEquipmentAssignment.class::cast).findFirst().orElseThrow(() -> new NotFoundException("Equipment assignment not found: " + id)); }
    private void apply(Project p, ProjectRequest r) { p.setCode(r.code()); p.setName(r.name()); p.setDescription(r.description()); p.setPlannedStartDate(r.plannedStartDate()); p.setPlannedEndDate(r.plannedEndDate()); p.setCurrency(Currency.getInstance(r.currencyCode().toUpperCase())); p.setLanguageCode("tr".equalsIgnoreCase(r.languageCode()) ? "tr" : "en"); if (r.usdTryRate() != null) p.setUsdTryRate(r.usdTryRate()); if (r.eurTryRate() != null) p.setEurTryRate(r.eurTryRate()); p.setStatus(r.status() == null ? ProjectStatus.DRAFT : r.status()); }
    private void validateDates(java.time.LocalDate start, java.time.LocalDate end) { if (start != null && end != null && end.isBefore(start)) throw new IllegalArgumentException("End date cannot be before start date"); }
    private void synchronizeAssignmentSchedule(Activity activity) {
        if (activity.getPlannedStartDate() == null || activity.getPlannedEndDate() == null) return;
        BigDecimal days = BigDecimal.valueOf(ChronoUnit.DAYS.between(activity.getPlannedStartDate(), activity.getPlannedEndDate()) + 1);
        activity.getResourceAssignments().forEach(assignment -> {
            assignment.setStartDate(activity.getPlannedStartDate()); assignment.setEndDate(activity.getPlannedEndDate());
            BigDecimal quantity = assignment.getQuantity() == null ? BigDecimal.ONE : assignment.getQuantity();
            if (assignment.getWorkUnit() == WorkUnit.PERSON_HOUR || assignment.getWorkUnit() == WorkUnit.EQUIPMENT_HOUR)
                assignment.setPlannedWork(days.multiply(BigDecimal.valueOf(8)).multiply(quantity));
            else if (assignment.getWorkUnit() == WorkUnit.PERSON_DAY || assignment.getWorkUnit() == WorkUnit.EQUIPMENT_DAY)
                assignment.setPlannedWork(days.multiply(quantity));
        });
    }
    private void convertPrices(Project project, BigDecimal rate, Currency source, Currency target) {
        resources.convertAllPrices(rate);
        project.getEstimateVersions().forEach(estimate -> {
            estimate.getProjectLevelCosts().forEach(cost -> cost.setUnitPrice(convert(cost.getUnitPrice(), rate)));
            estimate.getWbsItems().stream().flatMap(wbs -> wbs.getActivities().stream()).flatMap(activity -> activity.getAdditionalCostItems().stream()).forEach(cost -> cost.setUnitPrice(convert(cost.getUnitPrice(), rate)));
            var exchangeRate = new ProjectRate(); exchangeRate.setId(UUID.randomUUID()); exchangeRate.setRateType(RateType.EXCHANGE_RATE);
            exchangeRate.setName("1 " + source.getCurrencyCode() + " = " + rate.stripTrailingZeros().toPlainString() + " " + target.getCurrencyCode());
            exchangeRate.setValue(rate); exchangeRate.setValidFrom(java.time.LocalDate.now()); exchangeRate.setEstimateVersion(estimate); estimate.getProjectRates().add(exchangeRate);
        });
    }
    private BigDecimal convert(BigDecimal value, BigDecimal rate) { return value == null ? null : value.multiply(rate).setScale(4, java.math.RoundingMode.HALF_UP).stripTrailingZeros(); }
    private BigDecimal conversionRate(Currency source, Currency target, BigDecimal usdTry, BigDecimal eurTry) { BigDecimal sourceInTry = switch (source.getCurrencyCode()) { case "TRY" -> BigDecimal.ONE; case "USD" -> usdTry; case "EUR" -> eurTry; default -> throw new IllegalArgumentException("Unsupported source currency: " + source); }; BigDecimal targetInTry = switch (target.getCurrencyCode()) { case "TRY" -> BigDecimal.ONE; case "USD" -> usdTry; case "EUR" -> eurTry; default -> throw new IllegalArgumentException("Unsupported target currency: " + target); }; return sourceInTry.divide(targetInTry, 10, java.math.RoundingMode.HALF_UP); }
    private ProjectSummary summary(Project p) { return new ProjectSummary(p.getId(), p.getCode(), p.getName(), p.getDescription(), p.getPlannedStartDate(), p.getPlannedEndDate(), p.getCurrency(), p.getLanguageCode(), p.getUsdTryRate(), p.getEurTryRate(), p.getStatus()); }
    private ProjectDetail detail(Project p) { return new ProjectDetail(summary(p), p.getEstimateVersions().stream().map(this::estimateView).toList()); }
    private EstimateView estimateView(EstimateVersion e) { return new EstimateView(e.getId(), e.getName(), e.getDescription(), e.getVersionNumber(), e.getStatus(), e.getWbsItems().stream().map(this::wbsView).toList(), e.getProjectStaffAssignments().stream().map(this::staffView).toList()); }
    private WbsView wbsView(WbsItem w) { return new WbsView(w.getId(), w.getCode(), w.getName(), w.getDescription(), w.getSequence(), w.getParent() == null ? null : w.getParent().getId(), w.getActivities().stream().map(this::activityView).toList()); }
    private ActivityView activityView(Activity a) { return new ActivityView(a.getId(), a.getCode(), a.getName(), a.getType(), a.getPlannedQuantity(), a.getQuantityUnit(), a.getPlannedDuration(), a.getDurationUnit(), a.getPlannedStartDate(), a.getPlannedEndDate(), a.getResourceAssignments().stream().map(this::assignmentView).toList()); }
    private AssignmentView assignmentView(ResourceAssignment a) { Resource r = a instanceof ActivityEquipmentAssignment x ? x.getEquipmentResource() : a instanceof ActivityPersonnelAssignment x ? x.getPersonnelResource() : ((ActivityMaterialAssignment)a).getMaterialResource(); return new AssignmentView(a.getId(), r.getId(), r.getName(), r.getClass().getSimpleName().replace("Resource", "").toUpperCase(), a.getQuantity(), a.getPlannedWork(), a.getWorkUnit(), a.getUtilizationRate()); }
    private CrewView crewView(EquipmentCrewAssignment c) { return new CrewView(c.getId(), c.getPersonnelResource().getId(), c.getPersonnelResource().getName(), c.getRoleName(), c.getQuantity(), c.getWorkingHoursPerDay(), c.isMandatory()); }
    private StaffView staffView(ProjectStaffAssignment s) { return new StaffView(s.getId(), s.getPersonnelResource().getId(), s.getPersonnelResource().getName(), s.getProjectRole(), s.getQuantity(), s.getAllocationPercentage(), s.getStartDate(), s.getEndDate()); }
}
