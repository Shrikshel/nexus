package com.nexus.capabilities.reminder.dto;

import java.time.LocalDate;
import java.util.Locale;

/**
 * Generalized recurrence policies for automated rollover.
 */
public enum ReminderRecurrence {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY;

    public static ReminderRecurrence fromString(String val) {
        if (val == null || val.isBlank()) {
            return NONE;
        }
        String clean = val.trim().toUpperCase(Locale.ROOT);
        if ("ANNUAL".equals(clean)) {
            return YEARLY;
        }
        try {
            return ReminderRecurrence.valueOf(clean);
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }

    /**
     * Calculates the next occurrence date based on this policy.
     */
    public LocalDate nextOccurrence(LocalDate currentDate) {
        if (currentDate == null) {
            return null;
        }
        return switch (this) {
            case DAILY -> currentDate.plusDays(1);
            case WEEKLY -> currentDate.plusWeeks(1);
            case MONTHLY -> currentDate.plusMonths(1);
            case YEARLY -> currentDate.plusYears(1);
            case NONE -> currentDate;
        };
    }
}
