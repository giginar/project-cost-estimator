package com.project.costestimator.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class EquipmentCrewAssignment {
    private UUID id;
    private String roleName;
    private BigDecimal quantity;
    private BigDecimal workingHoursPerDay;
    private boolean mandatory;
    private ActivityEquipmentAssignment equipmentAssignment;
    private PersonnelResource personnelResource;
}
