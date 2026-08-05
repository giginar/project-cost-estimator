package com.project.costestimator.domain;

import com.project.costestimator.domain.enums.CalculationBasis;
import com.project.costestimator.domain.enums.CostCategory;
import com.project.costestimator.domain.enums.UnitOfMeasure;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class EstimateResourceRate {
    private UUID id;
    private UUID resourceId;
    private UUID sourceCostComponentId;
    private CostCategory category;
    private String name;
    private CalculationBasis calculationBasis;
    private BigDecimal unitPrice;
    private UnitOfMeasure unit;
    private boolean taxable;
    private BigDecimal taxRate;
    private LocalDate validFrom;
    private LocalDate validTo;
    private EstimateVersion estimateVersion;
}
