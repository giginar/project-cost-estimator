package com.project.costestimator.domain;

import com.project.costestimator.domain.enums.ResourceStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public abstract class Resource {
    private UUID id;
    private String code;
    private String name;
    private String description;
    private ResourceStatus status;
    private boolean shared;
    private UUID ownerProjectId;
    private List<CostComponent> costComponents = new ArrayList<>();
}
