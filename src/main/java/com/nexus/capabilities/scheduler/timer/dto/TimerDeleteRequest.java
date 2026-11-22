package com.nexus.capabilities.scheduler.timer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request payload to delete an existing timer and optionally its script.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TimerDeleteRequest(
        String nameOrId,
        boolean purgeScript,
        TimerScope scope
) {}
