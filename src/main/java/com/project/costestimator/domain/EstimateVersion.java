package com.project.costestimator.domain;

import com.project.costestimator.domain.enums.EstimateStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class EstimateVersion {
    private UUID id;
    private String name;
    private String description;
    private int versionNumber;
    private EstimateStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime approvedAt;
    private Project project;
    private List<WbsItem> wbsItems = new ArrayList<>();
    private List<ProjectStaffAssignment> projectStaffAssignments = new ArrayList<>();
    private List<ProjectRate> projectRates = new ArrayList<>();
    private List<AdditionalCostItem> projectLevelCosts = new ArrayList<>();
}
