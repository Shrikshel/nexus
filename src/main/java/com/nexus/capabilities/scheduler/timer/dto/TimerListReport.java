package com.nexus.capabilities.scheduler.timer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Report containing list of configured timers and execution telemetry.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TimerListReport(
        List<TimerInfo> timers,
        int count,
        TimerScope scope,
        Instant timestamp
) {}
