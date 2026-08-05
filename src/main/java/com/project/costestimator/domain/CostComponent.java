package com.project.costestimator.domain;

import com.project.costestimator.domain.enums.CalculationBasis;
import com.project.costestimator.domain.enums.CostCategory;
import com.project.costestimator.domain.enums.UnitOfMeasure;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class CostComponent {
    private UUID id;
    private CostCategory category;
    private String name;
    private CalculationBasis calculationBasis;
    private BigDecimal unitPrice;
    private Currency currency;
    private UnitOfMeasure unit;
    private boolean taxable;
    private BigDecimal taxRate;
    private LocalDate validFrom;
    private LocalDate validTo;
    private boolean generated;
    private Resource resource;
}
