package com.project.costestimator.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter @NoArgsConstructor
public class ActivityEquipmentAssignment extends ResourceAssignment {
    private BigDecimal operatingHoursPerDay;
    private BigDecimal standbyHoursPerDay;
    private EquipmentResource equipmentResource;
    private List<EquipmentCrewAssignment> crewAssignments = new ArrayList<>();
}
