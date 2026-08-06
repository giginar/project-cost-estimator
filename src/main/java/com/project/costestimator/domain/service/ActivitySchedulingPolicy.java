package com.project.costestimator.domain.service;

import com.project.costestimator.domain.Activity;
import com.project.costestimator.domain.ActivityDependency;
import com.project.costestimator.domain.ActivityEquipmentAssignment;
import com.project.costestimator.domain.EstimateVersion;
import com.project.costestimator.domain.ResourceAssignment;
import com.project.costestimator.domain.WorkCalendar;
import com.project.costestimator.domain.enums.DependencyType;
import com.project.costestimator.domain.enums.DurationUnit;
import com.project.costestimator.domain.enums.WorkUnit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ActivitySchedulingPolicy {
    private final WorkCalendarPolicy calendars;

    public ActivitySchedulingPolicy(WorkCalendarPolicy calendars) {
        this.calendars = calendars;
    }

    public void scheduleFromProduction(Activity activity) {
        if (!isPositive(activity.getPlannedQuantity())
                || !isPositive(activity.getDailyProductionRate())
                || activity.getPlannedStartDate() == null) {
            return;
        }

        int duration = activity.getPlannedQuantity()
                .divide(activity.getDailyProductionRate(), 0, RoundingMode.CEILING)
                .max(BigDecimal.ONE)
                .intValueExact();
        WorkCalendar calendar = calendarOf(activity);
        LocalDate startDate = calendars.nextWorkingDay(activity.getPlannedStartDate(), calendar);

        activity.setPlannedDuration(BigDecimal.valueOf(duration));
        activity.setDurationUnit(DurationUnit.DAY);
        activity.setPlannedStartDate(startDate);
        activity.setPlannedEndDate(calendars.addWorkingDays(startDate, duration - 1, calendar));
    }

    public void applyDependencyConstraints(Activity activity) {
        if (activity.getDependencies().isEmpty()) {
            return;
        }

        WorkCalendar calendar = calendarOf(activity);
        int duration = activity.getPlannedDuration() == null
                ? 1
                : Math.max(1, activity.getPlannedDuration().intValue());
        LocalDate constrainedStart = activity.getPlannedStartDate();
        LocalDate constrainedEnd = activity.getPlannedEndDate();

        for (ActivityDependency dependency : activity.getDependencies()) {
            Activity predecessor = dependency.getPredecessor();
            if (predecessor.getPlannedStartDate() == null || predecessor.getPlannedEndDate() == null) {
                continue;
            }
            switch (dependency.getType()) {
                case FINISH_TO_START -> constrainedStart = latest(
                        constrainedStart,
                        calendars.addWorkingDays(predecessor.getPlannedEndDate(), dependency.getLagDays() + 1, calendar));
                case START_TO_START -> constrainedStart = latest(
                        constrainedStart,
                        calendars.addWorkingDays(predecessor.getPlannedStartDate(), dependency.getLagDays(), calendar));
                case FINISH_TO_FINISH -> constrainedEnd = latest(
                        constrainedEnd,
                        calendars.addWorkingDays(predecessor.getPlannedEndDate(), dependency.getLagDays(), calendar));
                case START_TO_FINISH -> constrainedEnd = latest(
                        constrainedEnd,
                        calendars.addWorkingDays(predecessor.getPlannedStartDate(), dependency.getLagDays(), calendar));
            }
        }

        boolean endConstrained = activity.getDependencies().stream()
                .map(ActivityDependency::getType)
                .anyMatch(type -> type == DependencyType.FINISH_TO_FINISH || type == DependencyType.START_TO_FINISH);
        if (constrainedEnd != null && endConstrained) {
            constrainedStart = calendars.subtractWorkingDays(constrainedEnd, duration - 1, calendar);
        } else if (constrainedStart != null) {
            constrainedEnd = calendars.addWorkingDays(constrainedStart, duration - 1, calendar);
        }

        activity.setPlannedStartDate(constrainedStart);
        activity.setPlannedEndDate(constrainedEnd);
    }

    public boolean wouldCreateCycle(Activity predecessor, Activity successor) {
        return dependsOn(predecessor, successor, new HashSet<>());
    }

    public void rescheduleDependents(EstimateVersion estimate, Activity predecessor) {
        rescheduleDependents(estimate, predecessor, new HashSet<>());
    }

    public void synchronizeAssignmentSchedule(Activity activity) {
        if (activity.getPlannedStartDate() == null || activity.getPlannedEndDate() == null) {
            return;
        }
        BigDecimal days = BigDecimal.valueOf(calendars.workingDayCount(
                activity.getPlannedStartDate(), activity.getPlannedEndDate(), calendarOf(activity)));

        for (ResourceAssignment assignment : activity.getResourceAssignments()) {
            assignment.setStartDate(activity.getPlannedStartDate());
            assignment.setEndDate(activity.getPlannedEndDate());
            BigDecimal quantity = defaultOne(assignment.getQuantity());
            BigDecimal hoursPerDay = assignment instanceof ActivityEquipmentAssignment equipment
                    && isPositive(equipment.getOperatingHoursPerDay())
                    ? equipment.getOperatingHoursPerDay()
                    : calendars.hoursPerDay(calendarOf(activity));

            if (isHourly(assignment.getWorkUnit())) {
                assignment.setPlannedWork(days.multiply(hoursPerDay).multiply(quantity));
            } else if (isDaily(assignment.getWorkUnit())) {
                assignment.setPlannedWork(days.multiply(quantity));
            }
        }
    }

    public BigDecimal deriveWork(ResourceAssignment assignment) {
        if (assignment.getStartDate() == null || assignment.getEndDate() == null
                || assignment.getWorkUnit() == null) {
            return BigDecimal.ZERO;
        }

        Activity activity = assignment.getActivity();
        BigDecimal days = BigDecimal.valueOf(calendars.workingDayCount(
                assignment.getStartDate(), assignment.getEndDate(), calendarOf(activity)));
        BigDecimal quantity = defaultOne(assignment.getQuantity());
        if (isDaily(assignment.getWorkUnit())) {
            return days.multiply(quantity);
        }
        if (!isHourly(assignment.getWorkUnit())) {
            return BigDecimal.ZERO;
        }

        BigDecimal hours = assignment instanceof ActivityEquipmentAssignment equipment
                && isPositive(equipment.getOperatingHoursPerDay())
                ? equipment.getOperatingHoursPerDay()
                : calendars.hoursPerDay(calendarOf(activity));
        return days.multiply(hours).multiply(quantity);
    }

    private void rescheduleDependents(EstimateVersion estimate, Activity predecessor, Set<UUID> visited) {
        if (!visited.add(predecessor.getId())) {
            return;
        }
        estimate.getWbsItems().stream()
                .flatMap(wbs -> wbs.getActivities().stream())
                .filter(activity -> activity.getDependencies().stream()
                        .anyMatch(dependency -> dependency.getPredecessor() == predecessor))
                .forEach(activity -> {
                    applyDependencyConstraints(activity);
                    synchronizeAssignmentSchedule(activity);
                    rescheduleDependents(estimate, activity, visited);
                });
    }

    private boolean dependsOn(Activity activity, Activity target, Set<UUID> visited) {
        if (!visited.add(activity.getId())) {
            return false;
        }
        return activity.getDependencies().stream()
                .anyMatch(dependency -> dependency.getPredecessor() == target
                        || dependsOn(dependency.getPredecessor(), target, visited));
    }

    private WorkCalendar calendarOf(Activity activity) {
        return activity.getWbsItem().getEstimateVersion().getProject().getWorkCalendar();
    }

    private LocalDate latest(LocalDate current, LocalDate candidate) {
        return current == null || candidate.isAfter(current) ? candidate : current;
    }

    private boolean isHourly(WorkUnit unit) {
        return unit == WorkUnit.PERSON_HOUR || unit == WorkUnit.EQUIPMENT_HOUR;
    }

    private boolean isDaily(WorkUnit unit) {
        return unit == WorkUnit.PERSON_DAY || unit == WorkUnit.EQUIPMENT_DAY;
    }

    private BigDecimal defaultOne(BigDecimal value) {
        return value == null ? BigDecimal.ONE : value;
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }
}
