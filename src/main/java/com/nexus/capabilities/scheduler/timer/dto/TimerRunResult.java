package com.nexus.capabilities.scheduler.timer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Result payload returned from an on-demand timer execution.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TimerRunResult(
        boolean success,
        String nameOrId,
        int exitCode,
        String output,
        long executionTimeMs,
        String message
) {}
