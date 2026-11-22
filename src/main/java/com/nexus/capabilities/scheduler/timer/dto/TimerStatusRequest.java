package com.nexus.capabilities.scheduler.timer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request payload to inspect live status of a timer.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TimerStatusRequest(
        String nameOrId,
        TimerScope scope
) {}
