package com.project.costestimator.domain;

import com.project.costestimator.domain.enums.UnitOfMeasure;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
public class MaterialResource extends Resource {
    private String materialType;
    private UnitOfMeasure defaultUnit;
    private String supplier;
    private Integer leadTimeDays;
    private java.math.BigDecimal minimumOrderQuantity;
    private java.math.BigDecimal defaultWastePercentage;
}
