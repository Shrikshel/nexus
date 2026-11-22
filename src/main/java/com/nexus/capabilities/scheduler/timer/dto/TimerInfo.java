package com.nexus.capabilities.scheduler.timer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Representation of a scheduled task/timer across platform providers.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TimerInfo(
        String id,
        String name,
        String fullIdentifier,
        String schedule,
        String scheduleDetails,
        String description,
        TimerScope scope,
        String scriptPath,
        String unitPath,
        String serviceUnitPath,
        boolean active,
        String nextRun,
        String lastRun,
        String lastResult,
        Instant createdAt
) {}
