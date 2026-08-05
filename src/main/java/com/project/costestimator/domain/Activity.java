package com.project.costestimator.domain;

import com.project.costestimator.domain.enums.ActivityType;
import com.project.costestimator.domain.enums.DurationUnit;
import com.project.costestimator.domain.enums.UnitOfMeasure;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class Activity {
    private UUID id;
    private String code;
    private String name;
    private String description;
    private ActivityType type;
    private BigDecimal plannedQuantity;
    private UnitOfMeasure quantityUnit;
    private BigDecimal plannedDuration;
    private BigDecimal dailyProductionRate;
    private boolean autoSchedule;
    private DurationUnit durationUnit;
    private LocalDate plannedStartDate;
    private LocalDate plannedEndDate;
    private WbsItem wbsItem;
    private List<ResourceAssignment> resourceAssignments = new ArrayList<>();
    private List<AdditionalCostItem> additionalCostItems = new ArrayList<>();
    private List<ActivityDependency> dependencies = new ArrayList<>();
}
