package com.project.costestimator.domain;

import com.project.costestimator.domain.enums.RateType;
import com.project.costestimator.domain.enums.UnitOfMeasure;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class ProjectRate {
    private UUID id;
    private RateType rateType;
    private String name;
    private BigDecimal value;
    private UnitOfMeasure unit;
    private LocalDate validFrom;
    private LocalDate validTo;
    private EstimateVersion estimateVersion;
}
