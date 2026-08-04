package com.project.costestimator.controller;

import com.project.costestimator.domain.CostBreakdown;
import com.project.costestimator.dto.ApiModels.*;
import com.project.costestimator.service.CostCalculator;
import com.project.costestimator.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "Projects", description = "Project, estimate version, WBS, activity, and resource assignment operations")
public class ProjectController {
    private final ProjectService projects;
    private final CostCalculator calculator;

    @Operation(summary = "Create a project")
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public ProjectDetail create(@Valid @RequestBody ProjectRequest request) { return projects.create(request); }
    @Operation(summary = "List projects")
    @GetMapping public List<ProjectSummary> list() { return projects.list(); }
    @Operation(summary = "Get project details", description = "Returns the project with its estimate versions, WBS items, activities, and project staff.")
    @GetMapping("/{projectId}") public ProjectDetail get(@PathVariable UUID projectId) { return projects.get(projectId); }
    @Operation(summary = "Update a project")
    @PutMapping("/{projectId}") public ProjectDetail update(@PathVariable UUID projectId, @Valid @RequestBody ProjectRequest request) { return projects.update(projectId, request); }
    @Operation(summary = "Delete a project")
    @DeleteMapping("/{projectId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID projectId) { projects.delete(projectId); }
    @Operation(summary = "Add an estimate version to a project")
    @PostMapping("/{projectId}/estimates") @ResponseStatus(HttpStatus.CREATED) public EstimateView estimate(@PathVariable UUID projectId, @Valid @RequestBody EstimateRequest request) { return projects.addEstimate(projectId, request); }
    @Operation(summary = "Add a WBS item to an estimate version")
    @PostMapping("/{projectId}/estimates/{estimateId}/wbs-items") @ResponseStatus(HttpStatus.CREATED) public WbsView wbs(@PathVariable UUID projectId, @PathVariable UUID estimateId, @Valid @RequestBody WbsRequest request) { return projects.addWbs(projectId, estimateId, request); }
    @Operation(summary = "Add an activity to a WBS item")
    @PostMapping("/{projectId}/estimates/{estimateId}/wbs-items/{wbsId}/activities") @ResponseStatus(HttpStatus.CREATED) public ActivityView activity(@PathVariable UUID projectId, @PathVariable UUID estimateId, @PathVariable UUID wbsId, @Valid @RequestBody ActivityRequest request) { return projects.addActivity(projectId, estimateId, wbsId, request); }
    @Operation(summary = "Update an activity", description = "Updates activity details and its planned date range. Used by the Gantt timeline after a bar is resized.")
    @PutMapping("/{projectId}/estimates/{estimateId}/activities/{activityId}") public ActivityView updateActivity(@PathVariable UUID projectId, @PathVariable UUID estimateId, @PathVariable UUID activityId, @Valid @RequestBody ActivityRequest request) { return projects.updateActivity(projectId, estimateId, activityId, request); }
    @Operation(summary = "Assign a resource to an activity", description = "Creates an equipment, personnel, or material assignment based on the resource referenced by resourceId.")
    @PostMapping("/{projectId}/estimates/{estimateId}/activities/{activityId}/assignments") @ResponseStatus(HttpStatus.CREATED) public AssignmentView assignment(@PathVariable UUID projectId, @PathVariable UUID estimateId, @PathVariable UUID activityId, @Valid @RequestBody AssignmentRequest request) { return projects.assignResource(projectId, estimateId, activityId, request); }
    @Operation(summary = "Remove a resource assignment from an activity")
    @DeleteMapping("/{projectId}/estimates/{estimateId}/activities/{activityId}/assignments/{assignmentId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void unassign(@PathVariable UUID projectId, @PathVariable UUID estimateId, @PathVariable UUID activityId, @PathVariable UUID assignmentId) { projects.unassignResource(projectId, estimateId, activityId, assignmentId); }
    @Operation(summary = "Add a crew member to an equipment assignment")
    @PostMapping("/{projectId}/estimates/{estimateId}/equipment-assignments/{assignmentId}/crew") @ResponseStatus(HttpStatus.CREATED) public CrewView crew(@PathVariable UUID projectId, @PathVariable UUID estimateId, @PathVariable UUID assignmentId, @Valid @RequestBody CrewRequest request) { return projects.addCrew(projectId, estimateId, assignmentId, request); }
    @Operation(summary = "Add indirect staff to a project", description = "Use this operation for roles such as project manager, site manager, and technical office engineer.")
    @PostMapping("/{projectId}/estimates/{estimateId}/staff") @ResponseStatus(HttpStatus.CREATED) public StaffView staff(@PathVariable UUID projectId, @PathVariable UUID estimateId, @Valid @RequestBody StaffRequest request) { return projects.addStaff(projectId, estimateId, request); }
    @Operation(summary = "Calculate the cost of an estimate version")
    @GetMapping("/{projectId}/estimates/{estimateId}/cost") public CostBreakdown cost(@PathVariable UUID projectId, @PathVariable UUID estimateId) { return calculator.calculateProjectCost(projects.requireEstimate(projectId, estimateId)); }
}
