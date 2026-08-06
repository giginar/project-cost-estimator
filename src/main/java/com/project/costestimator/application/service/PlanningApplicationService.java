package com.project.costestimator.application.service;

import com.project.costestimator.application.port.in.PlanningUseCase;
import com.project.costestimator.application.port.out.ProjectRepositoryPort;
import com.project.costestimator.application.service.support.ProjectFinder;
import com.project.costestimator.application.service.support.ProjectViewMapper;
import com.project.costestimator.domain.*;
import com.project.costestimator.domain.enums.ActivityType;
import com.project.costestimator.domain.service.ActivitySchedulingPolicy;
import com.project.costestimator.domain.service.DateRangePolicy;
import com.project.costestimator.dto.ApiModels.*;
import com.project.costestimator.exception.NotFoundException;

import java.math.BigDecimal;
import java.util.UUID;

public final class PlanningApplicationService implements PlanningUseCase {
    private final ProjectRepositoryPort projects;
    private final ProjectFinder finder;
    private final ProjectViewMapper views;
    private final DateRangePolicy dateRanges;
    private final ActivitySchedulingPolicy scheduling;

    public PlanningApplicationService(ProjectRepositoryPort projects,
                                      ProjectFinder finder,
                                      ProjectViewMapper views,
                                      DateRangePolicy dateRanges,
                                      ActivitySchedulingPolicy scheduling) {
        this.projects = projects;
        this.finder = finder;
        this.views = views;
        this.dateRanges = dateRanges;
        this.scheduling = scheduling;
    }

    @Override
    public ActivityView addActivity(UUID projectId, UUID estimateId, UUID wbsId, ActivityRequest request) {
        EstimateVersion estimate = finder.requireEstimate(projectId, estimateId);
        WbsItem wbs = finder.requireWbs(estimate, wbsId);
        dateRanges.validate(request.plannedStartDate(), request.plannedEndDate());

        Activity activity = new Activity();
        activity.setId(UUID.randomUUID());
        applyActivity(activity, request, true);
        activity.setWbsItem(wbs);
        wbs.getActivities().add(activity);
        if (activity.isAutoSchedule()) {
            scheduling.scheduleFromProduction(activity);
        }
        projects.save(estimate.getProject());
        return views.toActivityView(activity);
    }

    @Override
    public ActivityView updateActivity(UUID projectId, UUID estimateId, UUID activityId,
                                       ActivityRequest request) {
        EstimateVersion estimate = finder.requireEstimate(projectId, estimateId);
        Activity activity = finder.requireActivity(estimate, activityId);
        dateRanges.validate(request.plannedStartDate(), request.plannedEndDate());
        applyActivity(activity, request, false);

        if (activity.isAutoSchedule()) {
            scheduling.scheduleFromProduction(activity);
        }
        scheduling.applyDependencyConstraints(activity);
        scheduling.synchronizeAssignmentSchedule(activity);
        scheduling.rescheduleDependents(estimate, activity);
        projects.save(estimate.getProject());
        return views.toActivityView(activity);
    }

    @Override
    public ActivityView updateActivityPlanning(UUID projectId, UUID estimateId, UUID activityId,
                                               ActivityPlanningRequest request) {
        EstimateVersion estimate = finder.requireEstimate(projectId, estimateId);
        Activity activity = finder.requireActivity(estimate, activityId);
        activity.setPlannedQuantity(request.plannedQuantity());
        activity.setQuantityUnit(request.quantityUnit());
        activity.setDailyProductionRate(request.dailyProductionRate());
        activity.setAutoSchedule(Boolean.TRUE.equals(request.autoSchedule()));
        activity.setPlannedStartDate(request.plannedStartDate());

        if (activity.isAutoSchedule()) {
            scheduling.scheduleFromProduction(activity);
        } else if (activity.getPlannedEndDate() != null) {
            dateRanges.validate(activity.getPlannedStartDate(), activity.getPlannedEndDate());
        }
        scheduling.applyDependencyConstraints(activity);
        scheduling.synchronizeAssignmentSchedule(activity);
        scheduling.rescheduleDependents(estimate, activity);
        projects.save(estimate.getProject());
        return views.toActivityView(activity);
    }

