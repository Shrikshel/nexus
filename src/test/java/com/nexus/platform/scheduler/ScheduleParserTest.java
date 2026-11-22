package com.nexus.platform.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleParserTest {

    @Test
    @DisplayName("Presets translate to accurate systemd and launchd directives")
    void testPresets() {
        var daily = ScheduleParser.parse("daily");
        assertThat(daily.systemdDirective()).isEqualTo("OnCalendar=*-*-* 00:00:00");
        assertThat(daily.humanDescription()).contains("Every day at 00:00:00");
        assertThat(daily.isMonotonic()).isFalse();

        var midnight = ScheduleParser.parse("midnight");
        assertThat(midnight.systemdDirective()).isEqualTo("OnCalendar=*-*-* 00:00:00");

        var hourly = ScheduleParser.parse("hourly");
        assertThat(hourly.systemdDirective()).isEqualTo("OnCalendar=hourly");

        var weekly = ScheduleParser.parse("weekly");
        assertThat(weekly.systemdDirective()).isEqualTo("OnCalendar=Mon *-*-* 00:00:00");

        var twiceWeekly = ScheduleParser.parse("twice-weekly");
        assertThat(twiceWeekly.systemdDirective()).isEqualTo("OnCalendar=Mon,Thu *-*-* 00:00:00");

        var biweekly = ScheduleParser.parse("biweekly");
        assertThat(biweekly.systemdDirective()).isEqualTo("OnCalendar=Mon,Thu *-*-* 00:00:00");

        var monthly = ScheduleParser.parse("monthly");
        assertThat(monthly.systemdDirective()).isEqualTo("OnCalendar=*-*-01 00:00:00");

        var boot = ScheduleParser.parse("boot");
        assertThat(boot.systemdDirective()).isEqualTo("OnBootSec=2min");
        assertThat(boot.isMonotonic()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"every 15m", "15m", "15min", "every 15 minutes"})
    @DisplayName("Monotonic intervals with minutes parse correctly")
    void testMonotonicMinutes(String input) {
        var parsed = ScheduleParser.parse(input);
        assertThat(parsed.isMonotonic()).isTrue();
        assertThat(parsed.systemdDirective()).contains("OnUnitActiveSec=15m");
        assertThat(parsed.systemdDirective()).contains("OnBootSec=1m");
        assertThat(parsed.humanDescription()).isEqualTo("Every 15 minutes");
    }

    @ParameterizedTest
    @ValueSource(strings = {"every 2h", "2h", "2hr", "every 2 hours"})
    @DisplayName("Monotonic intervals with hours parse correctly")
    void testMonotonicHours(String input) {
        var parsed = ScheduleParser.parse(input);
        assertThat(parsed.isMonotonic()).isTrue();
        assertThat(parsed.systemdDirective()).contains("OnUnitActiveSec=2h");
        assertThat(parsed.humanDescription()).isEqualTo("Every 2 hours");
    }

    @ParameterizedTest
    @ValueSource(strings = {"30s", "every 30s", "30sec", "every 30 seconds"})
    @DisplayName("Monotonic intervals with seconds parse correctly")
    void testMonotonicSeconds(String input) {
        var parsed = ScheduleParser.parse(input);
        assertThat(parsed.isMonotonic()).isTrue();
        assertThat(parsed.systemdDirective()).contains("OnUnitActiveSec=30s");
        assertThat(parsed.humanDescription()).isEqualTo("Every 30 seconds");
    }

    @Test
    @DisplayName("Time of day parses to specific daily calendar schedule")
    void testTimeOfDay() {
        var parsed = ScheduleParser.parse("14:30");
        assertThat(parsed.systemdDirective()).isEqualTo("OnCalendar=*-*-* 14:30:00");
        assertThat(parsed.humanDescription()).contains("Daily at 14:30:00");
    }

    @Test
    @DisplayName("Cron syntax translates to systemd OnCalendar")
    void testCronSyntax() {
        var parsed = ScheduleParser.parse("30 4 * * 1");
        assertThat(parsed.systemdDirective()).contains("OnCalendar=");
        assertThat(parsed.humanDescription()).contains("Cron schedule");
    }

    @Test
    @DisplayName("Null or blank schedule defaults to daily")
    void testDefaultSchedule() {
        var parsed = ScheduleParser.parse(null);
        assertThat(parsed.systemdDirective()).isEqualTo("OnCalendar=*-*-* 00:00:00");

        var parsedBlank = ScheduleParser.parse("   ");
        assertThat(parsedBlank.systemdDirective()).isEqualTo("OnCalendar=*-*-* 00:00:00");
    }
}
