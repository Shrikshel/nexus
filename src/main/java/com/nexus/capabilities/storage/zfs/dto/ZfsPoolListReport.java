package com.nexus.capabilities.storage.zfs.dto;

import java.time.Instant;
import java.util.List;

/**
 * Report listing all ZFS storage pools configured on the host.
 */
public record ZfsPoolListReport(
    boolean installed,
    int totalPools,
    List<ZfsPoolInfo> pools,
    String message,
    Instant timestamp
) {
    public static ZfsPoolListReport notInstalled() {
        return new ZfsPoolListReport(false, 0, List.of(), "ZFS utilities (zpool/zfs) not found on host", Instant.now());
    }

    public static ZfsPoolListReport empty(String message) {
        return new ZfsPoolListReport(true, 0, List.of(), message, Instant.now());
    }
}
