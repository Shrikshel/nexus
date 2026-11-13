package com.nexus.platform;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Utility methods for formatting durations, timestamps, and host time representations.
 */
public final class TimeUtils {

    private static final DateTimeFormatter LOCAL_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    private TimeUtils() {}

    public static String formatDuration(long seconds) {
        if (seconds < 0) {
            return "0s";
        }
        Duration d = Duration.ofSeconds(seconds);
        long days = d.toDays();
        long hours = d.toHoursPart();
        long minutes = d.toMinutesPart();
        long secs = d.toSecondsPart();

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0 || days > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0 || hours > 0 || days > 0) {
            sb.append(minutes).append("m ");
        }
        sb.append(secs).append("s");
        return sb.toString().trim();
    }

    public static String formatTimestamp(Instant instant) {
        if (instant == null) {
            return "N/A";
        }
        return LOCAL_FORMATTER.withZone(HostTimezoneResolver.getHostZoneId()).format(instant);
    }

    public static String formatIsoLocal(Instant instant) {
        if (instant == null) {
            return "N/A";
        }
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(HostTimezoneResolver.getHostZoneId()).format(instant);
    }
}
