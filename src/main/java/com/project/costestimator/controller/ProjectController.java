package com.project.costestimator.controller;

import com.project.costestimator.application.port.in.ProjectUseCase;
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
@Tag(name = "Projects", description = "Project, estimate version, and WBS operations")
public class ProjectController {
    private final ProjectUseCase projects;

    public ProjectController(ProjectUseCase projects) {
        this.projects = projects;
    }

    @Operation(summary = "Create a project")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectDetail create(@Valid @RequestBody ProjectRequest request) {
        return projects.create(request);
    }

    @Operation(summary = "List projects")
    @GetMapping
    public List<ProjectSummary> list() {
        return projects.list();
    }

    @Operation(summary = "Get project details",
            description = "Returns the project with its estimate versions, WBS items, activities, and project staff.")
    @GetMapping("/{projectId}")
    public ProjectDetail get(@PathVariable UUID projectId) {
        return projects.get(projectId);
    }

    @Operation(summary = "Update a project")
    @PutMapping("/{projectId}")
    public ProjectDetail update(@PathVariable UUID projectId,
                                @Valid @RequestBody ProjectRequest request) {
        return projects.update(projectId, request);
    }

    @Operation(summary = "Delete a project")
    @DeleteMapping("/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID projectId) {
        projects.delete(projectId);
    }

    @Operation(summary = "Add an estimate version to a project")
    @PostMapping("/{projectId}/estimates")
    @ResponseStatus(HttpStatus.CREATED)
    public EstimateView addEstimate(@PathVariable UUID projectId,
                                    @Valid @RequestBody EstimateRequest request) {
        return projects.addEstimate(projectId, request);
    }

    @Operation(summary = "Add a WBS item to an estimate version")
    @PostMapping("/{projectId}/estimates/{estimateId}/wbs-items")
    @ResponseStatus(HttpStatus.CREATED)
    public WbsView addWbs(@PathVariable UUID projectId,
                          @PathVariable UUID estimateId,
                          @Valid @RequestBody WbsRequest request) {
        return projects.addWbs(projectId, estimateId, request);
    }
}
