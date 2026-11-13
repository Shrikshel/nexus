package com.nexus.capabilities.system.dto;

import java.time.Instant;

/**
 * System host information report.
 */
public record SystemInfoReport(
    String hostname,
    String osName,
    String osVersion,
    String osArch,
    String kernelVersion,
    int availableProcessors,
    long totalPhysicalMemoryBytes,
    long freePhysicalMemoryBytes,
    String javaVendor,
    String javaVersion,
    String runtimeType,
    String timezone,
    Instant timestamp
) {}
