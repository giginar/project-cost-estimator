package com.project.costestimator.domain.service;

import com.project.costestimator.domain.EquipmentResource;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class EquipmentEconomicsPolicy {
    public BigDecimal monthlyDepreciation(EquipmentResource equipment) {
        if (!isPositive(equipment.getAcquisitionCost())
                || equipment.getUsefulLifeMonths() == null
                || equipment.getUsefulLifeMonths() <= 0) {
            return BigDecimal.ZERO;
        }
        return equipment.getAcquisitionCost()
                .subtract(zeroIfNull(equipment.getResidualValue()))
                .max(BigDecimal.ZERO)
                .divide(BigDecimal.valueOf(equipment.getUsefulLifeMonths()), 4, RoundingMode.HALF_UP);
    }

    public BigDecimal monthlyPercentage(BigDecimal value, BigDecimal annualRate) {
        if (!isPositive(value) || !isPositive(annualRate)) {
            return BigDecimal.ZERO;
        }
        return value.multiply(annualRate)
                .divide(BigDecimal.valueOf(1200), 4, RoundingMode.HALF_UP);
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
