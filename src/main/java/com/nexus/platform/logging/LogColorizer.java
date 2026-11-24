package com.nexus.platform.logging;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-performance terminal ANSI colorizer for Nexus application and audit logs.
 * Formats timestamps, threads, log levels, logger classes, and critical keywords
 * on-the-fly for terminal display without modifying on-disk plain text log files.
 */
public final class LogColorizer {

    // Standard Logback format: 2026-11-24 07:15:52.152 [thread] LEVEL logger - message
    // Optionally supports systemd journal prefix: "Nov 24 07:15:52 host nexus[123]: ..."
    private static final Pattern APP_LOG_PATTERN = Pattern.compile(
            "^(?:[A-Z][a-z]{2}\\s+\\d+\\s+\\d{2}:\\d{2}:\\d{2}\\s+[^\\s]+\\s+[^:]+:\\s+)?(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,3})?)\\s+(\\[[^\\]]+\\])\\s+([A-Z]+)\\s+([^\\s]+)\\s+-\\s+(.*)$"
    );

    // Audit log format: 2026-11-24 07:15:52.152 message
    private static final Pattern AUDIT_LOG_PATTERN = Pattern.compile(
            "^(?:[A-Z][a-z]{2}\\s+\\d+\\s+\\d{2}:\\d{2}:\\d{2}\\s+[^\\s]+\\s+[^:]+:\\s+)?(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,3})?)\\s+(.*)$"
    );

    // ANSI escape codes
    public static final String RESET = "\u001B[0m";
    public static final String DIM = "\u001B[2m";
    public static final String DARK_GRAY = "\u001B[90m";
    public static final String MAGENTA = "\u001B[35m";
    public static final String CYAN = "\u001B[36m";
    public static final String BLUE = "\u001B[34m";
    public static final String BOLD = "\u001B[1m";

    public static final String LEVEL_ERROR = "\u001B[1;31m"; // Bold Red
    public static final String LEVEL_WARN  = "\u001B[1;33m"; // Bold Yellow
    public static final String LEVEL_INFO  = "\u001B[1;32m"; // Bold Green
    public static final String LEVEL_DEBUG = "\u001B[36m";   // Cyan
    public static final String LEVEL_TRACE = "\u001B[90m";   // Dim Gray

    private LogColorizer() {}

    /**
     * Colorizes a single log line with ANSI terminal styling.
     */
    public static String colorize(String line) {
        if (line == null || line.isEmpty()) {
            return line;
        }

        // 1. Stack traces or indentation
        if (line.startsWith("\tat ")) {
            return DARK_GRAY + "\tat " + RESET + line.substring(4);
        }
        if (line.startsWith("Caused by: ")) {
            return LEVEL_ERROR + "Caused by: " + RESET + line.substring(11);
        }

        // 2. Standard Application Log format
        Matcher appMatcher = APP_LOG_PATTERN.matcher(line);
        if (appMatcher.matches()) {
            String timestamp = appMatcher.group(1);
            String thread = appMatcher.group(2);
            String level = appMatcher.group(3);
            String logger = appMatcher.group(4);
            String msg = appMatcher.group(5);

            String levelColor = getLevelColor(level);
            String formattedLevel = String.format("%-5s", level);

            return DARK_GRAY + timestamp + RESET + " " +
                   MAGENTA + thread + RESET + " " +
                   levelColor + formattedLevel + RESET + " " +
                   BLUE + logger + RESET + " " +
                   DARK_GRAY + "-" + RESET + " " +
                   colorizeKeywords(msg);
        }

        // 3. Audit log format
        Matcher auditMatcher = AUDIT_LOG_PATTERN.matcher(line);
        if (auditMatcher.matches()) {
            String timestamp = auditMatcher.group(1);
            String msg = auditMatcher.group(2);

            return DARK_GRAY + timestamp + RESET + " " + colorizeKeywords(msg);
        }

        // 4. Standalone lines or partial logs: colorize level badges and status keywords
        return colorizeStandalone(line);
    }

    private static String getLevelColor(String level) {
        if (level == null) return RESET;
        return switch (level.trim().toUpperCase()) {
            case "ERROR", "FATAL", "SEVERE" -> LEVEL_ERROR;
            case "WARN", "WARNING" -> LEVEL_WARN;
            case "INFO" -> LEVEL_INFO;
            case "DEBUG" -> LEVEL_DEBUG;
            case "TRACE" -> LEVEL_TRACE;
            default -> RESET;
        };
    }

    private static String colorizeStandalone(String line) {
        String result = line;
        if (result.contains("ERROR")) {
            result = result.replace("ERROR", LEVEL_ERROR + "ERROR" + RESET);
        }
        if (result.contains("WARN")) {
            result = result.replace("WARN", LEVEL_WARN + "WARN" + RESET);
        }
        if (result.contains("INFO")) {
            result = result.replace("INFO", LEVEL_INFO + "INFO" + RESET);
        }
        if (result.contains("DEBUG")) {
            result = result.replace("DEBUG", LEVEL_DEBUG + "DEBUG" + RESET);
        }
        return colorizeKeywords(result);
    }

    private static String colorizeKeywords(String msg) {
        if (msg == null || msg.isEmpty()) {
            return msg;
        }
        return msg
                .replaceAll("\\bSUCCESS\\b", LEVEL_INFO + "SUCCESS" + RESET)
                .replaceAll("\\bACTIVE\\b", LEVEL_INFO + "ACTIVE" + RESET)
                .replaceAll("\\bFAILED\\b", LEVEL_ERROR + "FAILED" + RESET)
                .replaceAll("\\bCRITICAL\\b", LEVEL_ERROR + "CRITICAL" + RESET)
                .replaceAll("\\bDENIED\\b", LEVEL_ERROR + "DENIED" + RESET);
    }
}
