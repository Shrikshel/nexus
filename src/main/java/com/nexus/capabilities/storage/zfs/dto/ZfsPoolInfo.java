package com.nexus.capabilities.storage.zfs.dto;

/**
 * High-level metrics for a ZFS storage pool.
 */
public record ZfsPoolInfo(
    String name,
    long sizeBytes,
    long allocatedBytes,
    long freeBytes,
    int fragmentationPct,
    int capacityPct,
    double dedupRatio,
    String healthStatus,
    String altroot
) {}
