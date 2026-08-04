package com.project.costestimator.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class ProjectStaffAssignment {
    private UUID id;
    private String projectRole;
    private BigDecimal quantity;
    private BigDecimal allocationPercentage;
    private LocalDate startDate;
    private LocalDate endDate;
    private EstimateVersion estimateVersion;
    private PersonnelResource personnelResource;
    private Shift shift;
}
