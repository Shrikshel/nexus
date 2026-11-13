package com.nexus.capabilities.storage.dto;

import java.time.Instant;
import java.util.List;

/**
 * Information regarding a filesystem mount or partition.
 */
public record DiskPartitionReport(
    String mountPoint,
    String volumeName,
    String filesystemType,
    long totalBytes,
    long usableBytes,
    long usedBytes,
    double usagePercentage
) {}
