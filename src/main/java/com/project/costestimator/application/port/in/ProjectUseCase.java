package com.project.costestimator.application.port.in;

import com.project.costestimator.dto.ApiModels.EstimateRequest;
import com.project.costestimator.dto.ApiModels.EstimateView;
import com.project.costestimator.dto.ApiModels.ProjectDetail;
import com.project.costestimator.dto.ApiModels.ProjectRequest;
import com.project.costestimator.dto.ApiModels.ProjectSummary;
import com.project.costestimator.dto.ApiModels.WbsRequest;
import com.project.costestimator.dto.ApiModels.WbsView;

import java.util.List;
import java.util.UUID;

public interface ProjectUseCase {
    ProjectDetail create(ProjectRequest request);
    List<ProjectSummary> list();
    ProjectDetail get(UUID projectId);
    ProjectDetail update(UUID projectId, ProjectRequest request);
    void delete(UUID projectId);
    EstimateView addEstimate(UUID projectId, EstimateRequest request);
    WbsView addWbs(UUID projectId, UUID estimateId, WbsRequest request);
}
