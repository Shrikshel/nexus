package com.nexus.capabilities.storage.zfs.dto;

import java.time.Instant;

/**
 * Detailed status of a ZFS storage pool including scrub status and vdev health tree.
 */
public record ZfsPoolStatusReport(
    boolean installed,
    String poolName,
    String state,
    String statusDetail,
    String scanInfo,
    String configTree,
    String errors,
    String message,
    Instant timestamp
) {
    public static ZfsPoolStatusReport notInstalled() {
        return new ZfsPoolStatusReport(false, "N/A", "UNKNOWN", "N/A", "N/A", "N/A", "None", "ZFS utilities not installed", Instant.now());
    }

    public static ZfsPoolStatusReport empty(String message) {
        return new ZfsPoolStatusReport(true, "N/A", "UNKNOWN", "N/A", "N/A", "N/A", "None", message, Instant.now());
    }
}
