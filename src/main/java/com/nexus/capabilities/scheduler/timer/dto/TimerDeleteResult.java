package com.nexus.capabilities.scheduler.timer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Result payload returned after deleting a timer.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TimerDeleteResult(
        boolean success,
        String nameOrId,
        String fullIdentifier,
        boolean deletedTimerUnit,
        boolean deletedServiceUnit,
        boolean deletedScript,
        String message
) {}
