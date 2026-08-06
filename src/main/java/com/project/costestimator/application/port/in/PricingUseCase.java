package com.project.costestimator.application.port.in;

import com.project.costestimator.dto.ApiModels.PricingRuleRequest;
import com.project.costestimator.dto.ApiModels.PricingRuleView;
import com.project.costestimator.dto.ApiModels.PricingSummaryView;

import java.util.List;
import java.util.UUID;

public interface PricingUseCase {
    List<PricingRuleView> listPricingRules(UUID projectId, UUID estimateId);
    PricingRuleView addPricingRule(UUID projectId, UUID estimateId, PricingRuleRequest request);
    PricingRuleView updatePricingRule(UUID projectId, UUID estimateId, UUID ruleId, PricingRuleRequest request);
    void deletePricingRule(UUID projectId, UUID estimateId, UUID ruleId);
    PricingSummaryView pricingSummary(UUID projectId, UUID estimateId);
}
