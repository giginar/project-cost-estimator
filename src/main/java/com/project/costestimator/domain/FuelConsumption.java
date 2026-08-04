package com.project.costestimator.domain;

import com.project.costestimator.domain.enums.FuelType;
import com.project.costestimator.domain.enums.UnitOfMeasure;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class FuelConsumption {
    private UUID id;
    private FuelType fuelType;
    private BigDecimal consumptionPerHour;
    private UnitOfMeasure consumptionUnit;
    private EquipmentResource equipmentResource;
}
