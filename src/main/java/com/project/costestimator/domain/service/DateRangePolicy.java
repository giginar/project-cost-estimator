package com.project.costestimator.domain.service;

import java.time.LocalDate;

public final class DateRangePolicy {
    public void validate(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }
    }
}
