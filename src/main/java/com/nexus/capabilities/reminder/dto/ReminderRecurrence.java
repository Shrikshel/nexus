package com.nexus.capabilities.reminder.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
     * Resolves anchor day of month from metadata or fallback date.
     */
    public static int resolveAnchorDay(LocalDate date, Map<String, Object> metadata) {
        if (metadata != null) {
            Object d = metadata.get("anchorDayOfMonth");
            if (d instanceof Number num) {
                return num.intValue();
            } else if (d != null) {
                try {
                    return Integer.parseInt(d.toString());
                } catch (Exception ignored) {}
            }
        }
        return date != null ? date.getDayOfMonth() : 1;
    }

    /**
     * Resolves anchor month value from metadata or fallback date.
     */
    public static int resolveAnchorMonth(LocalDate date, Map<String, Object> metadata) {
        if (metadata != null) {
            Object m = metadata.get("anchorMonth");
            if (m instanceof Number num) {
                return num.intValue();
            } else if (m != null) {
                try {
                    return Integer.parseInt(m.toString());
                } catch (Exception ignored) {}
            }
        }
        return date != null ? date.getMonthValue() : 1;
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
        int anchorDay = resolveAnchorDay(currentDate, metadata);
        int anchorMonth = resolveAnchorMonth(currentDate, metadata);

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
     * Fast-forwards past dates to the current or upcoming cycle occurrence.
     * Preserves calendar anchors and leap-year stability.
     */
    public LocalDateTime fastForward(LocalDate currentDate, LocalTime currentTime, ZoneId zone, Map<String, Object> metadata, Instant now) {
        if (currentDate == null) {
            return null;
        }
        LocalTime effectiveTime = currentTime != null ? currentTime : LocalTime.of(9, 0);
        if (unit == Unit.NONE) {
            return LocalDateTime.of(currentDate, effectiveTime);
        }

        ZoneId effectiveZone = zone != null ? zone : ZoneId.systemDefault();
        Instant threshold = now != null ? now.minusSeconds(30) : Instant.now().minusSeconds(30);

        ZonedDateTime currentZdt = ZonedDateTime.of(currentDate, effectiveTime, effectiveZone);
        if (!currentZdt.toInstant().isBefore(threshold)) {
            return LocalDateTime.of(currentDate, effectiveTime);
        }

        ZonedDateTime nowZdt = (now != null ? now : Instant.now()).atZone(effectiveZone);
        LocalDate dateCursor = currentDate;
        LocalTime timeCursor = effectiveTime;

        int anchorDay = resolveAnchorDay(currentDate, metadata);
        int anchorMonth = resolveAnchorMonth(currentDate, metadata);

        switch (unit) {
            case YEARS -> {
                int yearDiff = nowZdt.getYear() - dateCursor.getYear();
                if (yearDiff > 0) {
                    int steps = (yearDiff / amount) * amount;
                    int candidateYear = dateCursor.getYear() + steps;
                    YearMonth ym = YearMonth.of(candidateYear, anchorMonth);
                    int maxDay = ym.lengthOfMonth();
                    dateCursor = ym.atDay(Math.min(anchorDay, maxDay));
                }
            }
            case MONTHS -> {
                long monthsDiff = java.time.temporal.ChronoUnit.MONTHS.between(
                        YearMonth.from(dateCursor), YearMonth.from(nowZdt.toLocalDate())
                );
                if (monthsDiff > 0) {
                    long steps = (monthsDiff / amount) * amount;
                    YearMonth ym = YearMonth.from(dateCursor).plusMonths(steps);
                    int maxDay = ym.lengthOfMonth();
                    dateCursor = ym.atDay(Math.min(anchorDay, maxDay));
                }
            }
            case WEEKS -> {
                long weeksDiff = java.time.temporal.ChronoUnit.WEEKS.between(dateCursor, nowZdt.toLocalDate());
                if (weeksDiff > 0) {
                    long steps = (weeksDiff / amount) * amount;
                    dateCursor = dateCursor.plusWeeks(steps);
                }
            }
            case DAYS -> {
                long daysDiff = java.time.temporal.ChronoUnit.DAYS.between(dateCursor, nowZdt.toLocalDate());
                if (daysDiff > 0) {
                    long steps = (daysDiff / amount) * amount;
                    dateCursor = dateCursor.plusDays(steps);
                }
            }
            case HOURS -> {
                Duration dur = Duration.between(ZonedDateTime.of(dateCursor, timeCursor, effectiveZone), nowZdt);
                long hoursDiff = dur.toHours();
                if (hoursDiff > 0) {
                    long steps = (hoursDiff / amount) * amount;
                    LocalDateTime ldt = LocalDateTime.of(dateCursor, timeCursor).plusHours(steps);
                    dateCursor = ldt.toLocalDate();
                    timeCursor = ldt.toLocalTime();
                }
            }
            case MINUTES -> {
                Duration dur = Duration.between(ZonedDateTime.of(dateCursor, timeCursor, effectiveZone), nowZdt);
                long minsDiff = dur.toMinutes();
                if (minsDiff > 0) {
                    long steps = (minsDiff / amount) * amount;
                    LocalDateTime ldt = LocalDateTime.of(dateCursor, timeCursor).plusMinutes(steps);
                    dateCursor = ldt.toLocalDate();
                    timeCursor = ldt.toLocalTime();
                }
            }
            default -> {}
        }

        // Advance step-by-step if still before threshold (handles clock time edge cases)
        int safetyLimit = 0;
        while (ZonedDateTime.of(dateCursor, timeCursor, effectiveZone).toInstant().isBefore(threshold) && safetyLimit < 500) {
            LocalDateTime next = nextOccurrence(dateCursor, timeCursor, metadata);
            if (next == null || (next.toLocalDate().isEqual(dateCursor) && next.toLocalTime().equals(timeCursor))) {
                break;
            }
            dateCursor = next.toLocalDate();
            timeCursor = next.toLocalTime();
            safetyLimit++;
        }

        return LocalDateTime.of(dateCursor, timeCursor);
    }

    /**
     * Canonical cycle formatter for active state tracking.
     */
    public String formatCycle(LocalDate date, LocalTime time) {
        if (unit == Unit.NONE) {
            return date != null ? String.valueOf(date.getYear()) : "UNKNOWN";
        }
        if (isSubDay()) {
            return date + "T" + (time != null ? time.toString().substring(0, 5) : "09:00");
        } else if (unit == Unit.DAYS || unit == Unit.WEEKS) {
            return date != null ? date.toString() : "UNKNOWN";
        } else if (unit == Unit.MONTHS) {
            return date != null ? date.getYear() + "-" + String.format("%02d", date.getMonthValue()) : "UNKNOWN";
        } else {
            return date != null ? String.valueOf(date.getYear()) : "UNKNOWN";
        }
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
