package com.nexus.capabilities.reminder.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Active operational state of a reminder event.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReminderEventState(
        @JsonProperty("activeCycle") String activeCycle,
        @JsonProperty("status") String status,
        @JsonProperty("lastDispatchedStage") String lastDispatchedStage,
        @JsonProperty("nextStage") String nextStage,
        @JsonProperty("nextTriggerAt") Instant nextTriggerAt
) {
    public static ReminderEventState initial(String cycle, String nextStage, Instant nextTriggerAt) {
        return new ReminderEventState(cycle, "ACTIVE", null, nextStage, nextTriggerAt);
    }

    public ReminderEventState withProgress(String dispatchedStage, String upcomingStage, Instant upcomingTriggerAt) {
        return new ReminderEventState(activeCycle, status, dispatchedStage, upcomingStage, upcomingTriggerAt);
    }

    public ReminderEventState withNewCycle(String newCycle, String initialStage, Instant initialTriggerAt) {
        return new ReminderEventState(newCycle, "ACTIVE", null, initialStage, initialTriggerAt);
    }

    public ReminderEventState withCompleted() {
        return new ReminderEventState(activeCycle, "COMPLETED", lastDispatchedStage, null, null);
    }
}
