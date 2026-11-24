package com.nexus.capabilities.reminder.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Request DTO for modifying an existing reminder event.
 */
public record ReminderUpdateRequest(
        String targetIdentifier, // Can be friendly ID (EVT-001) or UUID
        String newTitle,
        ReminderType newType,
        String newCategory,
        LocalDate newTargetDate,
        LocalTime newTargetTime,
        ZoneId newTimezone,
        ReminderRecurrence newRecurrence,
        List<String> newStages,
        Map<String, String> updateStageMessages,
        Map<String, Object> updateMetadata
) {}
