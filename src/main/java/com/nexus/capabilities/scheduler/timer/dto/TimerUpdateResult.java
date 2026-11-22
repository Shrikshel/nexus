package com.nexus.capabilities.scheduler.timer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Result payload returned from updating a timer schedule.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TimerUpdateResult(
        boolean success,
        TimerInfo timer,
        String message
) {}
