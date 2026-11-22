package com.nexus.capabilities.scheduler.timer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request payload to run a timer's underlying task once on demand.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TimerRunRequest(
        String nameOrId,
        TimerScope scope
) {}
