package com.nexus.capabilities.reminder.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

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
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReminderEvent(
        @JsonProperty("uuid") String uuid,
        @JsonProperty("id") String id,
        @JsonProperty("title") String title,
        @JsonProperty("type") ReminderType type,
        @JsonProperty("category") String category,
        @JsonProperty("targetDate") LocalDate targetDate,
        @JsonProperty("targetTime") LocalTime targetTime,
        @JsonProperty("timezone") ZoneId timezone,
        @JsonProperty("recurrence") ReminderRecurrence recurrence,
        @JsonProperty("metadata") Map<String, Object> metadata,
        @JsonProperty("stages") List<ReminderStage> stages,
        @JsonProperty("state") ReminderEventState state,
        @JsonProperty("history") List<ReminderHistoryEntry> history
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
