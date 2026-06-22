package com.capacityplanner.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

public class PeriodSlicer {

    public record PeriodBounds(LocalDate start, LocalDate end, String label) {}

    public static List<PeriodBounds> slice(LocalDate from, LocalDate to, String granularity) {
        return switch (granularity.toUpperCase()) {
            case "WEEK"    -> sliceWeeks(from, to);
            case "MONTH"   -> sliceMonths(from, to);
            case "QUARTER" -> sliceQuarters(from, to);
            default -> throw new IllegalArgumentException("Unknown granularity: " + granularity);
        };
    }

    private static List<PeriodBounds> sliceWeeks(LocalDate from, LocalDate to) {
        List<PeriodBounds> result = new ArrayList<>();
        LocalDate weekStart = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        while (!weekStart.isAfter(to)) {
            LocalDate weekEnd = weekStart.plusDays(6);
            result.add(new PeriodBounds(
                weekStart.isBefore(from) ? from : weekStart,
                weekEnd.isAfter(to)      ? to   : weekEnd,
                String.format("W%02d %d",
                    weekStart.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),
                    weekStart.get(IsoFields.WEEK_BASED_YEAR))
            ));
            weekStart = weekStart.plusWeeks(1);
        }
        return result;
    }

    private static List<PeriodBounds> sliceMonths(LocalDate from, LocalDate to) {
        List<PeriodBounds> result = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM yyyy");
        LocalDate monthStart = from.withDayOfMonth(1);
        while (!monthStart.isAfter(to)) {
            LocalDate monthEnd = monthStart.with(TemporalAdjusters.lastDayOfMonth());
            result.add(new PeriodBounds(
                monthStart.isBefore(from) ? from : monthStart,
                monthEnd.isAfter(to)      ? to   : monthEnd,
                monthStart.format(fmt)
            ));
            monthStart = monthStart.plusMonths(1);
        }
        return result;
    }

    private static List<PeriodBounds> sliceQuarters(LocalDate from, LocalDate to) {
        List<PeriodBounds> result = new ArrayList<>();
        int startMonth = ((from.getMonthValue() - 1) / 3) * 3 + 1;
        LocalDate qStart = LocalDate.of(from.getYear(), startMonth, 1);
        while (!qStart.isAfter(to)) {
            int quarter = (qStart.getMonthValue() - 1) / 3 + 1;
            LocalDate qEnd = qStart.plusMonths(3).minusDays(1);
            result.add(new PeriodBounds(
                qStart.isBefore(from) ? from : qStart,
                qEnd.isAfter(to)      ? to   : qEnd,
                "Q" + quarter + " " + qStart.getYear()
            ));
            qStart = qStart.plusMonths(3);
        }
        return result;
    }
}
