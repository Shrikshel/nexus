package com.nexus.capabilities.storage.zfs.dto;

import java.time.Instant;
import java.util.List;

/**
 * Report listing all ZFS snapshots across datasets.
 */
public record ZfsSnapshotListReport(
    boolean installed,
    int totalSnapshots,
    List<ZfsSnapshotInfo> snapshots,
    String message,
    Instant timestamp
) {
    public static ZfsSnapshotListReport notInstalled() {
        return new ZfsSnapshotListReport(false, 0, List.of(), "ZFS utilities not installed", Instant.now());
    }

    public static ZfsSnapshotListReport empty(String message) {
        return new ZfsSnapshotListReport(true, 0, List.of(), message, Instant.now());
    }
}
