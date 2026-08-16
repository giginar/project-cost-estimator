package com.project.costestimator.domain;

import com.project.costestimator.domain.enums.CostCodeType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class CostCode {
    private UUID id;
    private String code;
    private String name;
    private CostCodeType type;
    private boolean active;
    private Project project;
}
