package com.project.costestimator.domain;

import com.project.costestimator.domain.enums.WorkUnit;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public abstract class ResourceAssignment {
    private UUID id;
    private BigDecimal quantity;
    private BigDecimal plannedWork;
    private WorkUnit workUnit;
    private BigDecimal utilizationRate;
    private LocalDate startDate;
    private LocalDate endDate;
    private boolean overtimeAllowed;
    private Activity activity;
    private Shift shift;
}
