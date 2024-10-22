package org.bihmi.jal.anon.util;

import org.deidentifier.arx.aggregates.HierarchyBuilderDate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.Locale;

public class DateTransformationHelper {

    protected static String reverseDateEncoding(String encodedDate, HierarchyBuilderDate.Granularity granularity){
        // guard clause if date is suppressed
        String date1 = handleSuppressed(encodedDate);
        if (date1 != null) return date1;

        return switch (granularity) {
            case WEEK_YEAR -> decodeWeekYear(encodedDate);
            case MONTH_YEAR -> decodeMonthYear(encodedDate);
            case QUARTER_YEAR -> decodeQuarterYear(encodedDate);
            case YEAR -> decodeYear(encodedDate);
            case DECADE -> decodeDecade(encodedDate);
            default -> throw new IllegalStateException("Unexpected value: " + granularity);
        };
    }

    private static String decodeYear(String encodedDate) {
        int year = Integer.parseInt(encodedDate.trim());
        LocalDate middleDate = LocalDate.of(year, 7, 1);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        return middleDate.format(formatter);
    }

    private static String decodeQuarterYear(String encodedDate) {
        String[] parts = encodedDate.split(" ");
        int quarter = Integer.parseInt(parts[0].replace("Q", ""));  // e.g. "Q2"
        int year = Integer.parseInt(parts[1]);

        int month = (quarter - 1) * 3 + 2;
        LocalDate date = LocalDate.of(year, month, 1);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        return date.format(formatter);
    }

    private static String decodeMonthYear(String encodedDate) {
        String[] parts = encodedDate.trim().split("/");

        int month = Integer.parseInt(parts[0].trim());
        int year = Integer.parseInt(parts[1].trim());
        LocalDate date = LocalDate.of(year, month, 1);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        return date.format(formatter);
    }

    private static String decodeWeekYear(String encodedDate) {
        String[] parts = encodedDate.trim().split("/");

        int week = Integer.parseInt(parts[0].trim());
        int year = Integer.parseInt(parts[1].trim());

        LocalDate date = LocalDate.ofYearDay(year, 1)
                .with(WeekFields.of(Locale.getDefault()).weekOfYear(), week)
                .with(WeekFields.of(Locale.getDefault()).dayOfWeek(), 1);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        return date.format(formatter);
    }

    private static String decodeDecade(String encodedDate) {
        String cleanedInterval = encodedDate.replace("[", "").replace("[", "").replace("]", "");
        String[] bounds = cleanedInterval.split(",");

        int lowerBound = Integer.parseInt(bounds[0].trim());
        int middleYear = lowerBound + 5;

        LocalDate date = LocalDate.of(middleYear, 1, 1);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        return date.format(formatter);
    }

    private static String handleSuppressed(String encodedDate) {
        if (encodedDate.contains("NULL") || encodedDate == "*"){
            LocalDate date = LocalDate.of(1, 1, 1);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            return date.format(formatter);
        }
        return null;
    }

}
