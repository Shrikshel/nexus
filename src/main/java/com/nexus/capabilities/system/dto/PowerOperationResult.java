package com.nexus.capabilities.system.dto;

import java.time.Instant;

/**
 * Result returned after initiating a host reboot or poweroff sequence.
 */
public record PowerOperationResult(
    boolean success,
    String action, // "REBOOT", "POWEROFF"
    String message,
    int delaySeconds,
    Instant timestamp
) {}
