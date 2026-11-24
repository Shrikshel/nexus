package com.nexus.capabilities.reminder.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generalized recurrence policies for automated rollover.
 * Supports calendar units (years, months, weeks, days) with anchor protection
 * (leap years, month-end stability) and physical elapsed durations (hours, minutes).
 */
public class ReminderRecurrence {

    public enum Unit {
        NONE,
        MINUTES,
        HOURS,
        DAYS,
        WEEKS,
        MONTHS,
        YEARS
    }

    private static final Pattern INTERVAL_PATTERN = Pattern.compile("^(\\d+)\\s*([a-zA-Z]+)$");

    public static final ReminderRecurrence NONE = new ReminderRecurrence(Unit.NONE, 0, "NONE");
    public static final ReminderRecurrence DAILY = new ReminderRecurrence(Unit.DAYS, 1, "DAILY");
    public static final ReminderRecurrence WEEKLY = new ReminderRecurrence(Unit.WEEKS, 1, "WEEKLY");
    public static final ReminderRecurrence MONTHLY = new ReminderRecurrence(Unit.MONTHS, 1, "MONTHLY");
    public static final ReminderRecurrence YEARLY = new ReminderRecurrence(Unit.YEARS, 1, "YEARLY");

    private final Unit unit;
    private final int amount;
    private final String rawToken;

    public ReminderRecurrence(Unit unit, int amount, String rawToken) {
        this.unit = unit != null ? unit : Unit.NONE;
        this.amount = Math.max(0, amount);
        this.rawToken = rawToken != null ? rawToken : (this.unit == Unit.NONE ? "NONE" : this.unit.name());
    }

    public Unit unit() {
        return unit;
    }

    public int amount() {
        return amount;
    }

    public String rawToken() {
        return rawToken;
    }

    public boolean isNone() {
        return unit == Unit.NONE;
    }

    public boolean isSubDay() {
        return unit == Unit.MINUTES || unit == Unit.HOURS;
    }

    @JsonCreator
    public static ReminderRecurrence fromString(String val) {
        if (val == null || val.isBlank()) {
            return NONE;
        }
        String clean = val.trim();
        String upper = clean.toUpperCase(Locale.ROOT);

        // Standard presets
        if ("NONE".equals(upper)) return NONE;
        if ("DAILY".equals(upper)) return DAILY;
        if ("WEEKLY".equals(upper)) return WEEKLY;
        if ("MONTHLY".equals(upper)) return MONTHLY;
        if ("YEARLY".equals(upper) || "ANNUAL".equals(upper)) return YEARLY;

        // Strip natural language prefix: "EVERY ", "EVERY_", "EVERY-"
        String token = clean;
        if (token.toUpperCase(Locale.ROOT).startsWith("EVERY")) {
            token = token.substring(5).trim();
            if (token.startsWith("_") || token.startsWith("-")) {
                token = token.substring(1).trim();
            }
        }

        // Check if now it's a keyword
        String upperToken = token.toUpperCase(Locale.ROOT);
        if ("DAY".equals(upperToken) || "DAILY".equals(upperToken)) return DAILY;
        if ("WEEK".equals(upperToken) || "WEEKLY".equals(upperToken)) return WEEKLY;
        if ("MONTH".equals(upperToken) || "MONTHLY".equals(upperToken)) return MONTHLY;
        if ("YEAR".equals(upperToken) || "YEARLY".equals(upperToken) || "ANNUAL".equals(upperToken)) return YEARLY;

        Matcher m = INTERVAL_PATTERN.matcher(token);
        if (!m.matches()) {
            return NONE;
        }

        int amount;
        try {
            amount = Integer.parseInt(m.group(1));
        } catch (NumberFormatException e) {
            return NONE;
        }

        if (amount <= 0) {
            return NONE;
        }

        String u = m.group(2).toLowerCase(Locale.ROOT);
        Unit parsedUnit;
        if (u.equals("m") || u.startsWith("min")) {
            parsedUnit = Unit.MINUTES;
        } else if (u.equals("h") || u.startsWith("hr") || u.startsWith("hour")) {
            parsedUnit = Unit.HOURS;
        } else if (u.equals("d") || u.startsWith("day")) {
            parsedUnit = Unit.DAYS;
        } else if (u.equals("w") || u.startsWith("wk") || u.startsWith("week")) {
            parsedUnit = Unit.WEEKS;
        } else if (u.equals("mo") || u.startsWith("mon") || u.startsWith("month")) {
            parsedUnit = Unit.MONTHS;
        } else if (u.equals("y") || u.startsWith("yr") || u.startsWith("year")) {
            parsedUnit = Unit.YEARS;
        } else {
            return NONE;
        }

        // Return canonical constants for standard 1-units
        if (amount == 1) {
            if (parsedUnit == Unit.DAYS) return DAILY;
            if (parsedUnit == Unit.WEEKS) return WEEKLY;
            if (parsedUnit == Unit.MONTHS) return MONTHLY;
            if (parsedUnit == Unit.YEARS) return YEARLY;
        }

        return new ReminderRecurrence(parsedUnit, amount, clean);
    }

