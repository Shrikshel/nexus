package com.nexus.capabilities.reminder.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Historical record of a completed reminder cycle.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReminderHistoryEntry(
        @JsonProperty("cycle") String cycle,
        @JsonProperty("targetDate") LocalDate targetDate,
        @JsonProperty("completedAt") Instant completedAt,
        @JsonProperty("stagesDelivered") List<String> stagesDelivered
) {}
