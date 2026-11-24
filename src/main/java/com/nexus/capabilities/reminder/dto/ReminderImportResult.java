package com.nexus.capabilities.reminder.dto;

/**
 * Result of an import operation.
 */
public record ReminderImportResult(
        boolean success,
        int importedCount,
        int updatedCount,
        int skippedCount,
        int totalEvents,
        String message
) {}
