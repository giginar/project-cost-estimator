package com.project.costestimator.domain;

import com.project.costestimator.domain.enums.UnitOfMeasure;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class BoqItem {
    private UUID id;
    private String code;
    private String description;
    private UnitOfMeasure unit;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private Currency currency;
    private EstimateVersion estimateVersion;
    private WbsItem wbsItem;
    private Activity activity;
}
