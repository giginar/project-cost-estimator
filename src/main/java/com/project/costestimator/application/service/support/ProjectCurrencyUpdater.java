package com.project.costestimator.application.service.support;

import com.project.costestimator.domain.Project;
import com.project.costestimator.domain.ProjectRate;
import com.project.costestimator.domain.enums.RateType;
import com.project.costestimator.domain.service.CurrencyConverter;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;

public final class ProjectCurrencyUpdater {
    private final CurrencyConverter currencies;
    private final Clock clock;

    public ProjectCurrencyUpdater(CurrencyConverter currencies, Clock clock) {
        this.currencies = currencies;
        this.clock = clock;
    }

    public void convertProjectPrices(Project project, Currency targetCurrency,
                                     BigDecimal usdTryRate, BigDecimal eurTryRate) {
        Currency sourceCurrency = project.getCurrency();
        BigDecimal conversionRate = currencies.conversionRate(
                sourceCurrency, targetCurrency, usdTryRate, eurTryRate);

        project.getEstimateVersions().forEach(estimate -> {
            estimate.getResourceRates().forEach(rate ->
                    rate.setUnitPrice(currencies.convert(rate.getUnitPrice(), conversionRate)));
            estimate.getProjectLevelCosts().forEach(cost ->
                    cost.setUnitPrice(currencies.convert(cost.getUnitPrice(), conversionRate)));
            estimate.getWbsItems().stream()
                    .flatMap(wbs -> wbs.getActivities().stream())
                    .flatMap(activity -> activity.getAdditionalCostItems().stream())
                    .forEach(cost -> cost.setUnitPrice(currencies.convert(cost.getUnitPrice(), conversionRate)));
            estimate.getProjectRates().add(exchangeRate(
                    estimate, sourceCurrency, targetCurrency, conversionRate));
        });
    }

    private ProjectRate exchangeRate(com.project.costestimator.domain.EstimateVersion estimate,
                                     Currency sourceCurrency, Currency targetCurrency,
                                     BigDecimal conversionRate) {
        ProjectRate exchangeRate = new ProjectRate();
        exchangeRate.setId(UUID.randomUUID());
        exchangeRate.setRateType(RateType.EXCHANGE_RATE);
        exchangeRate.setName("1 " + sourceCurrency.getCurrencyCode() + " = "
                + conversionRate.stripTrailingZeros().toPlainString() + " "
                + targetCurrency.getCurrencyCode());
        exchangeRate.setValue(conversionRate);
        exchangeRate.setValidFrom(LocalDate.now(clock));
        exchangeRate.setEstimateVersion(estimate);
        return exchangeRate;
    }
}
