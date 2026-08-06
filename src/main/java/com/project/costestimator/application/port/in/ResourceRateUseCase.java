package com.project.costestimator.application.port.in;

import com.project.costestimator.dto.ApiModels.EstimateRateRequest;
import com.project.costestimator.dto.ApiModels.EstimateRateView;

import java.util.List;
import java.util.UUID;

public interface ResourceRateUseCase {
    List<EstimateRateView> syncResourceRates(UUID projectId, UUID estimateId, UUID resourceId,
                                             boolean replaceExisting);
    List<EstimateRateView> listResourceRates(UUID projectId, UUID estimateId);
    EstimateRateView updateResourceRate(UUID projectId, UUID estimateId, UUID sourceCostComponentId,
                                        EstimateRateRequest request);
}
