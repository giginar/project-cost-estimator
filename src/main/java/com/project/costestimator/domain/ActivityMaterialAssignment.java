package com.project.costestimator.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor
public class ActivityMaterialAssignment extends ResourceAssignment {
    private BigDecimal requiredQuantity;
    private BigDecimal wastePercentage;
    private MaterialResource materialResource;
}
