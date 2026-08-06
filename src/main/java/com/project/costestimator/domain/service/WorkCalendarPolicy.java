package com.project.costestimator.domain.service;

import com.project.costestimator.domain.WorkCalendar;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class WorkCalendarPolicy {
    private static final BigDecimal DEFAULT_HOURS_PER_DAY = BigDecimal.valueOf(8);

    public boolean isWorkingDay(LocalDate date, WorkCalendar calendar) {
        if (calendar == null || calendar.getWorkingDaysPerWeek() == 0) {
            return true;
        }
        return date.getDayOfWeek().getValue() <= calendar.getWorkingDaysPerWeek();
    }

    public LocalDate nextWorkingDay(LocalDate date, WorkCalendar calendar) {
        LocalDate result = date;
        while (!isWorkingDay(result, calendar)) {
            result = result.plusDays(1);
        }
        return result;
    }

    public LocalDate addWorkingDays(LocalDate date, int days, WorkCalendar calendar) {
        LocalDate result = nextWorkingDay(date, calendar);
        for (int added = 0; added < days; ) {
            result = result.plusDays(1);
            if (isWorkingDay(result, calendar)) {
                added++;
            }
        }
        return result;
    }

    public LocalDate subtractWorkingDays(LocalDate date, int days, WorkCalendar calendar) {
        LocalDate result = date;
        while (!isWorkingDay(result, calendar)) {
            result = result.minusDays(1);
        }
        for (int subtracted = 0; subtracted < days; ) {
            result = result.minusDays(1);
            if (isWorkingDay(result, calendar)) {
                subtracted++;
            }
        }
        return result;
    }

    public long workingDayCount(LocalDate startDate, LocalDate endDate, WorkCalendar calendar) {
        long count = 0;
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            if (isWorkingDay(date, calendar)) {
                count++;
            }
        }
        return Math.max(1, count);
    }

    public BigDecimal workingDays(LocalDate startDate, LocalDate endDate,
                                  BigDecimal fallback, WorkCalendar calendar) {
        if (startDate == null || endDate == null) {
            return fallback;
        }
        if (calendar == null || calendar.getWorkingDaysPerWeek() == 0) {
            return BigDecimal.valueOf(ChronoUnit.DAYS.between(startDate, endDate) + 1);
        }
        return BigDecimal.valueOf(workingDayCount(startDate, endDate, calendar));
    }

    public BigDecimal hoursPerDay(WorkCalendar calendar) {
        if (calendar == null || calendar.getWorkingHoursPerDay() == null
                || calendar.getWorkingHoursPerDay().signum() <= 0) {
            return DEFAULT_HOURS_PER_DAY;
        }
        return calendar.getWorkingHoursPerDay();
    }
}
