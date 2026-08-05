package com.project.costestimator.controller;

import com.project.costestimator.domain.CostBreakdown;
import com.project.costestimator.dto.ApiModels.*;
import com.project.costestimator.service.CostCalculator;
import com.project.costestimator.service.ProjectService;
import com.project.costestimator.service.PricingService;
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
    private final PricingService pricing;

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
    @Operation(summary = "Update activity quantity, productivity and automatic duration")
    @PutMapping("/{projectId}/estimates/{estimateId}/activities/{activityId}/planning") public ActivityView updatePlanning(@PathVariable UUID projectId, @PathVariable UUID estimateId, @PathVariable UUID activityId, @Valid @RequestBody ActivityPlanningRequest request) { return projects.updateActivityPlanning(projectId, estimateId, activityId, request); }
    @Operation(summary = "Add an activity dependency and reschedule the successor")
    @PostMapping("/{projectId}/estimates/{estimateId}/activities/{activityId}/dependencies") @ResponseStatus(HttpStatus.CREATED) public DependencyView dependency(@PathVariable UUID projectId, @PathVariable UUID estimateId, @PathVariable UUID activityId, @Valid @RequestBody DependencyRequest request) { return projects.addDependency(projectId, estimateId, activityId, request); }
    @DeleteMapping("/{projectId}/estimates/{estimateId}/activities/{activityId}/dependencies/{dependencyId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteDependency(@PathVariable UUID projectId, @PathVariable UUID estimateId, @PathVariable UUID activityId, @PathVariable UUID dependencyId) { projects.deleteDependency(projectId, estimateId, activityId, dependencyId); }
    @Operation(summary = "Assign a resource to an activity", description = "Creates an equipment, personnel, or material assignment based on the resource referenced by resourceId.")
    @PostMapping("/{projectId}/estimates/{estimateId}/activities/{activityId}/assignments") @ResponseStatus(HttpStatus.CREATED) public AssignmentView assignment(@PathVariable UUID projectId, @PathVariable UUID estimateId, @PathVariable UUID activityId, @Valid @RequestBody AssignmentRequest request) { return projects.assignResource(projectId, estimateId, activityId, request); }
    @Operation(summary = "Update an activity resource assignment")
    @PutMapping("/{projectId}/estimates/{estimateId}/activities/{activityId}/assignments/{assignmentId}") public AssignmentView updateAssignment(@PathVariable UUID projectId, @PathVariable UUID estimateId, @PathVariable UUID activityId, @PathVariable UUID assignmentId, @Valid @RequestBody AssignmentRequest request) { return projects.updateAssignment(projectId, estimateId, activityId, assignmentId, request); }
    @Operation(summary = "Remove a resource assignment from an activity")
    @DeleteMapping("/{projectId}/estimates/{estimateId}/activities/{activityId}/assignments/{assignmentId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void unassign(@PathVariable UUID projectId, @PathVariable UUID estimateId, @PathVariable UUID activityId, @PathVariable UUID assignmentId) { projects.unassignResource(projectId, estimateId, activityId, assignmentId); }
    @Operation(summary = "Add a crew member to an equipment assignment")
    @PostMapping("/{projectId}/estimates/{estimateId}/equipment-assignments/{assignmentId}/crew") @ResponseStatus(HttpStatus.CREATED) public CrewView crew(@PathVariable UUID projectId, @PathVariable UUID estimateId, @PathVariable UUID assignmentId, @Valid @RequestBody CrewRequest request) { return projects.addCrew(projectId, estimateId, assignmentId, request); }
    @Operation(summary = "Add indirect staff to a project", description = "Use this operation for roles such as project manager, site manager, and technical office engineer.")
    @PostMapping("/{projectId}/estimates/{estimateId}/staff") @ResponseStatus(HttpStatus.CREATED) public StaffView staff(@PathVariable UUID projectId, @PathVariable UUID estimateId, @Valid @RequestBody StaffRequest request) { return projects.addStaff(projectId, estimateId, request); }
    @Operation(summary = "Copy resource catalog rates into an estimate", description = "Adds missing project-currency rate snapshots. Set replaceExisting=true to refresh existing snapshots from the catalog.")
    @PostMapping("/{projectId}/estimates/{estimateId}/resource-rates/{resourceId}/sync") public List<EstimateRateView> syncRates(@PathVariable UUID projectId, @PathVariable UUID estimateId, @PathVariable UUID resourceId, @RequestParam(defaultValue = "false") boolean replaceExisting) { return projects.syncResourceRates(projectId, estimateId, resourceId, replaceExisting); }
    @Operation(summary = "List project-specific resource rates for an estimate")
    @GetMapping("/{projectId}/estimates/{estimateId}/resource-rates") public List<EstimateRateView> resourceRates(@PathVariable UUID projectId, @PathVariable UUID estimateId) { return projects.listResourceRates(projectId, estimateId); }
    @Operation(summary = "Override a resource rate for an estimate")
    @PutMapping("/{projectId}/estimates/{estimateId}/resource-rates/{sourceCostComponentId}") public EstimateRateView updateRate(@PathVariable UUID projectId, @PathVariable UUID estimateId, @PathVariable UUID sourceCostComponentId, @Valid @RequestBody EstimateRateRequest request) { return projects.updateResourceRate(projectId, estimateId, sourceCostComponentId, request); }
    @Operation(summary = "Calculate the cost of an estimate version")
    @GetMapping("/{projectId}/estimates/{estimateId}/cost") public CostBreakdown cost(@PathVariable UUID projectId, @PathVariable UUID estimateId) { return calculator.calculateProjectCost(projects.requireEstimate(projectId, estimateId)); }
    @Operation(summary = "Get the detailed estimate cost report", description = "Returns authoritative project-level, WBS, and activity cost breakdowns from the backend calculation engine.")
    @GetMapping("/{projectId}/estimates/{estimateId}/cost-report") public EstimateCostReport costReport(@PathVariable UUID projectId, @PathVariable UUID estimateId) { return calculator.calculateEstimateCostReport(projects.requireEstimate(projectId, estimateId)); }
    @Operation(summary = "Create a BOQ item linked to WBS and optionally an activity")
    @PostMapping("/{projectId}/estimates/{estimateId}/boq-items") @ResponseStatus(HttpStatus.CREATED) public BoqView addBoq(@PathVariable UUID projectId, @PathVariable UUID estimateId, @Valid @RequestBody BoqRequest request) { return projects.addBoqItem(projectId, estimateId, request); }
    @GetMapping("/{projectId}/estimates/{estimateId}/boq-items") public List<BoqView> boq(@PathVariable UUID projectId, @PathVariable UUID estimateId) { return projects.listBoqItems(projectId, estimateId); }
    @PutMapping("/{projectId}/estimates/{estimateId}/boq-items/{boqId}") public BoqView updateBoq(@PathVariable UUID projectId, @PathVariable UUID estimateId, @PathVariable UUID boqId, @Valid @RequestBody BoqRequest request) { return projects.updateBoqItem(projectId, estimateId, boqId, request); }
    @DeleteMapping("/{projectId}/estimates/{estimateId}/boq-items/{boqId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteBoq(@PathVariable UUID projectId, @PathVariable UUID estimateId, @PathVariable UUID boqId) { projects.deleteBoqItem(projectId, estimateId, boqId); }
    @GetMapping("/{projectId}/estimates/{estimateId}/boq-traceability") public BoqTraceabilityReport boqTraceability(@PathVariable UUID projectId, @PathVariable UUID estimateId) { return projects.boqTraceability(projectId, estimateId); }
    @GetMapping("/{projectId}/calendar") public CalendarView calendar(@PathVariable UUID projectId) { return projects.getCalendar(projectId); }
    @PutMapping("/{projectId}/calendar") public CalendarView updateCalendar(@PathVariable UUID projectId, @Valid @RequestBody CalendarRequest request) { return projects.updateCalendar(projectId, request); }
    @GetMapping("/{projectId}/estimates/{estimateId}/pricing-rules") public List<PricingRuleView> pricingRules(@PathVariable UUID projectId, @PathVariable UUID estimateId) { return projects.listPricingRules(projectId, estimateId); }
    @PostMapping("/{projectId}/estimates/{estimateId}/pricing-rules") @ResponseStatus(HttpStatus.CREATED) public PricingRuleView addPricingRule(@PathVariable UUID projectId, @PathVariable UUID estimateId, @Valid @RequestBody PricingRuleRequest request) { return projects.addPricingRule(projectId, estimateId, request); }
    @PutMapping("/{projectId}/estimates/{estimateId}/pricing-rules/{ruleId}") public PricingRuleView updatePricingRule(@PathVariable UUID projectId, @PathVariable UUID estimateId, @PathVariable UUID ruleId, @Valid @RequestBody PricingRuleRequest request) { return projects.updatePricingRule(projectId, estimateId, ruleId, request); }
    @DeleteMapping("/{projectId}/estimates/{estimateId}/pricing-rules/{ruleId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deletePricingRule(@PathVariable UUID projectId, @PathVariable UUID estimateId, @PathVariable UUID ruleId) { projects.deletePricingRule(projectId, estimateId, ruleId); }
    @GetMapping("/{projectId}/estimates/{estimateId}/pricing-summary") public PricingSummaryView pricingSummary(@PathVariable UUID projectId, @PathVariable UUID estimateId) { return pricing.summary(projectId, estimateId); }
}
