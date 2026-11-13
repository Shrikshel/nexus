package com.nexus.capabilities.resource.dto;

import java.time.Instant;

/**
 * CPU utilization metrics and system load average report.
 */
public record CpuReport(
    int logicalCores,
    double systemLoadAverage1m,
    double systemLoadAverage5m,
    double systemLoadAverage15m,
    double cpuUsagePercentage,
    Instant timestamp
) {}
