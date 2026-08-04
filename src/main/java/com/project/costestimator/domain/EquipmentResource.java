package com.project.costestimator.domain;

import com.project.costestimator.domain.enums.UnitOfMeasure;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter @NoArgsConstructor
public class EquipmentResource extends Resource {
    private String equipmentType;
    private String manufacturer;
    private String model;
    private BigDecimal capacity;
    private UnitOfMeasure capacityUnit;
    private boolean owned;
    private List<EquipmentComposition> parentCompositions = new ArrayList<>();
    private List<EquipmentComposition> childCompositions = new ArrayList<>();
    private List<FuelConsumption> fuelConsumptions = new ArrayList<>();
}
