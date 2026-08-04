package com.project.costestimator.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor
public class CostBreakdown {
    private BigDecimal personnelCost = BigDecimal.ZERO;
    private BigDecimal equipmentCost = BigDecimal.ZERO;
    private BigDecimal fuelCost = BigDecimal.ZERO;
    private BigDecimal materialCost = BigDecimal.ZERO;
    private BigDecimal accommodationCost = BigDecimal.ZERO;
    private BigDecimal transportationCost = BigDecimal.ZERO;
    private BigDecimal overheadCost = BigDecimal.ZERO;
    private BigDecimal taxCost = BigDecimal.ZERO;
    private BigDecimal totalCost = BigDecimal.ZERO;
}
