package com.nexus.capabilities.reminder.dto;

import java.util.Locale;

/**
 * Supported reminder event categories.
 */
public enum ReminderType {
    BIRTHDAY,
    ANNIVERSARY,
    BILL,
    EVENT,
    MEETING,
    CUSTOM;

    public static ReminderType fromString(String val) {
        if (val == null || val.isBlank()) {
            return EVENT;
        }
        try {
            return ReminderType.valueOf(val.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return CUSTOM;
        }
    }
}
