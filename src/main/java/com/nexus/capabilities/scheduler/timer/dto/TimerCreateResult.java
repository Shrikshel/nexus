package com.nexus.capabilities.scheduler.timer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Result payload returned after attempting timer creation.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TimerCreateResult(
        boolean success,
        TimerInfo timer,
        String scriptPath,
        String unitPath,
        String serviceUnitPath,
        String enableCommand,
        String message
) {}
