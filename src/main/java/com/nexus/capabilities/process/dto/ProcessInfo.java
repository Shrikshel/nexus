package com.nexus.capabilities.process.dto;

import java.time.Instant;
import java.util.List;

/**
 * Individual process metadata.
 */
public record ProcessInfo(
    long pid,
    String user,
    String command,
    String cpuDuration,
    boolean alive
) {}
