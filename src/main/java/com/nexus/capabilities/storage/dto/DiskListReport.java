package com.nexus.capabilities.storage.dto;

import java.time.Instant;
import java.util.List;

/**
 * Summary of all mounted filesystems and aggregate storage statistics.
 */
public record DiskListReport(
    List<DiskPartitionReport> partitions,
    long aggregateTotalBytes,
    long aggregateUsableBytes,
    double aggregateUsagePercentage,
    Instant timestamp
) {}
