package com.project.costestimator.domain;

import com.project.costestimator.domain.enums.FuelType;
import com.project.costestimator.domain.enums.UnitOfMeasure;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class GeneralUnitPrice {
    private UUID id;
    private String code;
    private String name;
    private FuelType fuelType;
    private UnitOfMeasure unit;
    private BigDecimal unitPrice;
    private boolean active;
    private Project project;
}
