package com.nexus.platform.scheduler;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses user schedule strings (friendly presets, monotonic intervals, calendar/cron specs)
 * into native platform scheduler directives for systemd, launchd, and Windows Task Scheduler.
 */
public final class ScheduleParser {

    public record ParsedSchedule(
            String raw,
            String systemdDirective,
            boolean isMonotonic,
            String humanDescription,
            String launchdDirective,
            String windowsScheduleArgs
    ) {}

    private static final Pattern MONOTONIC_PATTERN = Pattern.compile("^(?:every\\s+)?(\\d+)\\s*(s|sec|seconds?|m|min|minutes?|h|hr|hours?|d|days?)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME_OF_DAY_PATTERN = Pattern.compile("^(\\d{1,2}):(\\d{2})(?::(\\d{2}))?$", Pattern.CASE_INSENSITIVE);

    public static ParsedSchedule parse(String input) {
        if (input == null || input.isBlank()) {
            input = "daily";
        }
        String clean = input.trim().toLowerCase(Locale.ROOT);

        // 1. Friendly Presets
        switch (clean) {
            case "hourly":
            case "@hourly":
                return new ParsedSchedule(
                        input,
                        "OnCalendar=hourly",
                        false,
                        "Every hour at :00",
                        "Hour: 0",
                        "/SC HOURLY"
                );
            case "daily":
            case "midnight":
            case "@daily":
            case "@midnight":
                return new ParsedSchedule(
                        input,
                        "OnCalendar=*-*-* 00:00:00",
                        false,
                        "Every day at 00:00:00",
                        "Hour: 0, Minute: 0",
                        "/SC DAILY /ST 00:00"
                );
            case "weekly":
            case "@weekly":
                return new ParsedSchedule(
                        input,
                        "OnCalendar=Mon *-*-* 00:00:00",
                        false,
                        "Every Monday at 00:00:00",
                        "Weekday: 1, Hour: 0, Minute: 0",
                        "/SC WEEKLY /D MON /ST 00:00"
                );
            case "twice-weekly":
            case "biweekly":
                return new ParsedSchedule(
                        input,
                        "OnCalendar=Mon,Thu *-*-* 00:00:00",
                        false,
                        "Every Monday and Thursday at 00:00:00",
                        "Weekday: 1,4, Hour: 0, Minute: 0",
                        "/SC WEEKLY /D MON,THU /ST 00:00"
                );
            case "monthly":
            case "@monthly":
                return new ParsedSchedule(
                        input,
                        "OnCalendar=*-*-01 00:00:00",
                        false,
                        "1st day of every month at 00:00:00",
                        "Day: 1, Hour: 0, Minute: 0",
                        "/SC MONTHLY /D 1 /ST 00:00"
                );
            case "boot":
            case "reboot":
            case "@reboot":
                return new ParsedSchedule(
                        input,
                        "OnBootSec=2min",
                        true,
                        "2 minutes after system boot",
                        "RunAtLoad = true",
                        "/SC ONSTART"
                );
        }

        // 2. Custom Monotonic Intervals (e.g. "every 15m", "every 2h", "30s", "10min")
        Matcher m = MONOTONIC_PATTERN.matcher(clean);
        if (m.matches()) {
            int amount = Integer.parseInt(m.group(1));
            String unitRaw = m.group(2).toLowerCase(Locale.ROOT);
            String systemdUnit;
            String humanUnit;
            String winArgs;

            if (unitRaw.startsWith("s")) {
                systemdUnit = amount + "s";
                humanUnit = amount + " seconds";
                winArgs = "/SC MINUTE /MO 1"; // Windows Task Scheduler min interval is 1 minute
            } else if (unitRaw.startsWith("m")) {
                systemdUnit = amount + "m";
                humanUnit = amount + " minutes";
                winArgs = "/SC MINUTE /MO " + amount;
            } else if (unitRaw.startsWith("h")) {
                systemdUnit = amount + "h";
                humanUnit = amount + " hours";
                winArgs = "/SC HOURLY /MO " + amount;
            } else {
                systemdUnit = amount + "d";
                humanUnit = amount + " days";
                winArgs = "/SC DAILY /MO " + amount;
            }

            return new ParsedSchedule(
                    input,
                    "OnBootSec=1m\nOnUnitActiveSec=" + systemdUnit,
                    true,
                    "Every " + humanUnit,
                    "StartInterval = " + toSeconds(amount, unitRaw),
                    winArgs
            );
        }

        // 3. Time of day (e.g. "03:30", "14:00")
        Matcher timeMatch = TIME_OF_DAY_PATTERN.matcher(clean);
        if (timeMatch.matches()) {
            int hr = Integer.parseInt(timeMatch.group(1));
            int min = Integer.parseInt(timeMatch.group(2));
            int sec = timeMatch.group(3) != null ? Integer.parseInt(timeMatch.group(3)) : 0;
            String formattedTime = String.format("%02d:%02d:%02d", hr, min, sec);
            return new ParsedSchedule(
                    input,
                    "OnCalendar=*-*-* " + formattedTime,
                    false,
                    "Daily at " + formattedTime,
                    "Hour: " + hr + ", Minute: " + min,
                    String.format("/SC DAILY /ST %02d:%02d", hr, min)
            );
        }

        // 4. Cron expression (5 space-separated fields)
        String[] cronParts = clean.split("\\s+");
        if (cronParts.length == 5) {
            return new ParsedSchedule(
                    input,
                    "OnCalendar=" + input,
                    false,
                    "Cron schedule: " + input,
                    "Cron: " + input,
                    "/SC DAILY"
            );
        }

        // 5. Fallback to raw calendar specification (e.g. systemd OnCalendar expression)
        String calDirective = input.startsWith("OnCalendar=") ? input : "OnCalendar=" + input;
        return new ParsedSchedule(
                input,
                calDirective,
                false,
                "Custom: " + input,
                "Custom",
                "/SC DAILY"
        );
    }

    private static int toSeconds(int amount, String unit) {
        if (unit.startsWith("s")) return amount;
        if (unit.startsWith("m")) return amount * 60;
        if (unit.startsWith("h")) return amount * 3600;
        return amount * 86400;
    }
}
