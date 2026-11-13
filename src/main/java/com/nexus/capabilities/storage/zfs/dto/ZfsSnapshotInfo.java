package com.nexus.capabilities.storage.zfs.dto;

/**
 * Metric summary for an individual ZFS snapshot.
 */
public record ZfsSnapshotInfo(
    String name,
    String dataset,
    String snapshotTag,
    long usedBytes,
    long referencedBytes,
    String creationTime
) {}
