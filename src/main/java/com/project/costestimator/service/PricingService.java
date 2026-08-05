package com.project.costestimator.service;

import com.project.costestimator.domain.PricingRule;
import com.project.costestimator.domain.enums.PricingRuleType;
import com.project.costestimator.dto.ApiModels.PricingLineView;
import com.project.costestimator.dto.ApiModels.PricingSummaryView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class PricingService {
    private final ProjectService projects;
    private final CostCalculator calculator;

    public PricingSummaryView summary(UUID projectId, UUID estimateId) {
        var estimate = projects.requireEstimate(projectId, estimateId); BigDecimal estimatedCost = calculator.calculateProjectCost(estimate).getTotalCost();
        BigDecimal boqValue = projects.boqTraceability(projectId, estimateId).totalBoqValue(); BigDecimal runningTotal = estimatedCost;
        BigDecimal nonProfit = BigDecimal.ZERO; BigDecimal profit = BigDecimal.ZERO; var lines = new ArrayList<PricingLineView>();
        for (PricingRule rule : estimate.getPricingRules().stream().filter(PricingRule::isActive).sorted(Comparator.comparingInt(PricingRule::getSequence)).toList()) {
            BigDecimal base = rule.getBase() == com.project.costestimator.domain.enums.PricingBase.RUNNING_TOTAL ? runningTotal : estimatedCost;
            BigDecimal amount = base.multiply(rule.getPercentage()).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            runningTotal = runningTotal.add(amount); if (rule.getType() == PricingRuleType.PROFIT) profit = profit.add(amount); else nonProfit = nonProfit.add(amount);
            lines.add(new PricingLineView(rule.getId(), rule.getType(), rule.getName(), rule.getPercentage(), rule.getBase(), rule.getSequence(), base, amount));
        }
        BigDecimal salesPrice = runningTotal; BigDecimal grossProfit = salesPrice.subtract(estimatedCost); BigDecimal margin = salesPrice.signum() == 0 ? BigDecimal.ZERO : profit.multiply(BigDecimal.valueOf(100)).divide(salesPrice, 4, RoundingMode.HALF_UP);
        return new PricingSummaryView(estimatedCost, boqValue, nonProfit, profit, salesPrice, grossProfit, profit, margin, boqValue.subtract(salesPrice), lines);
    }
}
