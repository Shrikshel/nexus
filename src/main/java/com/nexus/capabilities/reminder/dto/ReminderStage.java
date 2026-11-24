package com.nexus.capabilities.reminder.dto;

import java.time.Instant;

/**
 * Represents a single notification milestone (e.g. 7d, 3d, 1d, 1h, 0m) within an event cycle.
 */
public record ReminderStage(
        String stage,
        String offset,
        Instant triggerAt,
        String customMessage,
        Instant deliveredAt,
        ReminderStageStatus status
) {
    public ReminderStage withStatus(ReminderStageStatus newStatus, Instant delivered) {
        return new ReminderStage(stage, offset, triggerAt, customMessage, delivered, newStatus);
    }

    public ReminderStage withTriggerAt(Instant newTriggerAt) {
        return new ReminderStage(stage, offset, newTriggerAt, customMessage, null, ReminderStageStatus.PENDING);
    }
}
