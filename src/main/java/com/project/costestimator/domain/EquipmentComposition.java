package com.project.costestimator.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class EquipmentComposition {
    private UUID id;
    private BigDecimal quantity;
    private String description;
    private EquipmentResource parentEquipment;
    private EquipmentResource childEquipment;
}
