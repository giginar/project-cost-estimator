package com.project.costestimator.domain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

public final class CurrencyConverter {
    private static final Currency TRY = Currency.getInstance("TRY");
    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency EUR = Currency.getInstance("EUR");

    public Currency fromCode(String currencyCode) {
        return Currency.getInstance(currencyCode.toUpperCase());
    }

    public BigDecimal conversionRate(Currency source, Currency target,
                                     BigDecimal usdTryRate, BigDecimal eurTryRate) {
        if (source.equals(target)) {
            return BigDecimal.ONE;
        }
        BigDecimal sourceInTry = valueInTry(source, usdTryRate, eurTryRate);
        BigDecimal targetInTry = valueInTry(target, usdTryRate, eurTryRate);
        return sourceInTry.divide(targetInTry, 10, RoundingMode.HALF_UP);
    }

    public BigDecimal convert(BigDecimal value, BigDecimal conversionRate) {
        if (value == null) {
            return null;
        }
        return value.multiply(conversionRate)
                .setScale(4, RoundingMode.HALF_UP)
                .stripTrailingZeros();
    }

    private BigDecimal valueInTry(Currency currency, BigDecimal usdTryRate, BigDecimal eurTryRate) {
        if (TRY.equals(currency)) {
            return BigDecimal.ONE;
        }
        if (USD.equals(currency)) {
            return requirePositiveRate(usdTryRate, "USD/TRY");
        }
        if (EUR.equals(currency)) {
            return requirePositiveRate(eurTryRate, "EUR/TRY");
        }
        throw new IllegalArgumentException("Unsupported currency: " + currency);
    }

    private BigDecimal requirePositiveRate(BigDecimal rate, String name) {
        if (rate == null || rate.signum() <= 0) {
            throw new IllegalArgumentException(name + " rate is required for currency conversion");
        }
        return rate;
    }
}
