package com.project.costestimator.application.service.support;

import com.project.costestimator.domain.*;
import com.project.costestimator.domain.Resource;
import com.project.costestimator.dto.ApiModels.*;

public final class ProjectViewMapper {
    public ProjectSummary toSummary(Project project) {
        return new ProjectSummary(
                project.getId(), project.getCode(), project.getName(), project.getDescription(),
                project.getPlannedStartDate(), project.getPlannedEndDate(), project.getCurrency(),
                project.getLanguageCode(), project.getUsdTryRate(), project.getEurTryRate(), project.getStatus());
    }

    public ProjectDetail toDetail(Project project) {
        return new ProjectDetail(
                toSummary(project),
                project.getEstimateVersions().stream().map(this::toEstimateView).toList());
    }

    public EstimateView toEstimateView(EstimateVersion estimate) {
        return new EstimateView(
                estimate.getId(), estimate.getName(), estimate.getDescription(), estimate.getVersionNumber(),
                estimate.getStatus(), estimate.getWbsItems().stream().map(this::toWbsView).toList(),
                estimate.getProjectStaffAssignments().stream().map(this::toStaffView).toList());
    }

    public WbsView toWbsView(WbsItem wbs) {
        return new WbsView(
                wbs.getId(), wbs.getCode(), wbs.getName(), wbs.getDescription(), wbs.getSequence(),
                wbs.getParent() == null ? null : wbs.getParent().getId(),
                wbs.getActivities().stream().map(this::toActivityView).toList());
    }

    public ActivityView toActivityView(Activity activity) {
        return new ActivityView(
                activity.getId(), activity.getCode(), activity.getName(), activity.getType(),
                activity.getPlannedQuantity(), activity.getQuantityUnit(), activity.getPlannedDuration(),
                activity.getDurationUnit(), activity.getPlannedStartDate(), activity.getPlannedEndDate(),
                activity.getDailyProductionRate(), activity.isAutoSchedule(),
                activity.getDependencies().stream().map(this::toDependencyView).toList(),
                activity.getResourceAssignments().stream().map(this::toAssignmentView).toList());
    }

    public DependencyView toDependencyView(ActivityDependency dependency) {
        Activity predecessor = dependency.getPredecessor();
        return new DependencyView(
                dependency.getId(), predecessor.getId(), predecessor.getCode(), predecessor.getName(),
                dependency.getType(), dependency.getLagDays());
    }

    public AssignmentView toAssignmentView(ResourceAssignment assignment) {
        Resource resource = resourceOf(assignment);
        return new AssignmentView(
                assignment.getId(), resource.getId(), resource.getName(), resourceType(resource),
                assignment.getQuantity(), assignment.getPlannedWork(), assignment.getWorkUnit(),
                assignment.getUtilizationRate(), assignment.getStartDate(), assignment.getEndDate(),
                assignment.isOvertimeAllowed(),
                assignment instanceof ActivityPersonnelAssignment personnel ? personnel.getAssignmentType() : null,
                assignment instanceof ActivityEquipmentAssignment equipment ? equipment.getOperatingHoursPerDay() : null,
                assignment instanceof ActivityEquipmentAssignment equipment ? equipment.getStandbyHoursPerDay() : null,
                assignment instanceof ActivityMaterialAssignment material ? material.getRequiredQuantity() : null,
                assignment instanceof ActivityMaterialAssignment material ? material.getWastePercentage() : null);
    }

    public CrewView toCrewView(EquipmentCrewAssignment crew) {
        return new CrewView(
                crew.getId(), crew.getPersonnelResource().getId(), crew.getPersonnelResource().getName(),
                crew.getRoleName(), crew.getQuantity(), crew.getWorkingHoursPerDay(), crew.isMandatory());
    }

    public StaffView toStaffView(ProjectStaffAssignment staff) {
        return new StaffView(
                staff.getId(), staff.getPersonnelResource().getId(), staff.getPersonnelResource().getName(),
                staff.getProjectRole(), staff.getQuantity(), staff.getAllocationPercentage(),
                staff.getStartDate(), staff.getEndDate());
    }

    public BoqView toBoqView(BoqItem item) {
        Activity activity = item.getActivity();
        return new BoqView(
                item.getId(), item.getCode(), item.getDescription(), item.getUnit(), item.getQuantity(),
                item.getUnitPrice(), item.getCurrency().getCurrencyCode(),
                item.getQuantity().multiply(item.getUnitPrice()),
                item.getWbsItem().getId(), item.getWbsItem().getCode(), item.getWbsItem().getName(),
                activity == null ? null : activity.getId(),
                activity == null ? null : activity.getCode(),
                activity == null ? null : activity.getName());
    }

    public CalendarView toCalendarView(WorkCalendar calendar) {
        if (calendar == null) {
            return null;
        }
        return new CalendarView(
                calendar.getId(), calendar.getName(), calendar.getWorkingDaysPerWeek(),
                calendar.getWorkingHoursPerDay(),
                calendar.getShifts().stream().map(this::toShiftView).toList());
    }

    public PricingRuleView toPricingRuleView(PricingRule rule) {
        return new PricingRuleView(
                rule.getId(), rule.getType(), rule.getName(), rule.getPercentage(),
                rule.getBase(), rule.getSequence(), rule.isActive());
    }

    public EstimateRateView toEstimateRateView(EstimateResourceRate rate) {
        return new EstimateRateView(
                rate.getId(), rate.getResourceId(), rate.getSourceCostComponentId(), rate.getCategory(),
                rate.getName(), rate.getCalculationBasis(), rate.getUnitPrice(), rate.getUnit(),
                rate.isTaxable(), rate.getTaxRate(), rate.getValidFrom(), rate.getValidTo());
    }

    public Resource resourceOf(ResourceAssignment assignment) {
        if (assignment instanceof ActivityEquipmentAssignment equipment) {
            return equipment.getEquipmentResource();
        }
        if (assignment instanceof ActivityPersonnelAssignment personnel) {
            return personnel.getPersonnelResource();
        }
        return ((ActivityMaterialAssignment) assignment).getMaterialResource();
    }

    private ShiftView toShiftView(Shift shift) {
        return new ShiftView(
                shift.getId(), shift.getName(), shift.getStartTime(), shift.getEndTime(), shift.getPaidHours());
    }

    private String resourceType(Resource resource) {
        return resource.getClass().getSimpleName().replace("Resource", "").toUpperCase();
    }
}
