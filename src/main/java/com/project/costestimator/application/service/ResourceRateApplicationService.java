package com.project.costestimator.application.service;

import com.project.costestimator.application.port.in.ResourceRateUseCase;
import com.project.costestimator.application.port.out.ProjectRepositoryPort;
import com.project.costestimator.application.service.support.ProjectFinder;
import com.project.costestimator.application.service.support.ProjectViewMapper;
import com.project.costestimator.application.service.support.ResourceFinder;
import com.project.costestimator.application.service.support.ResourceRateSynchronizer;
import com.project.costestimator.domain.EstimateResourceRate;
import com.project.costestimator.domain.EstimateVersion;
import com.project.costestimator.domain.Resource;
import com.project.costestimator.dto.ApiModels.EstimateRateRequest;
import com.project.costestimator.dto.ApiModels.EstimateRateView;
import com.project.costestimator.exception.NotFoundException;

import java.util.List;
import java.util.UUID;

public final class ResourceRateApplicationService implements ResourceRateUseCase {
    private final ProjectRepositoryPort projects;
    private final ProjectFinder projectFinder;
    private final ResourceFinder resourceFinder;
    private final ResourceRateSynchronizer synchronizer;
    private final ProjectViewMapper views;

    public ResourceRateApplicationService(ProjectRepositoryPort projects,
                                          ProjectFinder projectFinder,
                                          ResourceFinder resourceFinder,
                                          ResourceRateSynchronizer synchronizer,
                                          ProjectViewMapper views) {
        this.projects = projects;
        this.projectFinder = projectFinder;
        this.resourceFinder = resourceFinder;
        this.synchronizer = synchronizer;
        this.views = views;
    }

    @Override
    public List<EstimateRateView> syncResourceRates(UUID projectId, UUID estimateId, UUID resourceId,
                                                    boolean replaceExisting) {
        EstimateVersion estimate = projectFinder.requireEstimate(projectId, estimateId);
        Resource resource = resourceFinder.requireAvailable(resourceId, projectId, Resource.class);
        synchronizer.synchronize(estimate, resource, replaceExisting);
        projects.save(estimate.getProject());
        return estimate.getResourceRates().stream()
                .filter(rate -> rate.getResourceId().equals(resourceId))
                .map(views::toEstimateRateView)
                .toList();
    }

    @Override
    public List<EstimateRateView> listResourceRates(UUID projectId, UUID estimateId) {
        return projectFinder.requireEstimate(projectId, estimateId).getResourceRates().stream()
                .map(views::toEstimateRateView)
                .toList();
    }

    @Override
    public EstimateRateView updateResourceRate(UUID projectId, UUID estimateId,
                                               UUID sourceCostComponentId, EstimateRateRequest request) {
        EstimateVersion estimate = projectFinder.requireEstimate(projectId, estimateId);
        EstimateResourceRate rate = estimate.getResourceRates().stream()
                .filter(candidate -> candidate.getSourceCostComponentId().equals(sourceCostComponentId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "Estimate resource rate not found: " + sourceCostComponentId));
        rate.setUnitPrice(request.unitPrice());
        projects.save(estimate.getProject());
        return views.toEstimateRateView(rate);
    }
}
