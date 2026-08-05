package com.project.costestimator.domain;

import com.project.costestimator.domain.enums.DependencyType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class ActivityDependency {
    private UUID id;
    private Activity successor;
    private Activity predecessor;
    private DependencyType type;
    private int lagDays;
}
