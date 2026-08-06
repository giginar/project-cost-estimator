package com.project.costestimator.controller;

import com.project.costestimator.application.port.in.PricingUseCase;
import com.project.costestimator.dto.ApiModels.PricingRuleRequest;
import com.project.costestimator.dto.ApiModels.PricingRuleView;
import com.project.costestimator.dto.ApiModels.PricingSummaryView;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
@Tag(name = "Pricing", description = "Ordered markup rules and sales-price summaries")
public class PricingController {
    private final PricingUseCase pricing;

    public PricingController(PricingUseCase pricing) {
        this.pricing = pricing;
    }

    @GetMapping("/{projectId}/estimates/{estimateId}/pricing-rules")
    public List<PricingRuleView> rules(@PathVariable UUID projectId, @PathVariable UUID estimateId) {
        return pricing.listPricingRules(projectId, estimateId);
    }

    @PostMapping("/{projectId}/estimates/{estimateId}/pricing-rules")
    @ResponseStatus(HttpStatus.CREATED)
    public PricingRuleView addRule(@PathVariable UUID projectId,
                                   @PathVariable UUID estimateId,
                                   @Valid @RequestBody PricingRuleRequest request) {
        return pricing.addPricingRule(projectId, estimateId, request);
    }

    @PutMapping("/{projectId}/estimates/{estimateId}/pricing-rules/{ruleId}")
    public PricingRuleView updateRule(@PathVariable UUID projectId,
                                      @PathVariable UUID estimateId,
                                      @PathVariable UUID ruleId,
                                      @Valid @RequestBody PricingRuleRequest request) {
        return pricing.updatePricingRule(projectId, estimateId, ruleId, request);
    }

    @DeleteMapping("/{projectId}/estimates/{estimateId}/pricing-rules/{ruleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRule(@PathVariable UUID projectId,
                           @PathVariable UUID estimateId,
                           @PathVariable UUID ruleId) {
        pricing.deletePricingRule(projectId, estimateId, ruleId);
    }

    @GetMapping("/{projectId}/estimates/{estimateId}/pricing-summary")
    public PricingSummaryView summary(@PathVariable UUID projectId, @PathVariable UUID estimateId) {
        return pricing.pricingSummary(projectId, estimateId);
    }
}
