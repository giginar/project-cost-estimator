package com.project.costestimator.controller;

import com.project.costestimator.application.port.in.AssignmentUseCase;
import com.project.costestimator.application.port.in.ResourceRateUseCase;
import com.project.costestimator.dto.ApiModels.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
@Tag(name = "Assignments", description = "Activity resources, equipment crews, project staff, and estimate rates")
public class AssignmentController {
    private final AssignmentUseCase assignments;
    private final ResourceRateUseCase rates;

    public AssignmentController(AssignmentUseCase assignments, ResourceRateUseCase rates) {
        this.assignments = assignments;
        this.rates = rates;
    }

    @Operation(summary = "Assign a catalog resource to an activity")
    @PostMapping("/{projectId}/estimates/{estimateId}/activities/{activityId}/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public AssignmentView assign(@PathVariable UUID projectId,
                                 @PathVariable UUID estimateId,
                                 @PathVariable UUID activityId,
                                 @Valid @RequestBody AssignmentRequest request) {
        return assignments.assignResource(projectId, estimateId, activityId, request);
    }

    @Operation(summary = "Update an activity resource assignment")
    @PutMapping("/{projectId}/estimates/{estimateId}/activities/{activityId}/assignments/{assignmentId}")
    public AssignmentView updateAssignment(@PathVariable UUID projectId,
                                           @PathVariable UUID estimateId,
                                           @PathVariable UUID activityId,
                                           @PathVariable UUID assignmentId,
                                           @Valid @RequestBody AssignmentRequest request) {
        return assignments.updateAssignment(projectId, estimateId, activityId, assignmentId, request);
    }

    @Operation(summary = "Remove a resource assignment from an activity")
    @DeleteMapping("/{projectId}/estimates/{estimateId}/activities/{activityId}/assignments/{assignmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unassign(@PathVariable UUID projectId,
                         @PathVariable UUID estimateId,
                         @PathVariable UUID activityId,
                         @PathVariable UUID assignmentId) {
        assignments.unassignResource(projectId, estimateId, activityId, assignmentId);
    }

    @Operation(summary = "Add a personnel resource to an equipment crew")
    @PostMapping("/{projectId}/estimates/{estimateId}/equipment-assignments/{assignmentId}/crew")
    @ResponseStatus(HttpStatus.CREATED)
    public CrewView addCrew(@PathVariable UUID projectId,
                            @PathVariable UUID estimateId,
                            @PathVariable UUID assignmentId,
                            @Valid @RequestBody CrewRequest request) {
        return assignments.addCrew(projectId, estimateId, assignmentId, request);
    }

    @Operation(summary = "Add indirect personnel at estimate level")
    @PostMapping("/{projectId}/estimates/{estimateId}/staff")
    @ResponseStatus(HttpStatus.CREATED)
    public StaffView addStaff(@PathVariable UUID projectId,
                              @PathVariable UUID estimateId,
                              @Valid @RequestBody StaffRequest request) {
        return assignments.addStaff(projectId, estimateId, request);
    }

    @Operation(summary = "Copy or refresh catalog prices into an estimate snapshot")
    @PostMapping("/{projectId}/estimates/{estimateId}/resource-rates/{resourceId}/sync")
    public List<EstimateRateView> syncRates(@PathVariable UUID projectId,
                                            @PathVariable UUID estimateId,
                                            @PathVariable UUID resourceId,
                                            @RequestParam(defaultValue = "false") boolean replaceExisting) {
        return rates.syncResourceRates(projectId, estimateId, resourceId, replaceExisting);
    }

    @GetMapping("/{projectId}/estimates/{estimateId}/resource-rates")
    public List<EstimateRateView> resourceRates(@PathVariable UUID projectId,
                                                @PathVariable UUID estimateId) {
        return rates.listResourceRates(projectId, estimateId);
    }

    @PutMapping("/{projectId}/estimates/{estimateId}/resource-rates/{sourceCostComponentId}")
    public EstimateRateView updateRate(@PathVariable UUID projectId,
                                       @PathVariable UUID estimateId,
                                       @PathVariable UUID sourceCostComponentId,
                                       @Valid @RequestBody EstimateRateRequest request) {
        return rates.updateResourceRate(projectId, estimateId, sourceCostComponentId, request);
    }
}
