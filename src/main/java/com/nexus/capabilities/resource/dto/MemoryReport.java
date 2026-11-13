package com.nexus.capabilities.resource.dto;

import java.time.Instant;

/**
 * Host memory and swap space utilization report.
 */
public record MemoryReport(
    long totalPhysicalMemoryBytes,
    long freePhysicalMemoryBytes,
    long usedPhysicalMemoryBytes,
    double memoryUsagePercentage,
    long totalSwapBytes,
    long freeSwapBytes,
    Instant timestamp
) {}
