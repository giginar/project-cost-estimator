package com.project.costestimator.application.service;

import com.project.costestimator.application.port.in.CostReportQuery;
import com.project.costestimator.application.service.support.ProjectFinder;
import com.project.costestimator.domain.CostBreakdown;
import com.project.costestimator.dto.ApiModels.EstimateCostReport;
import com.project.costestimator.domain.service.CostCalculator;

import java.util.UUID;

public final class CostReportApplicationService implements CostReportQuery {
    private final ProjectFinder projects;
    private final CostCalculator calculator;

    public CostReportApplicationService(ProjectFinder projects, CostCalculator calculator) {
        this.projects = projects;
        this.calculator = calculator;
    }

    @Override
    public CostBreakdown projectCost(UUID projectId, UUID estimateId) {
        return calculator.calculateProjectCost(projects.requireEstimate(projectId, estimateId));
    }

    @Override
    public EstimateCostReport costReport(UUID projectId, UUID estimateId) {
        return calculator.calculateEstimateCostReport(projects.requireEstimate(projectId, estimateId));
    }
}
