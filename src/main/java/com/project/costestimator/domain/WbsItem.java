package com.project.costestimator.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class WbsItem {
    private UUID id;
    private String code;
    private String name;
    private String description;
    private int sequence;
    private EstimateVersion estimateVersion;
    private WbsItem parent;
    private List<WbsItem> children = new ArrayList<>();
    private List<Activity> activities = new ArrayList<>();
}
