package com.nexus.capabilities.reminder.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Represents a single notification milestone (e.g. 7d, 3d, 1d, 1h, 0m) within an event cycle.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReminderStage(
        @JsonProperty("stage") String stage,
        @JsonProperty("offset") String offset,
        @JsonProperty("triggerAt") Instant triggerAt,
        @JsonProperty("customMessage") String customMessage,
        @JsonProperty("deliveredAt") Instant deliveredAt,
        @JsonProperty("status") ReminderStageStatus status
) {
    public ReminderStage withStatus(ReminderStageStatus newStatus, Instant delivered) {
        return new ReminderStage(stage, offset, triggerAt, customMessage, delivered, newStatus);
    }

    public ReminderStage withTriggerAt(Instant newTriggerAt) {
        return new ReminderStage(stage, offset, newTriggerAt, customMessage, null, ReminderStageStatus.PENDING);
    }
}
