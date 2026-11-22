package com.nexus.capabilities.scheduler.timer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request payload for querying scheduled timers by scope.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TimerListRequest(
        TimerScope scope
) {
    public TimerListRequest() {
        this(TimerScope.USER);
    }
}
