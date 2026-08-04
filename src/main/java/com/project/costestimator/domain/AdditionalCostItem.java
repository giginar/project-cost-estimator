package com.project.costestimator.domain;

import com.project.costestimator.domain.enums.CalculationBasis;
import com.project.costestimator.domain.enums.CostCategory;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class AdditionalCostItem {
    private UUID id;
    private String code;
    private String name;
    private CostCategory category;
    private CalculationBasis calculationBasis;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private String description;
    private Activity activity;
    private EstimateVersion estimateVersion;
}
