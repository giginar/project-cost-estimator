package com.project.costestimator.application.port.in;

import com.project.costestimator.dto.ApiModels.CostCodeRequest;
import com.project.costestimator.dto.ApiModels.CostCodeView;
import com.project.costestimator.dto.ApiModels.GeneralUnitPriceRequest;
import com.project.costestimator.dto.ApiModels.GeneralUnitPriceView;

import java.util.List;
import java.util.UUID;

public interface ProjectConfigurationUseCase {
    List<GeneralUnitPriceView> listGeneralUnitPrices(UUID projectId);
    GeneralUnitPriceView addGeneralUnitPrice(UUID projectId, GeneralUnitPriceRequest request);
    GeneralUnitPriceView updateGeneralUnitPrice(UUID projectId, UUID priceId, GeneralUnitPriceRequest request);
    void deleteGeneralUnitPrice(UUID projectId, UUID priceId);

    List<CostCodeView> listCostCodes(UUID projectId);
    CostCodeView addCostCode(UUID projectId, CostCodeRequest request);
    CostCodeView updateCostCode(UUID projectId, UUID costCodeId, CostCodeRequest request);
    void deleteCostCode(UUID projectId, UUID costCodeId);
}
