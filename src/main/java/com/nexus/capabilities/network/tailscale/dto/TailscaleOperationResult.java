package com.nexus.capabilities.network.tailscale.dto;

import java.time.Instant;

/**
 * Result returned after performing a Tailscale lifecycle operation (up / down).
 */
public record TailscaleOperationResult(
    boolean success,
    String action, // "UP", "DOWN"
    String message,
    int exitCode,
    Instant timestamp
) {}
