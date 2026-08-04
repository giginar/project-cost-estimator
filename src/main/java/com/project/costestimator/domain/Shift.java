package com.project.costestimator.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class Shift {
    private UUID id;
    private String name;
    private LocalTime startTime;
    private LocalTime endTime;
    private BigDecimal paidHours;
    private WorkCalendar workCalendar;
}
