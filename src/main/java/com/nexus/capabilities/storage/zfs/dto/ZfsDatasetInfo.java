package com.nexus.capabilities.storage.zfs.dto;

/**
 * Metric summary for an individual ZFS filesystem or volume dataset.
 */
public record ZfsDatasetInfo(
    String name,
    String type, // "filesystem", "volume"
    long usedBytes,
    long availableBytes,
    long referencedBytes,
    String mountpoint
) {}
