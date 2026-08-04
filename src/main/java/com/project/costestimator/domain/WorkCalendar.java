package com.project.costestimator.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class WorkCalendar {
    private UUID id;
    private String name;
    private int workingDaysPerWeek;
    private BigDecimal workingHoursPerDay;
    private Project project;
    private List<Shift> shifts = new ArrayList<>();
}
