package com.project.costestimator.application.service.support;

import com.project.costestimator.application.port.out.ProjectRepositoryPort;
import com.project.costestimator.domain.*;
import com.project.costestimator.exception.NotFoundException;

import java.util.UUID;

public final class ProjectFinder {
    private final ProjectRepositoryPort projects;

    public ProjectFinder(ProjectRepositoryPort projects) {
        this.projects = projects;
    }

    public Project requireProject(UUID projectId) {
        return projects.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));
    }

    public EstimateVersion requireEstimate(UUID projectId, UUID estimateId) {
        return requireProject(projectId).getEstimateVersions().stream()
                .filter(estimate -> estimate.getId().equals(estimateId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Estimate not found: " + estimateId));
    }

    public WbsItem requireWbs(EstimateVersion estimate, UUID wbsId) {
        return estimate.getWbsItems().stream()
                .filter(wbs -> wbs.getId().equals(wbsId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("WBS item not found: " + wbsId));
    }

    public Activity requireActivity(EstimateVersion estimate, UUID activityId) {
        return estimate.getWbsItems().stream()
                .flatMap(wbs -> wbs.getActivities().stream())
                .filter(activity -> activity.getId().equals(activityId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Activity not found: " + activityId));
    }

    public ActivityEquipmentAssignment requireEquipmentAssignment(EstimateVersion estimate, UUID assignmentId) {
        return estimate.getWbsItems().stream()
                .flatMap(wbs -> wbs.getActivities().stream())
                .flatMap(activity -> activity.getResourceAssignments().stream())
                .filter(assignment -> assignment.getId().equals(assignmentId))
                .filter(ActivityEquipmentAssignment.class::isInstance)
                .map(ActivityEquipmentAssignment.class::cast)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Equipment assignment not found: " + assignmentId));
    }

    public BoqItem requireBoqItem(EstimateVersion estimate, UUID boqId) {
        return estimate.getBoqItems().stream()
                .filter(item -> item.getId().equals(boqId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("BOQ item not found: " + boqId));
    }

    public PricingRule requirePricingRule(EstimateVersion estimate, UUID ruleId) {
        return estimate.getPricingRules().stream()
                .filter(rule -> rule.getId().equals(ruleId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Pricing rule not found: " + ruleId));
    }
}
