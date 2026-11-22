package com.nexus.capabilities.scheduler.timer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Result payload containing live status and recent execution logs for a timer.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TimerStatusResult(
        boolean success,
        TimerInfo timer,
        String timerUnitStatus,
        String serviceUnitStatus,
        List<String> recentLogs,
        String rawOutput,
        String message
) {}
