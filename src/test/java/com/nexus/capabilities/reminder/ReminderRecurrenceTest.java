package com.nexus.capabilities.reminder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.capabilities.reminder.dto.ReminderRecurrence;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReminderRecurrenceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Parses standard preset recurrence strings")
    void testParsePresets() {
        assertThat(ReminderRecurrence.fromString("NONE")).isEqualTo(ReminderRecurrence.NONE);
        assertThat(ReminderRecurrence.fromString("none")).isEqualTo(ReminderRecurrence.NONE);
        assertThat(ReminderRecurrence.fromString("")).isEqualTo(ReminderRecurrence.NONE);
        assertThat(ReminderRecurrence.fromString(null)).isEqualTo(ReminderRecurrence.NONE);

        assertThat(ReminderRecurrence.fromString("DAILY")).isEqualTo(ReminderRecurrence.DAILY);
        assertThat(ReminderRecurrence.fromString("WEEKLY")).isEqualTo(ReminderRecurrence.WEEKLY);
        assertThat(ReminderRecurrence.fromString("MONTHLY")).isEqualTo(ReminderRecurrence.MONTHLY);
        assertThat(ReminderRecurrence.fromString("YEARLY")).isEqualTo(ReminderRecurrence.YEARLY);
        assertThat(ReminderRecurrence.fromString("ANNUAL")).isEqualTo(ReminderRecurrence.YEARLY);
    }

    @Test
    @DisplayName("Parses shorthand and natural interval strings")
    void testParseIntervals() {
        ReminderRecurrence m5 = ReminderRecurrence.fromString("5m");
        assertThat(m5.unit()).isEqualTo(ReminderRecurrence.Unit.MINUTES);
        assertThat(m5.amount()).isEqualTo(5);
        assertThat(m5.isSubDay()).isTrue();

        ReminderRecurrence h16 = ReminderRecurrence.fromString("every 16h");
        assertThat(h16.unit()).isEqualTo(ReminderRecurrence.Unit.HOURS);
        assertThat(h16.amount()).isEqualTo(16);
        assertThat(h16.isSubDay()).isTrue();

        ReminderRecurrence d2 = ReminderRecurrence.fromString("every 2 days");
        assertThat(d2.unit()).isEqualTo(ReminderRecurrence.Unit.DAYS);
        assertThat(d2.amount()).isEqualTo(2);
        assertThat(d2.isSubDay()).isFalse();

        ReminderRecurrence w3 = ReminderRecurrence.fromString("3w");
        assertThat(w3.unit()).isEqualTo(ReminderRecurrence.Unit.WEEKS);
        assertThat(w3.amount()).isEqualTo(3);

        ReminderRecurrence mo3 = ReminderRecurrence.fromString("every 3 months");
        assertThat(mo3.unit()).isEqualTo(ReminderRecurrence.Unit.MONTHS);
        assertThat(mo3.amount()).isEqualTo(3);

        ReminderRecurrence y2 = ReminderRecurrence.fromString("2y");
        assertThat(y2.unit()).isEqualTo(ReminderRecurrence.Unit.YEARS);
        assertThat(y2.amount()).isEqualTo(2);

        // Case insensitivity
        assertThat(ReminderRecurrence.fromString("EVERY 45M").amount()).isEqualTo(45);
        assertThat(ReminderRecurrence.fromString("EVERY 16H").amount()).isEqualTo(16);
        assertThat(ReminderRecurrence.fromString("EVERY 3MO").amount()).isEqualTo(3);

        // Fallback on invalid
        assertThat(ReminderRecurrence.fromString("invalid-string")).isEqualTo(ReminderRecurrence.NONE);
        assertThat(ReminderRecurrence.fromString("0m")).isEqualTo(ReminderRecurrence.NONE);
    }

    @Test
    @DisplayName("Guarantees leap-year calendar anchor for Feb 29 across non-leap and leap years")
    void testLeapYearBirthdayAnchor() {
        ReminderRecurrence yearly = ReminderRecurrence.YEARLY;

        // Base anchor: Feb 29 (leap year 2024)
        Map<String, Object> meta = Map.of("anchorDayOfMonth", 29, "anchorMonth", 2);
        LocalTime time = LocalTime.of(9, 0);

        // 2024 -> 2025 (non-leap year -> Feb 28)
        LocalDateTime c1 = yearly.nextOccurrence(LocalDate.of(2024, 2, 29), time, meta);
        assertThat(c1.toLocalDate()).isEqualTo(LocalDate.of(2025, 2, 28));

        // 2025 -> 2026 (non-leap year -> Feb 28)
        LocalDateTime c2 = yearly.nextOccurrence(c1.toLocalDate(), time, meta);
        assertThat(c2.toLocalDate()).isEqualTo(LocalDate.of(2026, 2, 28));

        // 2026 -> 2027 (non-leap year -> Feb 28)
        LocalDateTime c3 = yearly.nextOccurrence(c2.toLocalDate(), time, meta);
        assertThat(c3.toLocalDate()).isEqualTo(LocalDate.of(2027, 2, 28));

        // 2027 -> 2028 (leap year -> automatically snaps back to Feb 29!)
        LocalDateTime c4 = yearly.nextOccurrence(c3.toLocalDate(), time, meta);
        assertThat(c4.toLocalDate()).isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @Test
    @DisplayName("Guarantees day-of-month calendar anchor for every 3 months on the 15th")
    void testQuarterlyCalendarAnchor() {
        ReminderRecurrence every3mo = ReminderRecurrence.fromString("every 3mo");
        Map<String, Object> meta = Map.of("anchorDayOfMonth", 15, "anchorMonth", 1);
        LocalTime time = LocalTime.of(10, 0);

        // 2026-01-15 -> 2026-04-15
        LocalDateTime q1 = every3mo.nextOccurrence(LocalDate.of(2026, 1, 15), time, meta);
        assertThat(q1.toLocalDate()).isEqualTo(LocalDate.of(2026, 4, 15));

        // 2026-04-15 -> 2026-07-15
        LocalDateTime q2 = every3mo.nextOccurrence(q1.toLocalDate(), time, meta);
        assertThat(q2.toLocalDate()).isEqualTo(LocalDate.of(2026, 7, 15));

        // 2026-07-15 -> 2026-10-15
        LocalDateTime q3 = every3mo.nextOccurrence(q2.toLocalDate(), time, meta);
        assertThat(q3.toLocalDate()).isEqualTo(LocalDate.of(2026, 10, 15));

        // 2026-10-15 -> 2027-01-15
        LocalDateTime q4 = every3mo.nextOccurrence(q3.toLocalDate(), time, meta);
        assertThat(q4.toLocalDate()).isEqualTo(LocalDate.of(2027, 1, 15));
    }

    @Test
    @DisplayName("Guarantees month-end anchor for 31st without getting stuck on 28th/30th")
    void testMonthEndAnchor() {
        ReminderRecurrence monthly = ReminderRecurrence.MONTHLY;
        Map<String, Object> meta = Map.of("anchorDayOfMonth", 31, "anchorMonth", 1);
        LocalTime time = LocalTime.of(9, 0);

        // Jan 31 -> Feb 28 (clamped)
        LocalDateTime feb = monthly.nextOccurrence(LocalDate.of(2026, 1, 31), time, meta);
        assertThat(feb.toLocalDate()).isEqualTo(LocalDate.of(2026, 2, 28));

        // Feb 28 -> Mar 31 (recovers to 31!)
        LocalDateTime mar = monthly.nextOccurrence(feb.toLocalDate(), time, meta);
        assertThat(mar.toLocalDate()).isEqualTo(LocalDate.of(2026, 3, 31));

        // Mar 31 -> Apr 30 (clamped)
        LocalDateTime apr = monthly.nextOccurrence(mar.toLocalDate(), time, meta);
        assertThat(apr.toLocalDate()).isEqualTo(LocalDate.of(2026, 4, 30));

        // Apr 30 -> May 31 (recovers to 31!)
        LocalDateTime may = monthly.nextOccurrence(apr.toLocalDate(), time, meta);
        assertThat(may.toLocalDate()).isEqualTo(LocalDate.of(2026, 5, 31));
    }

    @Test
    @DisplayName("Sub-day intervals advance both date and clock with exact elapsed duration")
    void testSubDayIntervals() {
        ReminderRecurrence h16 = ReminderRecurrence.fromString("every 16h");
        LocalDateTime start1 = LocalDateTime.of(2026, 11, 24, 18, 0, 0);
        LocalDateTime next1 = h16.nextOccurrence(start1.toLocalDate(), start1.toLocalTime(), Map.of());
        assertThat(next1).isEqualTo(LocalDateTime.of(2026, 11, 25, 10, 0, 0));

        ReminderRecurrence m45 = ReminderRecurrence.fromString("45m");
        LocalDateTime start2 = LocalDateTime.of(2026, 11, 24, 10, 0, 0);
        LocalDateTime next2 = m45.nextOccurrence(start2.toLocalDate(), start2.toLocalTime(), Map.of());
        assertThat(next2).isEqualTo(LocalDateTime.of(2026, 11, 24, 10, 45, 0));
    }

    @Test
    @DisplayName("Serializes and deserializes cleanly with Jackson")
    void testJacksonSerialization() throws Exception {
        String jsonYearly = objectMapper.writeValueAsString(ReminderRecurrence.YEARLY);
        assertThat(jsonYearly).isEqualTo("\"YEARLY\"");

        ReminderRecurrence parsedYearly = objectMapper.readValue("\"YEARLY\"", ReminderRecurrence.class);
        assertThat(parsedYearly).isEqualTo(ReminderRecurrence.YEARLY);

        ReminderRecurrence h16 = ReminderRecurrence.fromString("every 16h");
        String jsonH16 = objectMapper.writeValueAsString(h16);
        assertThat(jsonH16).isEqualTo("\"EVERY 16h\"");

        ReminderRecurrence parsedH16 = objectMapper.readValue(jsonH16, ReminderRecurrence.class);
        assertThat(parsedH16).isEqualTo(h16);
    }
}
