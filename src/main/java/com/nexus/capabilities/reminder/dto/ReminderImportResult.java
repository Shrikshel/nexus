package com.nexus.capabilities.reminder.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Result of an import operation.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReminderImportResult(
        @JsonProperty("success") boolean success,
        @JsonProperty("importedCount") int importedCount,
        @JsonProperty("updatedCount") int updatedCount,
        @JsonProperty("skippedCount") int skippedCount,
        @JsonProperty("totalEvents") int totalEvents,
        @JsonProperty("message") String message
) {}