    /**
     * Calculates the next occurrence timestamp preserving calendar anchors (leap year, month-end).
     */
    public LocalDateTime nextOccurrence(LocalDate currentDate, LocalTime currentTime, Map<String, Object> metadata) {
        if (currentDate == null) {
            return null;
        }
        LocalTime effectiveTime = currentTime != null ? currentTime : LocalTime.of(9, 0);
        if (unit == Unit.NONE) {
            return LocalDateTime.of(currentDate, effectiveTime);
        }

        // Resolve calendar anchor points (day of month & month) to guarantee leap-year and month-end stability
        int anchorDay = currentDate.getDayOfMonth();
        int anchorMonth = currentDate.getMonthValue();
        if (metadata != null) {
            Object d = metadata.get("anchorDayOfMonth");
            if (d instanceof Number num) {
                anchorDay = num.intValue();
            } else if (d != null) {
                try {
                    anchorDay = Integer.parseInt(d.toString());
                } catch (Exception ignored) {}
            }
            Object m = metadata.get("anchorMonth");
            if (m instanceof Number num) {
                anchorMonth = num.intValue();
            } else if (m != null) {
                try {
                    anchorMonth = Integer.parseInt(m.toString());
                } catch (Exception ignored) {}
            }
        }

        return switch (unit) {
            case MINUTES -> {
                LocalDateTime dt = LocalDateTime.of(currentDate, effectiveTime);
                yield dt.plusMinutes(amount);
            }
            case HOURS -> {
                LocalDateTime dt = LocalDateTime.of(currentDate, effectiveTime);
                yield dt.plusHours(amount);
            }
            case DAYS -> {
                LocalDate nextDate = currentDate.plusDays(amount);
                yield LocalDateTime.of(nextDate, effectiveTime);
            }
            case WEEKS -> {
                LocalDate nextDate = currentDate.plusWeeks(amount);
                yield LocalDateTime.of(nextDate, effectiveTime);
            }
            case MONTHS -> {
                YearMonth nextYm = YearMonth.from(currentDate).plusMonths(amount);
                int maxDay = nextYm.lengthOfMonth();
                int targetDay = Math.min(anchorDay, maxDay);
                LocalDate nextDate = nextYm.atDay(targetDay);
                yield LocalDateTime.of(nextDate, effectiveTime);
            }
            case YEARS -> {
                int nextYear = currentDate.getYear() + amount;
                YearMonth nextYm = YearMonth.of(nextYear, anchorMonth);
                int maxDay = nextYm.lengthOfMonth();
                int targetDay = Math.min(anchorDay, maxDay);
                LocalDate nextDate = nextYm.atDay(targetDay);
                yield LocalDateTime.of(nextDate, effectiveTime);
            }
            case NONE -> LocalDateTime.of(currentDate, effectiveTime);
        };
    }

    /**
     * Backward-compatible helper calculating next date.
     */
    public LocalDate nextOccurrence(LocalDate currentDate) {
        LocalDateTime dt = nextOccurrence(currentDate, LocalTime.of(9, 0), Collections.emptyMap());
        return dt != null ? dt.toLocalDate() : null;
    }

    @JsonValue
    public String format() {
        if (unit == Unit.NONE) return "NONE";
        if (unit == Unit.DAYS && amount == 1) return "DAILY";
        if (unit == Unit.WEEKS && amount == 1) return "WEEKLY";
        if (unit == Unit.MONTHS && amount == 1) return "MONTHLY";
        if (unit == Unit.YEARS && amount == 1) return "YEARLY";
        return switch (unit) {
            case MINUTES -> "EVERY " + amount + "m";
            case HOURS -> "EVERY " + amount + "h";
            case DAYS -> "EVERY " + amount + "d";
            case WEEKS -> "EVERY " + amount + "w";
            case MONTHS -> "EVERY " + amount + "mo";
            case YEARS -> "EVERY " + amount + "y";
            default -> "NONE";
        };
    }

    @Override
    public String toString() {
        return format();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReminderRecurrence that = (ReminderRecurrence) o;
        return amount == that.amount && unit == that.unit;
    }

    @Override
    public int hashCode() {
        return Objects.hash(unit, amount);
    }
}
