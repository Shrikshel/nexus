package com.nexus.capabilities.reminder.dto;

import java.time.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for parsing stage descriptors and calculating trigger timestamps.
 */
public final class StageCalculator {

    private static final Pattern STAGE_PATTERN = Pattern.compile("^(\\d+)([dhm])$", Pattern.CASE_INSENSITIVE);

    private StageCalculator() {}

    /**
     * Returns default stage list for a given event type.
     */
    public static List<String> getDefaultStages(ReminderType type) {
        if (type == null) {
            return List.of("7d", "3d", "1d", "1h", "0m");
        }
        return switch (type) {
            case BIRTHDAY -> List.of("7d", "3d", "1d", "1h", "0m");
            case ANNIVERSARY -> List.of("14d", "7d", "3d", "1d", "0m");
            case BILL -> List.of("15d", "7d", "3d", "1d", "0m");
            case MEETING -> List.of("1d", "2h", "15m", "0m");
            case EVENT, CUSTOM -> List.of("7d", "1d", "2h", "0m");
        };
    }

    /**
     * Builds list of ReminderStage instances calculated against a target date, time, and timezone.
     */
    public static List<ReminderStage> buildStages(
            List<String> stageTokens,
            LocalDate targetDate,
            LocalTime targetTime,
            ZoneId zone,
            Map<String, String> customMessages) {

        if (stageTokens == null || stageTokens.isEmpty()) {
            stageTokens = List.of("7d", "1d", "0m");
        }

        ZonedDateTime targetDateTime = ZonedDateTime.of(targetDate, targetTime != null ? targetTime : LocalTime.of(9, 0), zone);
        List<ReminderStage> result = new ArrayList<>();

        for (String rawToken : stageTokens) {
            String token = rawToken.trim().toLowerCase(Locale.ROOT);
            Duration offset = parseDuration(token);
            String isoOffset = formatIsoDuration(offset);
            Instant triggerAt = targetDateTime.minus(offset).toInstant();

            String customMsg = customMessages != null ? customMessages.get(token) : null;
            result.add(new ReminderStage(token, isoOffset, triggerAt, customMsg, null, ReminderStageStatus.PENDING));
        }

        // Sort chronologically by trigger timestamp
        result.sort(Comparator.comparing(ReminderStage::triggerAt));
        return result;
    }

    /**
     * Inspects built stages and marks any past pending stages whose trigger timestamp
     * has already passed as SKIPPED.
     */
    public static List<ReminderStage> filterPastStages(List<ReminderStage> stages, Instant now) {
        if (stages == null) return Collections.emptyList();
        Instant threshold = now != null ? now.minusSeconds(30) : Instant.now().minusSeconds(30);
        List<ReminderStage> result = new ArrayList<>();
        for (ReminderStage s : stages) {
            if (s.status() == ReminderStageStatus.PENDING && s.triggerAt() != null && s.triggerAt().isBefore(threshold)) {
                result.add(s.withStatus(ReminderStageStatus.SKIPPED, null));
            } else {
                result.add(s);
            }
        }
        return result;
    }

    /**
     * Parses tokens like "7d", "3d", "12h", "15m", "0m" into a positive Duration offset.
     */
    public static Duration parseDuration(String token) {
        if (token == null || token.isBlank() || "0m".equalsIgnoreCase(token) || "0".equals(token) || "0d".equalsIgnoreCase(token)) {
            return Duration.ZERO;
        }
        Matcher m = STAGE_PATTERN.matcher(token.trim());
        if (!m.matches()) {
            return Duration.ZERO;
        }
        long amount = Long.parseLong(m.group(1));
        char unit = Character.toLowerCase(m.group(2).charAt(0));

        return switch (unit) {
            case 'd' -> Duration.ofDays(amount);
            case 'h' -> Duration.ofHours(amount);
            case 'm' -> Duration.ofMinutes(amount);
            default -> Duration.ZERO;
        };
    }

    public static String formatIsoDuration(Duration duration) {
        if (duration.isZero()) {
            return "PT0S";
        }
        return "-" + duration.toString();
    }
}
