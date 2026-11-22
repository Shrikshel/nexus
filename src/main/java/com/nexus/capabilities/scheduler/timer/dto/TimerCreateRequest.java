package com.nexus.capabilities.scheduler.timer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request payload for creating a new scheduled timer and associated executable script.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TimerCreateRequest(
        String name,
        String schedule,
        String description,
        TimerScope scope,
        String scriptContent,
        boolean openEditor
) {}
