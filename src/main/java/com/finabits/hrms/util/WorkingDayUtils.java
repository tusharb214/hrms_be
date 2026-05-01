package com.finabits.hrms.util;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Utility class that defines the organisation's working-day calendar.
 *
 * ┌─────────────────────────────────────────────────────┐
 * │  Work week: Monday – Saturday  (6 days)             │
 * │  Non-working day: Sunday ONLY                       │
 * │                                                     │
 * │  Saturday is a FULL working day — employees must    │
 * │  check in and check out exactly like any weekday.   │
 * │  Leave must be applied if Saturday is to be taken   │
 * │  off.                                               │
 * └─────────────────────────────────────────────────────┘
 */
public final class WorkingDayUtils {

    private WorkingDayUtils() {}

    /**
     * Returns {@code true} if the given date is a working day
     * (Monday–Saturday).  Sunday is the only non-working day.
     */
    public static boolean isWorkingDay(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    /**
     * Returns {@code true} if the given date is a non-working day (Sunday).
     */
    public static boolean isNonWorkingDay(LocalDate date) {
        return !isWorkingDay(date);
    }

    /**
     * Counts the number of working days (Mon–Sat) in the range [from, to],
     * inclusive on both ends.
     *
     * Used by LeaveService to calculate how many leave days a request spans.
     *
     * <pre>
     *   Friday → Monday  =  3 days  (Fri, Sat, Mon)
     *   Friday → Sunday  =  2 days  (Fri, Sat)   ← Sunday not counted
     * </pre>
     */
    public static int countWorkingDays(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) return 0;
        int count = 0;
        LocalDate cur = from;
        while (!cur.isAfter(to)) {
            if (isWorkingDay(cur)) count++;
            cur = cur.plusDays(1);
        }
        return count;
    }

    /**
     * Counts working days in the range [from, to) — exclusive upper bound.
     * Useful for "last N days" dashboard calculations.
     */
    public static long countWorkingDaysExclusive(LocalDate from, LocalDate toExclusive) {
        if (!from.isBefore(toExclusive)) return 0;
        return from.datesUntil(toExclusive)
                .filter(WorkingDayUtils::isWorkingDay)
                .count();
    }
}