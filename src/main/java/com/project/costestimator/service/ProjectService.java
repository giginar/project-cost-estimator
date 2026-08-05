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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProjectService {
    private final ProjectRepository projects;
    private final ResourceService resources;

    public ProjectDetail create(ProjectRequest request) {
        validateDates(request.plannedStartDate(), request.plannedEndDate());
        var project = new Project(); project.setId(UUID.randomUUID()); apply(project, request);
        var calendar = new WorkCalendar(); calendar.setId(UUID.randomUUID()); calendar.setName("Standard 5-day calendar");
        calendar.setWorkingDaysPerWeek(5); calendar.setWorkingHoursPerDay(BigDecimal.valueOf(8)); calendar.setProject(project);
        var shift = new Shift(); shift.setId(UUID.randomUUID()); shift.setName("Day shift"); shift.setStartTime(java.time.LocalTime.of(8, 0));
        shift.setEndTime(java.time.LocalTime.of(17, 0)); shift.setPaidHours(BigDecimal.valueOf(8)); shift.setWorkCalendar(calendar); calendar.getShifts().add(shift); project.setWorkCalendar(calendar);
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
            convertProjectPrices(project, conversionRate(project.getCurrency(), targetCurrency, usdTryRate, eurTryRate), project.getCurrency(), targetCurrency);
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
        estimate.setStatus(EstimateStatus.DRAFT); estimate.setCreatedAt(LocalDateTime.now()); estimate.setProject(project); project.getEstimateVersions().add(estimate);
        projects.save(project);
        return estimateView(estimate);
    }
    public WbsView addWbs(UUID projectId, UUID estimateId, WbsRequest request) {
        EstimateVersion estimate = requireEstimate(projectId, estimateId); var wbs = new WbsItem(); wbs.setId(UUID.randomUUID());
        wbs.setCode(request.code()); wbs.setName(request.name()); wbs.setDescription(request.description()); wbs.setSequence(request.sequence() == null ? estimate.getWbsItems().size() + 1 : request.sequence());
        wbs.setEstimateVersion(estimate);
        if (request.parentId() != null) { var parent = requireWbs(estimate, request.parentId()); wbs.setParent(parent); parent.getChildren().add(wbs); }
        estimate.getWbsItems().add(wbs);
        projects.save(estimate.getProject());
        return wbsView(wbs);
    }
    public ActivityView addActivity(UUID projectId, UUID estimateId, UUID wbsId, ActivityRequest request) {
        WbsItem wbs = requireWbs(requireEstimate(projectId, estimateId), wbsId); validateDates(request.plannedStartDate(), request.plannedEndDate());
        var a = new Activity(); a.setId(UUID.randomUUID()); a.setCode(request.code()); a.setName(request.name()); a.setDescription(request.description());
        a.setType(request.type() == null ? ActivityType.WORK : request.type()); a.setPlannedQuantity(request.plannedQuantity()); a.setQuantityUnit(request.quantityUnit());
        a.setPlannedDuration(request.plannedDuration()); a.setDurationUnit(request.durationUnit()); a.setPlannedStartDate(request.plannedStartDate()); a.setPlannedEndDate(request.plannedEndDate());
        a.setDailyProductionRate(request.dailyProductionRate()); a.setAutoSchedule(Boolean.TRUE.equals(request.autoSchedule()));
        a.setWbsItem(wbs); wbs.getActivities().add(a);
        if (a.isAutoSchedule()) scheduleFromProduction(a);
        projects.save(wbs.getEstimateVersion().getProject());
        return activityView(a);
    }
    public ActivityView updateActivity(UUID projectId, UUID estimateId, UUID activityId, ActivityRequest request) {
        Activity activity = requireActivity(requireEstimate(projectId, estimateId), activityId);
        validateDates(request.plannedStartDate(), request.plannedEndDate());
        activity.setCode(request.code()); activity.setName(request.name()); activity.setDescription(request.description());
        activity.setType(request.type() == null ? ActivityType.WORK : request.type());
        activity.setPlannedQuantity(request.plannedQuantity()); activity.setQuantityUnit(request.quantityUnit());
        activity.setPlannedDuration(request.plannedDuration()); activity.setDurationUnit(request.durationUnit());
        activity.setPlannedStartDate(request.plannedStartDate()); activity.setPlannedEndDate(request.plannedEndDate());
        if (request.dailyProductionRate() != null) activity.setDailyProductionRate(request.dailyProductionRate());
        if (request.autoSchedule() != null) activity.setAutoSchedule(request.autoSchedule());
        if (activity.isAutoSchedule()) scheduleFromProduction(activity);
        applyDependencyConstraints(activity);
        synchronizeAssignmentSchedule(activity);
        rescheduleDependents(activity.getWbsItem().getEstimateVersion(), activity, new HashSet<>());
        return activityView(activity);
    }
    public AssignmentView assignResource(UUID projectId, UUID estimateId, UUID activityId, AssignmentRequest request) {
        EstimateVersion estimate = requireEstimate(projectId, estimateId);
        Activity activity = requireActivity(estimate, activityId); Resource resource = resources.requireAvailable(request.resourceId(), projectId, Resource.class);
        if (activity.getResourceAssignments().stream().map(this::assignmentView).anyMatch(existing -> existing.resourceId().equals(request.resourceId())))
            throw new IllegalArgumentException("Resource is already assigned to this activity");
        ResourceAssignment assignment;
        if (resource instanceof EquipmentResource equipment) { var e = new ActivityEquipmentAssignment(); e.setEquipmentResource(equipment); assignment = e; }
        else if (resource instanceof PersonnelResource personnel) { var p = new ActivityPersonnelAssignment(); p.setPersonnelResource(personnel); assignment = p; }
        else if (resource instanceof MaterialResource material) { var m = new ActivityMaterialAssignment(); m.setMaterialResource(material); assignment = m; }
        else throw new IllegalArgumentException("Unsupported resource type");
        assignment.setId(UUID.randomUUID()); assignment.setActivity(activity); applyAssignmentValues(assignment, request, activity);
        activity.getResourceAssignments().add(assignment); synchronizeResourceRates(estimate, resource, false); return assignmentView(assignment);
    }
    public AssignmentView updateAssignment(UUID projectId, UUID estimateId, UUID activityId, UUID assignmentId, AssignmentRequest request) {
        EstimateVersion estimate = requireEstimate(projectId, estimateId); Activity activity = requireActivity(estimate, activityId);
        ResourceAssignment assignment = activity.getResourceAssignments().stream().filter(value -> value.getId().equals(assignmentId)).findFirst()
                .orElseThrow(() -> new NotFoundException("Assignment not found: " + assignmentId));
        Resource assignedResource = resourceFor(assignment);
        if (!assignedResource.getId().equals(request.resourceId())) throw new IllegalArgumentException("Assignment resource cannot be changed");
        applyAssignmentValues(assignment, request, activity);
        projects.save(activity.getWbsItem().getEstimateVersion().getProject());
        return assignmentView(assignment);
    }
    public void unassignResource(UUID projectId, UUID estimateId, UUID activityId, UUID assignmentId) {
        Activity activity = requireActivity(requireEstimate(projectId, estimateId), activityId);
        boolean removed = activity.getResourceAssignments().removeIf(assignment -> assignment.getId().equals(assignmentId));
        if (!removed) throw new NotFoundException("Assignment not found: " + assignmentId);
    }
    private void applyAssignmentValues(ResourceAssignment assignment, AssignmentRequest request, Activity activity) {
        validateDates(request.startDate() == null ? activity.getPlannedStartDate() : request.startDate(), request.endDate() == null ? activity.getPlannedEndDate() : request.endDate());
        assignment.setQuantity(request.quantity()); assignment.setPlannedWork(request.plannedWork()); assignment.setWorkUnit(request.workUnit());
        assignment.setUtilizationRate(request.utilizationRate()); assignment.setStartDate(request.startDate() == null ? activity.getPlannedStartDate() : request.startDate());
        assignment.setEndDate(request.endDate() == null ? activity.getPlannedEndDate() : request.endDate()); assignment.setOvertimeAllowed(Boolean.TRUE.equals(request.overtimeAllowed()));
        if (assignment instanceof ActivityEquipmentAssignment equipment) {
            equipment.setOperatingHoursPerDay(request.operatingHoursPerDay()); equipment.setStandbyHoursPerDay(request.standbyHoursPerDay());
        } else if (assignment instanceof ActivityPersonnelAssignment personnel) {
            personnel.setAssignmentType(request.personnelAssignmentType() == null ? PersonnelAssignmentType.DIRECT_LABOR : request.personnelAssignmentType());
        } else if (assignment instanceof ActivityMaterialAssignment material) {
            material.setRequiredQuantity(request.requiredQuantity()); material.setWastePercentage(request.wastePercentage() == null ? material.getMaterialResource().getDefaultWastePercentage() : request.wastePercentage());
        }
        if (assignment.getPlannedWork() == null) assignment.setPlannedWork(derivedWork(assignment));
    }
    public CrewView addCrew(UUID projectId, UUID estimateId, UUID assignmentId, CrewRequest request) {
        ActivityEquipmentAssignment assignment = requireEquipmentAssignment(requireEstimate(projectId, estimateId), assignmentId);
        PersonnelResource person = resources.requireAvailable(request.personnelResourceId(), projectId, PersonnelResource.class); var crew = new EquipmentCrewAssignment(); crew.setId(UUID.randomUUID());
        crew.setPersonnelResource(person); crew.setEquipmentAssignment(assignment); crew.setRoleName(request.roleName()); crew.setQuantity(request.quantity()); crew.setWorkingHoursPerDay(request.workingHoursPerDay()); crew.setMandatory(!Boolean.FALSE.equals(request.mandatory()));
        assignment.getCrewAssignments().add(crew); synchronizeResourceRates(assignment.getActivity().getWbsItem().getEstimateVersion(), person, false); return crewView(crew);
    }
    public StaffView addStaff(UUID projectId, UUID estimateId, StaffRequest request) {
        EstimateVersion estimate = requireEstimate(projectId, estimateId); PersonnelResource person = resources.requireAvailable(request.personnelResourceId(), projectId, PersonnelResource.class);
        validateDates(request.startDate(), request.endDate()); var staff = new ProjectStaffAssignment(); staff.setId(UUID.randomUUID()); staff.setEstimateVersion(estimate); staff.setPersonnelResource(person);
        staff.setProjectRole(request.projectRole()); staff.setQuantity(request.quantity()); staff.setAllocationPercentage(request.allocationPercentage()); staff.setStartDate(request.startDate()); staff.setEndDate(request.endDate());
        estimate.getProjectStaffAssignments().add(staff); synchronizeResourceRates(estimate, person, false); return staffView(staff);
    }

    public ActivityView updateActivityPlanning(UUID projectId, UUID estimateId, UUID activityId, ActivityPlanningRequest request) {
        EstimateVersion estimate = requireEstimate(projectId, estimateId); Activity activity = requireActivity(estimate, activityId);
        activity.setPlannedQuantity(request.plannedQuantity()); activity.setQuantityUnit(request.quantityUnit());
        activity.setDailyProductionRate(request.dailyProductionRate()); activity.setAutoSchedule(Boolean.TRUE.equals(request.autoSchedule()));
        activity.setPlannedStartDate(request.plannedStartDate());
        if (activity.isAutoSchedule()) scheduleFromProduction(activity);
        else if (activity.getPlannedEndDate() != null) validateDates(activity.getPlannedStartDate(), activity.getPlannedEndDate());
        applyDependencyConstraints(activity); synchronizeAssignmentSchedule(activity);
        rescheduleDependents(estimate, activity, new HashSet<>());
        projects.save(activity.getWbsItem().getEstimateVersion().getProject());
        return activityView(activity);
    }

    public DependencyView addDependency(UUID projectId, UUID estimateId, UUID activityId, DependencyRequest request) {
        EstimateVersion estimate = requireEstimate(projectId, estimateId); Activity successor = requireActivity(estimate, activityId);
        Activity predecessor = requireActivity(estimate, request.predecessorActivityId());
        if (successor == predecessor) throw new IllegalArgumentException("An activity cannot depend on itself");
        if (dependsOn(predecessor, successor, new HashSet<>())) throw new IllegalArgumentException("Activity dependency would create a cycle");
        if (successor.getDependencies().stream().anyMatch(value -> value.getPredecessor().getId().equals(predecessor.getId())))
            throw new IllegalArgumentException("Dependency already exists");
        var dependency = new ActivityDependency(); dependency.setId(UUID.randomUUID()); dependency.setSuccessor(successor); dependency.setPredecessor(predecessor);
        dependency.setType(request.type()); dependency.setLagDays(request.lagDays() == null ? 0 : request.lagDays()); successor.getDependencies().add(dependency);
        applyDependencyConstraints(successor); synchronizeAssignmentSchedule(successor); rescheduleDependents(estimate, successor, new HashSet<>()); projects.save(estimate.getProject()); return dependencyView(dependency);
    }

    public void deleteDependency(UUID projectId, UUID estimateId, UUID activityId, UUID dependencyId) {
        Activity activity = requireActivity(requireEstimate(projectId, estimateId), activityId);
        if (!activity.getDependencies().removeIf(value -> value.getId().equals(dependencyId))) throw new NotFoundException("Dependency not found: " + dependencyId);
        projects.save(activity.getWbsItem().getEstimateVersion().getProject());
    }

    public BoqView addBoqItem(UUID projectId, UUID estimateId, BoqRequest request) {
        EstimateVersion estimate = requireEstimate(projectId, estimateId);
        if (estimate.getBoqItems().stream().anyMatch(item -> item.getCode().equalsIgnoreCase(request.code()))) throw new IllegalArgumentException("BOQ code already exists: " + request.code());
        var item = new BoqItem(); item.setId(UUID.randomUUID()); item.setEstimateVersion(estimate); applyBoq(item, estimate, request); estimate.getBoqItems().add(item);
        projects.save(estimate.getProject()); return boqView(item);
    }

    public List<BoqView> listBoqItems(UUID projectId, UUID estimateId) { return requireEstimate(projectId, estimateId).getBoqItems().stream().map(this::boqView).toList(); }

    public BoqView updateBoqItem(UUID projectId, UUID estimateId, UUID boqId, BoqRequest request) {
        EstimateVersion estimate = requireEstimate(projectId, estimateId); BoqItem item = requireBoq(estimate, boqId);
        if (estimate.getBoqItems().stream().anyMatch(value -> value != item && value.getCode().equalsIgnoreCase(request.code()))) throw new IllegalArgumentException("BOQ code already exists: " + request.code());
        applyBoq(item, estimate, request); projects.save(estimate.getProject()); return boqView(item);
    }

    public void deleteBoqItem(UUID projectId, UUID estimateId, UUID boqId) {
        EstimateVersion estimate = requireEstimate(projectId, estimateId);
        if (!estimate.getBoqItems().removeIf(item -> item.getId().equals(boqId))) throw new NotFoundException("BOQ item not found: " + boqId);
        projects.save(estimate.getProject());
    }

    public BoqTraceabilityReport boqTraceability(UUID projectId, UUID estimateId) {
        EstimateVersion estimate = requireEstimate(projectId, estimateId); List<BoqView> items = estimate.getBoqItems().stream().map(this::boqView).toList();
        long linked = estimate.getBoqItems().stream().filter(item -> item.getActivity() != null).count();
        BigDecimal total = estimate.getBoqItems().stream().map(item -> {
            BigDecimal value = item.getQuantity().multiply(item.getUnitPrice());
            BigDecimal rate = conversionRate(item.getCurrency(), estimate.getProject().getCurrency(), estimate.getProject().getUsdTryRate(), estimate.getProject().getEurTryRate());
            return convert(value, rate);
        }).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new BoqTraceabilityReport(total, items.size(), (int) linked, items.size() - (int) linked, items);
    }

    public CalendarView getCalendar(UUID projectId) { return calendarView(requireProject(projectId).getWorkCalendar()); }

    public CalendarView updateCalendar(UUID projectId, CalendarRequest request) {
        Project project = requireProject(projectId); WorkCalendar calendar = project.getWorkCalendar() == null ? new WorkCalendar() : project.getWorkCalendar();
        if (calendar.getId() == null) calendar.setId(UUID.randomUUID()); calendar.setProject(project); calendar.setName(request.name());
        calendar.setWorkingDaysPerWeek(request.workingDaysPerWeek()); calendar.getShifts().clear();
        request.shifts().forEach(value -> { var shift = new Shift(); shift.setId(UUID.randomUUID()); shift.setName(value.name()); shift.setStartTime(value.startTime()); shift.setEndTime(value.endTime()); shift.setPaidHours(value.paidHours()); shift.setWorkCalendar(calendar); calendar.getShifts().add(shift); });
        calendar.setWorkingHoursPerDay(calendar.getShifts().stream().map(Shift::getPaidHours).reduce(BigDecimal.ZERO, BigDecimal::add));
        project.setWorkCalendar(calendar); project.getEstimateVersions().forEach(estimate -> estimate.getWbsItems().stream().flatMap(wbs -> wbs.getActivities().stream()).forEach(activity -> { if (activity.isAutoSchedule()) scheduleFromProduction(activity); applyDependencyConstraints(activity); synchronizeAssignmentSchedule(activity); }));
        projects.save(project); return calendarView(calendar);
    }

    public List<EstimateRateView> syncResourceRates(UUID projectId, UUID estimateId, UUID resourceId, boolean replaceExisting) {
        EstimateVersion estimate = requireEstimate(projectId, estimateId);
        Resource resource = resources.requireAvailable(resourceId, projectId, Resource.class);
        synchronizeResourceRates(estimate, resource, replaceExisting);
        projects.save(estimate.getProject());
        return estimate.getResourceRates().stream().filter(rate -> rate.getResourceId().equals(resourceId)).map(this::rateView).toList();
    }

    public List<EstimateRateView> listResourceRates(UUID projectId, UUID estimateId) {
        return requireEstimate(projectId, estimateId).getResourceRates().stream().map(this::rateView).toList();
    }

    public EstimateRateView updateResourceRate(UUID projectId, UUID estimateId, UUID sourceCostComponentId, EstimateRateRequest request) {
        EstimateVersion estimate = requireEstimate(projectId, estimateId);
        EstimateResourceRate rate = estimate.getResourceRates().stream()
                .filter(candidate -> candidate.getSourceCostComponentId().equals(sourceCostComponentId)).findFirst()
                .orElseThrow(() -> new NotFoundException("Estimate resource rate not found: " + sourceCostComponentId));
        rate.setUnitPrice(request.unitPrice());
        projects.save(estimate.getProject());
        return rateView(rate);
    }

    public List<PricingRuleView> listPricingRules(UUID projectId, UUID estimateId) { return requireEstimate(projectId, estimateId).getPricingRules().stream().sorted(Comparator.comparingInt(PricingRule::getSequence)).map(this::pricingRuleView).toList(); }
    public PricingRuleView addPricingRule(UUID projectId, UUID estimateId, PricingRuleRequest request) { EstimateVersion estimate = requireEstimate(projectId, estimateId); var rule = new PricingRule(); rule.setId(UUID.randomUUID()); rule.setEstimateVersion(estimate); applyPricingRule(rule, request); estimate.getPricingRules().add(rule); projects.save(estimate.getProject()); return pricingRuleView(rule); }
    public PricingRuleView updatePricingRule(UUID projectId, UUID estimateId, UUID ruleId, PricingRuleRequest request) { EstimateVersion estimate = requireEstimate(projectId, estimateId); PricingRule rule = requirePricingRule(estimate, ruleId); applyPricingRule(rule, request); projects.save(estimate.getProject()); return pricingRuleView(rule); }
    public void deletePricingRule(UUID projectId, UUID estimateId, UUID ruleId) { EstimateVersion estimate = requireEstimate(projectId, estimateId); if (!estimate.getPricingRules().removeIf(rule -> rule.getId().equals(ruleId))) throw new NotFoundException("Pricing rule not found: " + ruleId); projects.save(estimate.getProject()); }

    public EstimateVersion requireEstimate(UUID projectId, UUID id) { return requireProject(projectId).getEstimateVersions().stream().filter(e -> e.getId().equals(id)).findFirst().orElseThrow(() -> new NotFoundException("Estimate not found: " + id)); }
    public Activity requireActivity(EstimateVersion e, UUID id) { return e.getWbsItems().stream().flatMap(w -> w.getActivities().stream()).filter(a -> a.getId().equals(id)).findFirst().orElseThrow(() -> new NotFoundException("Activity not found: " + id)); }
    private Project requireProject(UUID id) { return projects.findById(id).orElseThrow(() -> new NotFoundException("Project not found: " + id)); }
    private WbsItem requireWbs(EstimateVersion e, UUID id) { return e.getWbsItems().stream().filter(w -> w.getId().equals(id)).findFirst().orElseThrow(() -> new NotFoundException("WBS item not found: " + id)); }
    private BoqItem requireBoq(EstimateVersion estimate, UUID id) { return estimate.getBoqItems().stream().filter(item -> item.getId().equals(id)).findFirst().orElseThrow(() -> new NotFoundException("BOQ item not found: " + id)); }
    private PricingRule requirePricingRule(EstimateVersion estimate, UUID id) { return estimate.getPricingRules().stream().filter(rule -> rule.getId().equals(id)).findFirst().orElseThrow(() -> new NotFoundException("Pricing rule not found: " + id)); }
    private void applyPricingRule(PricingRule rule, PricingRuleRequest request) { rule.setType(request.type()); rule.setName(request.name().trim()); rule.setPercentage(request.percentage()); rule.setBase(request.base()); rule.setSequence(request.sequence() == null ? 0 : request.sequence()); rule.setActive(!Boolean.FALSE.equals(request.active())); }
    private ActivityEquipmentAssignment requireEquipmentAssignment(EstimateVersion e, UUID id) { return e.getWbsItems().stream().flatMap(w -> w.getActivities().stream()).flatMap(a -> a.getResourceAssignments().stream()).filter(x -> x.getId().equals(id)).filter(ActivityEquipmentAssignment.class::isInstance).map(ActivityEquipmentAssignment.class::cast).findFirst().orElseThrow(() -> new NotFoundException("Equipment assignment not found: " + id)); }
    private void applyBoq(BoqItem item, EstimateVersion estimate, BoqRequest request) {
        WbsItem wbs = requireWbs(estimate, request.wbsId()); Activity activity = request.activityId() == null ? null : requireActivity(estimate, request.activityId());
        if (activity != null && activity.getWbsItem() != wbs) throw new IllegalArgumentException("BOQ activity must belong to the selected WBS");
        item.setCode(request.code().trim()); item.setDescription(request.description().trim()); item.setUnit(request.unit()); item.setQuantity(request.quantity());
        item.setUnitPrice(request.unitPrice()); item.setCurrency(Currency.getInstance(request.currencyCode().toUpperCase()));
        conversionRate(item.getCurrency(), estimate.getProject().getCurrency(), estimate.getProject().getUsdTryRate(), estimate.getProject().getEurTryRate());
        item.setWbsItem(wbs); item.setActivity(activity);
        if (activity != null) { activity.setPlannedQuantity(request.quantity()); activity.setQuantityUnit(request.unit()); if (activity.isAutoSchedule()) scheduleFromProduction(activity); applyDependencyConstraints(activity); synchronizeAssignmentSchedule(activity); rescheduleDependents(estimate, activity, new HashSet<>()); }
    }
    private void scheduleFromProduction(Activity activity) {
        if (!positive(activity.getPlannedQuantity()) || !positive(activity.getDailyProductionRate()) || activity.getPlannedStartDate() == null) return;
        int duration = activity.getPlannedQuantity().divide(activity.getDailyProductionRate(), 0, RoundingMode.CEILING).max(BigDecimal.ONE).intValueExact();
        activity.setPlannedDuration(BigDecimal.valueOf(duration)); activity.setDurationUnit(DurationUnit.DAY);
        activity.setPlannedStartDate(nextWorkingDay(activity.getPlannedStartDate(), activityCalendar(activity)));
        activity.setPlannedEndDate(addWorkingDays(activity.getPlannedStartDate(), duration - 1, activityCalendar(activity)));
    }
    private void applyDependencyConstraints(Activity activity) {
        if (activity.getDependencies().isEmpty()) return;
        WorkCalendar calendar = activityCalendar(activity); int duration = activity.getPlannedDuration() == null ? 1 : Math.max(1, activity.getPlannedDuration().intValue());
        LocalDate constrainedStart = activity.getPlannedStartDate(); LocalDate constrainedEnd = activity.getPlannedEndDate();
        for (ActivityDependency dependency : activity.getDependencies()) {
            Activity predecessor = dependency.getPredecessor(); if (predecessor.getPlannedStartDate() == null || predecessor.getPlannedEndDate() == null) continue;
            switch (dependency.getType()) {
                case FINISH_TO_START -> { LocalDate candidate = addWorkingDays(predecessor.getPlannedEndDate(), dependency.getLagDays() + 1, calendar); if (constrainedStart == null || candidate.isAfter(constrainedStart)) constrainedStart = candidate; }
                case START_TO_START -> { LocalDate candidate = addWorkingDays(predecessor.getPlannedStartDate(), dependency.getLagDays(), calendar); if (constrainedStart == null || candidate.isAfter(constrainedStart)) constrainedStart = candidate; }
                case FINISH_TO_FINISH -> { LocalDate candidate = addWorkingDays(predecessor.getPlannedEndDate(), dependency.getLagDays(), calendar); if (constrainedEnd == null || candidate.isAfter(constrainedEnd)) constrainedEnd = candidate; }
                case START_TO_FINISH -> { LocalDate candidate = addWorkingDays(predecessor.getPlannedStartDate(), dependency.getLagDays(), calendar); if (constrainedEnd == null || candidate.isAfter(constrainedEnd)) constrainedEnd = candidate; }
            }
        }
        if (constrainedEnd != null && (activity.getDependencies().stream().anyMatch(value -> value.getType() == DependencyType.FINISH_TO_FINISH || value.getType() == DependencyType.START_TO_FINISH))) {
            constrainedStart = subtractWorkingDays(constrainedEnd, duration - 1, calendar);
        } else if (constrainedStart != null) constrainedEnd = addWorkingDays(constrainedStart, duration - 1, calendar);
        activity.setPlannedStartDate(constrainedStart); activity.setPlannedEndDate(constrainedEnd);
    }
    private boolean dependsOn(Activity activity, Activity target, Set<UUID> visited) {
        if (!visited.add(activity.getId())) return false;
        return activity.getDependencies().stream().anyMatch(dependency -> dependency.getPredecessor() == target || dependsOn(dependency.getPredecessor(), target, visited));
    }
    private void rescheduleDependents(EstimateVersion estimate, Activity predecessor, Set<UUID> visited) {
        if (!visited.add(predecessor.getId())) return;
        estimate.getWbsItems().stream().flatMap(wbs -> wbs.getActivities().stream())
                .filter(activity -> activity.getDependencies().stream().anyMatch(dependency -> dependency.getPredecessor() == predecessor))
                .forEach(activity -> { applyDependencyConstraints(activity); synchronizeAssignmentSchedule(activity); rescheduleDependents(estimate, activity, visited); });
    }
    private WorkCalendar activityCalendar(Activity activity) { return activity.getWbsItem().getEstimateVersion().getProject().getWorkCalendar(); }
    private LocalDate nextWorkingDay(LocalDate date, WorkCalendar calendar) { LocalDate result = date; while (!isWorkingDay(result, calendar)) result = result.plusDays(1); return result; }
    private LocalDate addWorkingDays(LocalDate date, int days, WorkCalendar calendar) { LocalDate result = nextWorkingDay(date, calendar); for (int added = 0; added < days;) { result = result.plusDays(1); if (isWorkingDay(result, calendar)) added++; } return result; }
    private LocalDate subtractWorkingDays(LocalDate date, int days, WorkCalendar calendar) { LocalDate result = date; while (!isWorkingDay(result, calendar)) result = result.minusDays(1); for (int subtracted = 0; subtracted < days;) { result = result.minusDays(1); if (isWorkingDay(result, calendar)) subtracted++; } return result; }
    private boolean isWorkingDay(LocalDate date, WorkCalendar calendar) { if (calendar == null || calendar.getWorkingDaysPerWeek() == 0) return true; return date.getDayOfWeek().getValue() <= calendar.getWorkingDaysPerWeek(); }
    private long workingDayCount(LocalDate start, LocalDate end, WorkCalendar calendar) { long count = 0; for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) if (isWorkingDay(date, calendar)) count++; return Math.max(1, count); }
    private void apply(Project p, ProjectRequest r) { p.setCode(r.code()); p.setName(r.name()); p.setDescription(r.description()); p.setPlannedStartDate(r.plannedStartDate()); p.setPlannedEndDate(r.plannedEndDate()); p.setCurrency(Currency.getInstance(r.currencyCode().toUpperCase())); p.setLanguageCode("tr".equalsIgnoreCase(r.languageCode()) ? "tr" : "en"); if (r.usdTryRate() != null) p.setUsdTryRate(r.usdTryRate()); if (r.eurTryRate() != null) p.setEurTryRate(r.eurTryRate()); p.setStatus(r.status() == null ? ProjectStatus.DRAFT : r.status()); }
    private void validateDates(java.time.LocalDate start, java.time.LocalDate end) { if (start != null && end != null && end.isBefore(start)) throw new IllegalArgumentException("End date cannot be before start date"); }
    private void synchronizeAssignmentSchedule(Activity activity) {
        if (activity.getPlannedStartDate() == null || activity.getPlannedEndDate() == null) return;
        BigDecimal days = BigDecimal.valueOf(workingDayCount(activity.getPlannedStartDate(), activity.getPlannedEndDate(), activityCalendar(activity)));
        activity.getResourceAssignments().forEach(assignment -> {
            assignment.setStartDate(activity.getPlannedStartDate()); assignment.setEndDate(activity.getPlannedEndDate());
            BigDecimal quantity = assignment.getQuantity() == null ? BigDecimal.ONE : assignment.getQuantity();
            BigDecimal calendarHours = activity.getWbsItem().getEstimateVersion().getProject().getWorkCalendar() == null
                    ? BigDecimal.valueOf(8) : activity.getWbsItem().getEstimateVersion().getProject().getWorkCalendar().getWorkingHoursPerDay();
            BigDecimal hoursPerDay = assignment instanceof ActivityEquipmentAssignment equipment && positive(equipment.getOperatingHoursPerDay())
                    ? equipment.getOperatingHoursPerDay() : calendarHours;
            if (assignment.getWorkUnit() == WorkUnit.PERSON_HOUR || assignment.getWorkUnit() == WorkUnit.EQUIPMENT_HOUR)
                assignment.setPlannedWork(days.multiply(hoursPerDay).multiply(quantity));
            else if (assignment.getWorkUnit() == WorkUnit.PERSON_DAY || assignment.getWorkUnit() == WorkUnit.EQUIPMENT_DAY)
                assignment.setPlannedWork(days.multiply(quantity));
        });
    }
    private BigDecimal derivedWork(ResourceAssignment assignment) {
        if (assignment.getStartDate() == null || assignment.getEndDate() == null || assignment.getWorkUnit() == null) return BigDecimal.ZERO;
        BigDecimal days = BigDecimal.valueOf(workingDayCount(assignment.getStartDate(), assignment.getEndDate(), activityCalendar(assignment.getActivity())));
        BigDecimal quantity = assignment.getQuantity() == null ? BigDecimal.ONE : assignment.getQuantity();
        if (assignment.getWorkUnit() == WorkUnit.PERSON_DAY || assignment.getWorkUnit() == WorkUnit.EQUIPMENT_DAY) return days.multiply(quantity);
        if (assignment.getWorkUnit() != WorkUnit.PERSON_HOUR && assignment.getWorkUnit() != WorkUnit.EQUIPMENT_HOUR) return BigDecimal.ZERO;
        BigDecimal hours = assignment instanceof ActivityEquipmentAssignment equipment && positive(equipment.getOperatingHoursPerDay())
                ? equipment.getOperatingHoursPerDay() : assignment.getActivity().getWbsItem().getEstimateVersion().getProject().getWorkCalendar() == null
                ? BigDecimal.valueOf(8) : assignment.getActivity().getWbsItem().getEstimateVersion().getProject().getWorkCalendar().getWorkingHoursPerDay();
        return days.multiply(hours).multiply(quantity);
    }
    private void synchronizeResourceRates(EstimateVersion estimate, Resource resource, boolean replaceExisting) {
        if (replaceExisting) {
            Set<UUID> currentComponents = resource.getCostComponents().stream().map(CostComponent::getId).collect(java.util.stream.Collectors.toSet());
            estimate.getResourceRates().removeIf(rate -> rate.getResourceId().equals(resource.getId()) && !currentComponents.contains(rate.getSourceCostComponentId()));
        }
        resource.getCostComponents().forEach(component -> {
            EstimateResourceRate existing = estimate.getResourceRates().stream()
                    .filter(rate -> rate.getSourceCostComponentId().equals(component.getId())).findFirst().orElse(null);
            if (existing != null && !replaceExisting) return;
            EstimateResourceRate rate = existing == null ? new EstimateResourceRate() : existing;
            if (existing == null) { rate.setId(UUID.randomUUID()); rate.setResourceId(resource.getId()); rate.setSourceCostComponentId(component.getId()); rate.setEstimateVersion(estimate); estimate.getResourceRates().add(rate); }
            rate.setCategory(component.getCategory()); rate.setName(component.getName()); rate.setCalculationBasis(component.getCalculationBasis());
            Currency sourceCurrency = component.getCurrency() == null ? Currency.getInstance("USD") : component.getCurrency();
            BigDecimal conversion = conversionRate(sourceCurrency, estimate.getProject().getCurrency(), estimate.getProject().getUsdTryRate(), estimate.getProject().getEurTryRate());
            rate.setUnitPrice(convert(component.getUnitPrice(), conversion)); rate.setUnit(component.getUnit()); rate.setTaxable(component.isTaxable()); rate.setTaxRate(component.getTaxRate());
            rate.setValidFrom(component.getValidFrom()); rate.setValidTo(component.getValidTo());
        });
    }
    private void convertProjectPrices(Project project, BigDecimal rate, Currency source, Currency target) {
        project.getEstimateVersions().forEach(estimate -> {
            estimate.getResourceRates().forEach(resourceRate -> resourceRate.setUnitPrice(convert(resourceRate.getUnitPrice(), rate)));
            estimate.getProjectLevelCosts().forEach(cost -> cost.setUnitPrice(convert(cost.getUnitPrice(), rate)));
            estimate.getWbsItems().stream().flatMap(wbs -> wbs.getActivities().stream()).flatMap(activity -> activity.getAdditionalCostItems().stream()).forEach(cost -> cost.setUnitPrice(convert(cost.getUnitPrice(), rate)));
            var exchangeRate = new ProjectRate(); exchangeRate.setId(UUID.randomUUID()); exchangeRate.setRateType(RateType.EXCHANGE_RATE);
            exchangeRate.setName("1 " + source.getCurrencyCode() + " = " + rate.stripTrailingZeros().toPlainString() + " " + target.getCurrencyCode());
            exchangeRate.setValue(rate); exchangeRate.setValidFrom(java.time.LocalDate.now()); exchangeRate.setEstimateVersion(estimate); estimate.getProjectRates().add(exchangeRate);
        });
    }
    private BigDecimal convert(BigDecimal value, BigDecimal rate) { return value == null ? null : value.multiply(rate).setScale(4, java.math.RoundingMode.HALF_UP).stripTrailingZeros(); }
    private BigDecimal conversionRate(Currency source, Currency target, BigDecimal usdTry, BigDecimal eurTry) {
        if (source.equals(target)) return BigDecimal.ONE;
        BigDecimal sourceInTry = currencyInTry(source, usdTry, eurTry);
        BigDecimal targetInTry = currencyInTry(target, usdTry, eurTry);
        return sourceInTry.divide(targetInTry, 10, java.math.RoundingMode.HALF_UP);
    }
    private BigDecimal currencyInTry(Currency currency, BigDecimal usdTry, BigDecimal eurTry) { return switch (currency.getCurrencyCode()) { case "TRY" -> BigDecimal.ONE; case "USD" -> requireRate(usdTry, "USD/TRY"); case "EUR" -> requireRate(eurTry, "EUR/TRY"); default -> throw new IllegalArgumentException("Unsupported currency: " + currency); }; }
    private BigDecimal requireRate(BigDecimal rate, String name) { if (rate == null || rate.signum() <= 0) throw new IllegalArgumentException(name + " rate is required for currency conversion"); return rate; }
    private boolean positive(BigDecimal value) { return value != null && value.signum() > 0; }
    private ProjectSummary summary(Project p) { return new ProjectSummary(p.getId(), p.getCode(), p.getName(), p.getDescription(), p.getPlannedStartDate(), p.getPlannedEndDate(), p.getCurrency(), p.getLanguageCode(), p.getUsdTryRate(), p.getEurTryRate(), p.getStatus()); }
    private ProjectDetail detail(Project p) { return new ProjectDetail(summary(p), p.getEstimateVersions().stream().map(this::estimateView).toList()); }
    private EstimateView estimateView(EstimateVersion e) { return new EstimateView(e.getId(), e.getName(), e.getDescription(), e.getVersionNumber(), e.getStatus(), e.getWbsItems().stream().map(this::wbsView).toList(), e.getProjectStaffAssignments().stream().map(this::staffView).toList()); }
    private WbsView wbsView(WbsItem w) { return new WbsView(w.getId(), w.getCode(), w.getName(), w.getDescription(), w.getSequence(), w.getParent() == null ? null : w.getParent().getId(), w.getActivities().stream().map(this::activityView).toList()); }
    private ActivityView activityView(Activity a) { return new ActivityView(a.getId(), a.getCode(), a.getName(), a.getType(), a.getPlannedQuantity(), a.getQuantityUnit(), a.getPlannedDuration(), a.getDurationUnit(), a.getPlannedStartDate(), a.getPlannedEndDate(), a.getDailyProductionRate(), a.isAutoSchedule(), a.getDependencies().stream().map(this::dependencyView).toList(), a.getResourceAssignments().stream().map(this::assignmentView).toList()); }
    private DependencyView dependencyView(ActivityDependency dependency) { return new DependencyView(dependency.getId(), dependency.getPredecessor().getId(), dependency.getPredecessor().getCode(), dependency.getPredecessor().getName(), dependency.getType(), dependency.getLagDays()); }
    private BoqView boqView(BoqItem item) { return new BoqView(item.getId(), item.getCode(), item.getDescription(), item.getUnit(), item.getQuantity(), item.getUnitPrice(), item.getCurrency().getCurrencyCode(), item.getQuantity().multiply(item.getUnitPrice()), item.getWbsItem().getId(), item.getWbsItem().getCode(), item.getWbsItem().getName(), item.getActivity() == null ? null : item.getActivity().getId(), item.getActivity() == null ? null : item.getActivity().getCode(), item.getActivity() == null ? null : item.getActivity().getName()); }
    private CalendarView calendarView(WorkCalendar calendar) { if (calendar == null) return null; return new CalendarView(calendar.getId(), calendar.getName(), calendar.getWorkingDaysPerWeek(), calendar.getWorkingHoursPerDay(), calendar.getShifts().stream().map(shift -> new ShiftView(shift.getId(), shift.getName(), shift.getStartTime(), shift.getEndTime(), shift.getPaidHours())).toList()); }
    private PricingRuleView pricingRuleView(PricingRule rule) { return new PricingRuleView(rule.getId(), rule.getType(), rule.getName(), rule.getPercentage(), rule.getBase(), rule.getSequence(), rule.isActive()); }
    private Resource resourceFor(ResourceAssignment assignment) { return assignment instanceof ActivityEquipmentAssignment value ? value.getEquipmentResource() : assignment instanceof ActivityPersonnelAssignment value ? value.getPersonnelResource() : ((ActivityMaterialAssignment) assignment).getMaterialResource(); }
    private AssignmentView assignmentView(ResourceAssignment a) {
        Resource resource = resourceFor(a);
        return new AssignmentView(a.getId(), resource.getId(), resource.getName(), resource.getClass().getSimpleName().replace("Resource", "").toUpperCase(),
                a.getQuantity(), a.getPlannedWork(), a.getWorkUnit(), a.getUtilizationRate(), a.getStartDate(), a.getEndDate(), a.isOvertimeAllowed(),
                a instanceof ActivityPersonnelAssignment value ? value.getAssignmentType() : null,
                a instanceof ActivityEquipmentAssignment value ? value.getOperatingHoursPerDay() : null,
                a instanceof ActivityEquipmentAssignment value ? value.getStandbyHoursPerDay() : null,
                a instanceof ActivityMaterialAssignment value ? value.getRequiredQuantity() : null,
                a instanceof ActivityMaterialAssignment value ? value.getWastePercentage() : null);
    }
    private CrewView crewView(EquipmentCrewAssignment c) { return new CrewView(c.getId(), c.getPersonnelResource().getId(), c.getPersonnelResource().getName(), c.getRoleName(), c.getQuantity(), c.getWorkingHoursPerDay(), c.isMandatory()); }
    private StaffView staffView(ProjectStaffAssignment s) { return new StaffView(s.getId(), s.getPersonnelResource().getId(), s.getPersonnelResource().getName(), s.getProjectRole(), s.getQuantity(), s.getAllocationPercentage(), s.getStartDate(), s.getEndDate()); }
    private EstimateRateView rateView(EstimateResourceRate rate) { return new EstimateRateView(rate.getId(), rate.getResourceId(), rate.getSourceCostComponentId(), rate.getCategory(), rate.getName(), rate.getCalculationBasis(), rate.getUnitPrice(), rate.getUnit(), rate.isTaxable(), rate.getTaxRate(), rate.getValidFrom(), rate.getValidTo()); }
}
