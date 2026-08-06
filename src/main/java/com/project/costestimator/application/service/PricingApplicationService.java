package com.project.costestimator.application.service;

import com.project.costestimator.application.port.in.PricingUseCase;
import com.project.costestimator.application.port.out.ProjectRepositoryPort;
import com.project.costestimator.application.service.support.ProjectFinder;
import com.project.costestimator.application.service.support.ProjectViewMapper;
import com.project.costestimator.domain.BoqItem;
import com.project.costestimator.domain.EstimateVersion;
import com.project.costestimator.domain.PricingRule;
import com.project.costestimator.domain.enums.PricingBase;
import com.project.costestimator.domain.enums.PricingRuleType;
import com.project.costestimator.domain.service.CurrencyConverter;
import com.project.costestimator.domain.service.CostCalculator;
import com.project.costestimator.dto.ApiModels.*;
import com.project.costestimator.exception.NotFoundException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class PricingApplicationService implements PricingUseCase {
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final ProjectRepositoryPort projects;
    private final ProjectFinder finder;
    private final ProjectViewMapper views;
    private final CostCalculator calculator;
    private final CurrencyConverter currencies;

    public PricingApplicationService(ProjectRepositoryPort projects,
                                     ProjectFinder finder,
                                     ProjectViewMapper views,
                                     CostCalculator calculator,
                                     CurrencyConverter currencies) {
        this.projects = projects;
        this.finder = finder;
        this.views = views;
        this.calculator = calculator;
        this.currencies = currencies;
    }

    @Override
    public List<PricingRuleView> listPricingRules(UUID projectId, UUID estimateId) {
        return finder.requireEstimate(projectId, estimateId).getPricingRules().stream()
                .sorted(Comparator.comparingInt(PricingRule::getSequence))
                .map(views::toPricingRuleView)
                .toList();
    }

    @Override
    public PricingRuleView addPricingRule(UUID projectId, UUID estimateId, PricingRuleRequest request) {
        EstimateVersion estimate = finder.requireEstimate(projectId, estimateId);
        PricingRule rule = new PricingRule();
        rule.setId(UUID.randomUUID());
        rule.setEstimateVersion(estimate);
        apply(rule, request);
        estimate.getPricingRules().add(rule);
        projects.save(estimate.getProject());
        return views.toPricingRuleView(rule);
    }

    @Override
    public PricingRuleView updatePricingRule(UUID projectId, UUID estimateId, UUID ruleId,
                                             PricingRuleRequest request) {
        EstimateVersion estimate = finder.requireEstimate(projectId, estimateId);
        PricingRule rule = finder.requirePricingRule(estimate, ruleId);
        apply(rule, request);
        projects.save(estimate.getProject());
        return views.toPricingRuleView(rule);
    }

    @Override
    public void deletePricingRule(UUID projectId, UUID estimateId, UUID ruleId) {
        EstimateVersion estimate = finder.requireEstimate(projectId, estimateId);
        boolean removed = estimate.getPricingRules().removeIf(rule -> rule.getId().equals(ruleId));
        if (!removed) {
            throw new NotFoundException("Pricing rule not found: " + ruleId);
        }
        projects.save(estimate.getProject());
    }

    @Override
    public PricingSummaryView pricingSummary(UUID projectId, UUID estimateId) {
        EstimateVersion estimate = finder.requireEstimate(projectId, estimateId);
        BigDecimal estimatedCost = calculator.calculateProjectCost(estimate).getTotalCost();
        BigDecimal boqValue = totalBoqValue(estimate);
        BigDecimal runningTotal = estimatedCost;
        BigDecimal nonProfitAdders = BigDecimal.ZERO;
        BigDecimal profit = BigDecimal.ZERO;
        List<PricingLineView> lines = new ArrayList<>();

        for (PricingRule rule : activeRules(estimate)) {
            BigDecimal baseAmount = rule.getBase() == PricingBase.RUNNING_TOTAL
                    ? runningTotal
                    : estimatedCost;
            BigDecimal amount = percentageOf(baseAmount, rule.getPercentage());
            runningTotal = runningTotal.add(amount);
            if (rule.getType() == PricingRuleType.PROFIT) {
                profit = profit.add(amount);
            } else {
                nonProfitAdders = nonProfitAdders.add(amount);
            }
            lines.add(new PricingLineView(
                    rule.getId(), rule.getType(), rule.getName(), rule.getPercentage(), rule.getBase(),
                    rule.getSequence(), baseAmount, amount));
        }

        BigDecimal salesPrice = runningTotal;
        BigDecimal grossProfit = salesPrice.subtract(estimatedCost);
        BigDecimal margin = salesPrice.signum() == 0
                ? BigDecimal.ZERO
                : profit.multiply(ONE_HUNDRED).divide(salesPrice, 4, RoundingMode.HALF_UP);
        return new PricingSummaryView(
                estimatedCost, boqValue, nonProfitAdders, profit, salesPrice, grossProfit,
                profit, margin, boqValue.subtract(salesPrice), lines);
    }

    private void apply(PricingRule rule, PricingRuleRequest request) {
        rule.setType(request.type());
        rule.setName(request.name().trim());
        rule.setPercentage(request.percentage());
        rule.setBase(request.base());
        rule.setSequence(request.sequence() == null ? 0 : request.sequence());
        rule.setActive(!Boolean.FALSE.equals(request.active()));
    }

    private List<PricingRule> activeRules(EstimateVersion estimate) {
        return estimate.getPricingRules().stream()
                .filter(PricingRule::isActive)
                .sorted(Comparator.comparingInt(PricingRule::getSequence))
                .toList();
    }

    private BigDecimal totalBoqValue(EstimateVersion estimate) {
        return estimate.getBoqItems().stream()
                .map(item -> projectCurrencyValue(estimate, item))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal projectCurrencyValue(EstimateVersion estimate, BoqItem item) {
        BigDecimal value = item.getQuantity().multiply(item.getUnitPrice());
        BigDecimal conversionRate = currencies.conversionRate(
                item.getCurrency(), estimate.getProject().getCurrency(),
                estimate.getProject().getUsdTryRate(), estimate.getProject().getEurTryRate());
        return currencies.convert(value, conversionRate);
    }

    private BigDecimal percentageOf(BigDecimal base, BigDecimal percentage) {
        return base.multiply(percentage).divide(ONE_HUNDRED, 4, RoundingMode.HALF_UP);
    }
}
