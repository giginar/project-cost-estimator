package com.project.costestimator.application.service.support;

import com.project.costestimator.domain.CostComponent;
import com.project.costestimator.domain.EstimateResourceRate;
import com.project.costestimator.domain.EstimateVersion;
import com.project.costestimator.domain.Resource;
import com.project.costestimator.domain.service.CurrencyConverter;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ResourceRateSynchronizer {
    private static final Currency DEFAULT_CURRENCY = Currency.getInstance("USD");

    private final CurrencyConverter currencies;

    public ResourceRateSynchronizer(CurrencyConverter currencies) {
        this.currencies = currencies;
    }

    public void synchronize(EstimateVersion estimate, Resource resource, boolean replaceExisting) {
        if (replaceExisting) {
            removeDeletedComponents(estimate, resource);
        }
        for (CostComponent component : resource.getCostComponents()) {
            synchronizeComponent(estimate, resource, component, replaceExisting);
        }
    }

    private void removeDeletedComponents(EstimateVersion estimate, Resource resource) {
        Set<UUID> currentComponentIds = resource.getCostComponents().stream()
                .map(CostComponent::getId)
                .collect(Collectors.toSet());
        estimate.getResourceRates().removeIf(rate ->
                rate.getResourceId().equals(resource.getId())
                        && !currentComponentIds.contains(rate.getSourceCostComponentId()));
    }

    private void synchronizeComponent(EstimateVersion estimate, Resource resource,
                                      CostComponent component, boolean replaceExisting) {
        EstimateResourceRate existing = estimate.getResourceRates().stream()
                .filter(rate -> rate.getSourceCostComponentId().equals(component.getId()))
                .findFirst()
                .orElse(null);
        if (existing != null && !replaceExisting) {
            return;
        }

        EstimateResourceRate rate = existing == null
                ? newRate(estimate, resource, component)
                : existing;
        copyComponentValues(estimate, component, rate);
    }

    private EstimateResourceRate newRate(EstimateVersion estimate, Resource resource, CostComponent component) {
        EstimateResourceRate rate = new EstimateResourceRate();
        rate.setId(UUID.randomUUID());
        rate.setResourceId(resource.getId());
        rate.setSourceCostComponentId(component.getId());
        rate.setEstimateVersion(estimate);
        estimate.getResourceRates().add(rate);
        return rate;
    }

    private void copyComponentValues(EstimateVersion estimate, CostComponent component,
                                     EstimateResourceRate rate) {
        rate.setCategory(component.getCategory());
        rate.setName(component.getName());
        rate.setCalculationBasis(component.getCalculationBasis());
        Currency sourceCurrency = component.getCurrency() == null ? DEFAULT_CURRENCY : component.getCurrency();
        BigDecimal conversionRate = currencies.conversionRate(
                sourceCurrency,
                estimate.getProject().getCurrency(),
                estimate.getProject().getUsdTryRate(),
                estimate.getProject().getEurTryRate());
        rate.setUnitPrice(currencies.convert(component.getUnitPrice(), conversionRate));
        rate.setUnit(component.getUnit());
        rate.setTaxable(component.isTaxable());
        rate.setTaxRate(component.getTaxRate());
        rate.setValidFrom(component.getValidFrom());
        rate.setValidTo(component.getValidTo());
    }
}
