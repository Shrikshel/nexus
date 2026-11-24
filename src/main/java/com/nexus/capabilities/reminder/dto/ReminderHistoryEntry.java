package com.nexus.capabilities.reminder.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Historical record of a completed reminder cycle.
 */
public record ReminderHistoryEntry(
        String cycle,
        LocalDate targetDate,
        Instant completedAt,
        List<String> stagesDelivered
) {}
