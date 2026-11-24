package com.nexus.capabilities.reminder;

import com.nexus.capabilities.reminder.dto.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StageCalculatorTest {

    private final ZoneId zone = ZoneId.of("Asia/Kolkata");
    private final LocalDate targetDate = LocalDate.of(2026, 12, 1);
    private final LocalTime targetTime = LocalTime.of(10, 0, 0);

    @Test
    @DisplayName("Builds stages with correct chronological ordering and offsets")
    void testBuildStagesChronological() {
        List<String> tokens = List.of("0m", "7d", "1d", "3d", "1h");
        List<ReminderStage> stages = StageCalculator.buildStages(
                tokens, targetDate, targetTime, zone, Map.of("1d", "Custom message 1d")
        );

        assertThat(stages).hasSize(5);
        // Earliest alert should be 7d, then 3d, 1d, 1h, 0m
        assertThat(stages.get(0).stage()).isEqualTo("7d");
        assertThat(stages.get(1).stage()).isEqualTo("3d");
        assertThat(stages.get(2).stage()).isEqualTo("1d");
        assertThat(stages.get(3).stage()).isEqualTo("1h");
        assertThat(stages.get(4).stage()).isEqualTo("0m");

        // Custom message
        assertThat(stages.get(2).customMessage()).isEqualTo("Custom message 1d");
        assertThat(stages.get(0).customMessage()).isNull();

        // Check trigger times
        assertThat(stages.get(0).triggerAt()).isBefore(stages.get(1).triggerAt());
        assertThat(stages.get(1).triggerAt()).isBefore(stages.get(2).triggerAt());
        assertThat(stages.get(2).triggerAt()).isBefore(stages.get(3).triggerAt());
        assertThat(stages.get(3).triggerAt()).isBefore(stages.get(4).triggerAt());
    }

    @Test
    @DisplayName("Provides sensible default stages per reminder type")
    void testDefaultStages() {
        List<String> bdayStages = StageCalculator.getDefaultStages(ReminderType.BIRTHDAY);
        assertThat(bdayStages).containsExactly("7d", "3d", "1d", "1h", "0m");

        List<String> billStages = StageCalculator.getDefaultStages(ReminderType.BILL);
        assertThat(billStages).containsExactly("15d", "7d", "3d", "1d", "0m");

        List<String> meetingStages = StageCalculator.getDefaultStages(ReminderType.MEETING);
        assertThat(meetingStages).containsExactly("1d", "2h", "15m", "0m");
    }

    @Test
    @DisplayName("Handles invalid offset tokens gracefully without crashing by defaulting to zero offset")
    void testInvalidTokensFallback() {
        List<String> tokens = List.of("invalid_token", "5d");
        List<ReminderStage> stages = StageCalculator.buildStages(tokens, targetDate, targetTime, zone, Map.of());

        assertThat(stages).hasSize(2);
        assertThat(stages.get(0).stage()).isEqualTo("5d");
        assertThat(stages.get(1).stage()).isEqualTo("invalid_token");
        assertThat(stages.get(1).offset()).isEqualTo("PT0S");
    }
}
