package com.nexus.capabilities.scheduler.timer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request payload to update an existing timer's schedule or metadata.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TimerUpdateRequest(
        String nameOrId,
        String schedule,
        String description,
        TimerScope scope
) {}
