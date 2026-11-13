package com.nexus.capabilities.process.dto;

import java.time.Instant;
import java.util.List;

/**
 * Report containing active processes.
 */
public record ProcessListReport(
    List<ProcessInfo> processes,
    int totalProcessCount,
    Instant timestamp
) {}
