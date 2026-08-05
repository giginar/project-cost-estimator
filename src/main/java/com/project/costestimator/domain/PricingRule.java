package com.project.costestimator.domain;

import com.project.costestimator.domain.enums.PricingBase;
import com.project.costestimator.domain.enums.PricingRuleType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class PricingRule {
    private UUID id;
    private PricingRuleType type;
    private String name;
    private BigDecimal percentage;
    private PricingBase base;
    private int sequence;
    private boolean active;
    private EstimateVersion estimateVersion;
}
