package com.nexus.capabilities.reminder.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Complete domain entity for an event reminder.
 * Contains both an internal immutable UUIDv4 and a human-friendly natural ID (e.g. EVT-001).
 */
public record ReminderEvent(
        String uuid,
        String id,
        String title,
        ReminderType type,
        String category,
        LocalDate targetDate,
        LocalTime targetTime,
        ZoneId timezone,
        ReminderRecurrence recurrence,
        Map<String, Object> metadata,
        List<ReminderStage> stages,
        ReminderEventState state,
        List<ReminderHistoryEntry> history
) {
    public ReminderEvent {
        if (metadata == null) metadata = Collections.emptyMap();
        if (stages == null) stages = Collections.emptyList();
        if (history == null) history = Collections.emptyList();
    }

    public ReminderEvent withTargetDateAndStages(LocalDate newDate, List<ReminderStage> newStages, ReminderEventState newState) {
        return new ReminderEvent(
                uuid, id, title, type, category, newDate, targetTime, timezone, recurrence, metadata, newStages, newState, history
        );
    }

    public ReminderEvent withUpdatedState(ReminderEventState newState, List<ReminderStage> newStages) {
        return new ReminderEvent(
                uuid, id, title, type, category, targetDate, targetTime, timezone, recurrence, metadata, newStages, newState, history
        );
    }

    public ReminderEvent withArchivedCycle(ReminderHistoryEntry entry, LocalDate nextDate, List<ReminderStage> nextStages, ReminderEventState nextState) {
        List<ReminderHistoryEntry> updatedHistory = new java.util.ArrayList<>(history);
        updatedHistory.add(entry);
        return new ReminderEvent(
                uuid, id, title, type, category, nextDate, targetTime, timezone, recurrence, metadata, nextStages, nextState, updatedHistory
        );
    }
}