    @Override
    public DependencyView addDependency(UUID projectId, UUID estimateId, UUID activityId,
                                        DependencyRequest request) {
        EstimateVersion estimate = finder.requireEstimate(projectId, estimateId);
        Activity successor = finder.requireActivity(estimate, activityId);
        Activity predecessor = finder.requireActivity(estimate, request.predecessorActivityId());
        validateDependency(successor, predecessor);

        ActivityDependency dependency = new ActivityDependency();
        dependency.setId(UUID.randomUUID());
        dependency.setSuccessor(successor);
        dependency.setPredecessor(predecessor);
        dependency.setType(request.type());
        dependency.setLagDays(request.lagDays() == null ? 0 : request.lagDays());
        successor.getDependencies().add(dependency);

        scheduling.applyDependencyConstraints(successor);
        scheduling.synchronizeAssignmentSchedule(successor);
        scheduling.rescheduleDependents(estimate, successor);
        projects.save(estimate.getProject());
        return views.toDependencyView(dependency);
    }

    @Override
    public void deleteDependency(UUID projectId, UUID estimateId, UUID activityId, UUID dependencyId) {
        EstimateVersion estimate = finder.requireEstimate(projectId, estimateId);
        Activity activity = finder.requireActivity(estimate, activityId);
        boolean removed = activity.getDependencies().removeIf(dependency -> dependency.getId().equals(dependencyId));
        if (!removed) {
            throw new NotFoundException("Dependency not found: " + dependencyId);
        }
        projects.save(estimate.getProject());
    }

    @Override
    public CalendarView getCalendar(UUID projectId) {
        return views.toCalendarView(finder.requireProject(projectId).getWorkCalendar());
    }

    @Override
    public CalendarView updateCalendar(UUID projectId, CalendarRequest request) {
        Project project = finder.requireProject(projectId);
        WorkCalendar calendar = project.getWorkCalendar() == null
                ? new WorkCalendar()
                : project.getWorkCalendar();
        if (calendar.getId() == null) {
            calendar.setId(UUID.randomUUID());
        }
        calendar.setProject(project);
        calendar.setName(request.name());
        calendar.setWorkingDaysPerWeek(request.workingDaysPerWeek());
        replaceShifts(calendar, request);
        calendar.setWorkingHoursPerDay(calendar.getShifts().stream()
                .map(Shift::getPaidHours)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        project.setWorkCalendar(calendar);
        rescheduleProjectActivities(project);
        projects.save(project);
        return views.toCalendarView(calendar);
    }

    private void applyActivity(Activity activity, ActivityRequest request, boolean creating) {
        activity.setCode(request.code());
        activity.setName(request.name());
        activity.setDescription(request.description());
        activity.setType(request.type() == null ? ActivityType.WORK : request.type());
        activity.setPlannedQuantity(request.plannedQuantity());
        activity.setQuantityUnit(request.quantityUnit());
        activity.setPlannedDuration(request.plannedDuration());
        activity.setDurationUnit(request.durationUnit());
        activity.setPlannedStartDate(request.plannedStartDate());
        activity.setPlannedEndDate(request.plannedEndDate());
        if (creating || request.dailyProductionRate() != null) {
            activity.setDailyProductionRate(request.dailyProductionRate());
        }
        if (creating) {
            activity.setAutoSchedule(Boolean.TRUE.equals(request.autoSchedule()));
        } else if (request.autoSchedule() != null) {
            activity.setAutoSchedule(request.autoSchedule());
        }
    }

    private void validateDependency(Activity successor, Activity predecessor) {
        if (successor == predecessor) {
            throw new IllegalArgumentException("An activity cannot depend on itself");
        }
        if (scheduling.wouldCreateCycle(predecessor, successor)) {
            throw new IllegalArgumentException("Activity dependency would create a cycle");
        }
        boolean duplicate = successor.getDependencies().stream()
                .anyMatch(dependency -> dependency.getPredecessor().getId().equals(predecessor.getId()));
        if (duplicate) {
            throw new IllegalArgumentException("Dependency already exists");
        }
    }

    private void replaceShifts(WorkCalendar calendar, CalendarRequest request) {
        calendar.getShifts().clear();
        request.shifts().forEach(value -> {
            Shift shift = new Shift();
            shift.setId(UUID.randomUUID());
            shift.setName(value.name());
            shift.setStartTime(value.startTime());
            shift.setEndTime(value.endTime());
            shift.setPaidHours(value.paidHours());
            shift.setWorkCalendar(calendar);
            calendar.getShifts().add(shift);
        });
    }

    private void rescheduleProjectActivities(Project project) {
        project.getEstimateVersions().forEach(estimate ->
                estimate.getWbsItems().stream()
                        .flatMap(wbs -> wbs.getActivities().stream())
                        .forEach(activity -> {
                            if (activity.isAutoSchedule()) {
                                scheduling.scheduleFromProduction(activity);
                            }
                            scheduling.applyDependencyConstraints(activity);
                            scheduling.synchronizeAssignmentSchedule(activity);
                        }));
    }
}
