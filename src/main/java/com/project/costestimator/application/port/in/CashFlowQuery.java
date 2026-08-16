package com.project.costestimator.application.port.in;

import com.project.costestimator.dto.ApiModels.CashFlowReport;

import java.util.UUID;

public interface CashFlowQuery {
    CashFlowReport cashFlow(UUID projectId, UUID estimateId);
}
