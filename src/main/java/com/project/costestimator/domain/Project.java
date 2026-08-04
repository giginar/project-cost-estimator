package com.project.costestimator.domain;

import com.project.costestimator.domain.enums.ProjectStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor
public class Project {
    private UUID id;
    private String code;
    private String name;
    private String description;
    private LocalDate plannedStartDate;
    private LocalDate plannedEndDate;
    private Currency currency;
    private String languageCode;
    private BigDecimal usdTryRate;
    private BigDecimal eurTryRate;
    private ProjectStatus status;
    private List<EstimateVersion> estimateVersions = new ArrayList<>();
    private WorkCalendar workCalendar;
}
