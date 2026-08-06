package com.project.costestimator.application.port.in;

import com.project.costestimator.domain.CostBreakdown;
import com.project.costestimator.dto.ApiModels.EstimateCostReport;

import java.util.UUID;

public interface CostReportQuery {
    CostBreakdown projectCost(UUID projectId, UUID estimateId);
    EstimateCostReport costReport(UUID projectId, UUID estimateId);
}
