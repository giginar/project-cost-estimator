package com.project.costestimator.controller;

import com.project.costestimator.application.port.in.PlanningUseCase;
import com.project.costestimator.dto.ApiModels.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
@Tag(name = "Planning", description = "Activity scheduling, dependency, and work calendar operations")
public class PlanningController {
    private final PlanningUseCase planning;

    public PlanningController(PlanningUseCase planning) {
        this.planning = planning;
    }

    @Operation(summary = "Add an activity to a WBS item")
    @PostMapping("/{projectId}/estimates/{estimateId}/wbs-items/{wbsId}/activities")
    @ResponseStatus(HttpStatus.CREATED)
    public ActivityView addActivity(@PathVariable UUID projectId,
                                    @PathVariable UUID estimateId,
                                    @PathVariable UUID wbsId,
                                    @Valid @RequestBody ActivityRequest request) {
        return planning.addActivity(projectId, estimateId, wbsId, request);
    }

    @Operation(summary = "Update an activity",
            description = "Updates activity details and its planned date range. Used after a Gantt bar is resized.")
    @PutMapping("/{projectId}/estimates/{estimateId}/activities/{activityId}")
    public ActivityView updateActivity(@PathVariable UUID projectId,
                                       @PathVariable UUID estimateId,
                                       @PathVariable UUID activityId,
                                       @Valid @RequestBody ActivityRequest request) {
        return planning.updateActivity(projectId, estimateId, activityId, request);
    }

    @Operation(summary = "Update activity quantity, productivity, and automatic duration")
    @PutMapping("/{projectId}/estimates/{estimateId}/activities/{activityId}/planning")
    public ActivityView updatePlanning(@PathVariable UUID projectId,
                                       @PathVariable UUID estimateId,
                                       @PathVariable UUID activityId,
                                       @Valid @RequestBody ActivityPlanningRequest request) {
        return planning.updateActivityPlanning(projectId, estimateId, activityId, request);
    }

    @Operation(summary = "Add an activity dependency and reschedule the successor")
    @PostMapping("/{projectId}/estimates/{estimateId}/activities/{activityId}/dependencies")
    @ResponseStatus(HttpStatus.CREATED)
    public DependencyView addDependency(@PathVariable UUID projectId,
                                        @PathVariable UUID estimateId,
                                        @PathVariable UUID activityId,
                                        @Valid @RequestBody DependencyRequest request) {
        return planning.addDependency(projectId, estimateId, activityId, request);
    }

    @DeleteMapping("/{projectId}/estimates/{estimateId}/activities/{activityId}/dependencies/{dependencyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDependency(@PathVariable UUID projectId,
                                 @PathVariable UUID estimateId,
                                 @PathVariable UUID activityId,
                                 @PathVariable UUID dependencyId) {
        planning.deleteDependency(projectId, estimateId, activityId, dependencyId);
    }

    @GetMapping("/{projectId}/calendar")
    public CalendarView calendar(@PathVariable UUID projectId) {
        return planning.getCalendar(projectId);
    }

    @PutMapping("/{projectId}/calendar")
    public CalendarView updateCalendar(@PathVariable UUID projectId,
                                       @Valid @RequestBody CalendarRequest request) {
        return planning.updateCalendar(projectId, request);
    }
}
