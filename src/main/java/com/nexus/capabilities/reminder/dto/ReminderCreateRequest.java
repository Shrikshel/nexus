package com.nexus.capabilities.reminder.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Request DTO for creating a new reminder event.
 */
public record ReminderCreateRequest(
        String id,
        String title,
        ReminderType type,
        String category,
        LocalDate targetDate,
        LocalTime targetTime,
        ZoneId timezone,
        ReminderRecurrence recurrence,
        List<String> stages,
        Map<String, String> customStageMessages,
        Map<String, Object> metadata
) {}
