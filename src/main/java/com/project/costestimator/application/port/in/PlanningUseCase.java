package com.project.costestimator.application.port.in;

import com.project.costestimator.dto.ApiModels.ActivityPlanningRequest;
import com.project.costestimator.dto.ApiModels.ActivityRequest;
import com.project.costestimator.dto.ApiModels.ActivityView;
import com.project.costestimator.dto.ApiModels.CalendarRequest;
import com.project.costestimator.dto.ApiModels.CalendarView;
import com.project.costestimator.dto.ApiModels.DependencyRequest;
import com.project.costestimator.dto.ApiModels.DependencyView;

import java.util.UUID;

public interface PlanningUseCase {
    ActivityView addActivity(UUID projectId, UUID estimateId, UUID wbsId, ActivityRequest request);
    ActivityView updateActivity(UUID projectId, UUID estimateId, UUID activityId, ActivityRequest request);
    ActivityView updateActivityPlanning(UUID projectId, UUID estimateId, UUID activityId, ActivityPlanningRequest request);
    DependencyView addDependency(UUID projectId, UUID estimateId, UUID activityId, DependencyRequest request);
    void deleteDependency(UUID projectId, UUID estimateId, UUID activityId, UUID dependencyId);
    CalendarView getCalendar(UUID projectId);
    CalendarView updateCalendar(UUID projectId, CalendarRequest request);
}
