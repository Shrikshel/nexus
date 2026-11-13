package com.nexus.capabilities.storage.zfs.dto;

import java.time.Instant;
import java.util.List;

/**
 * Report listing all ZFS datasets configured across all pools.
 */
public record ZfsDatasetListReport(
    boolean installed,
    int totalDatasets,
    List<ZfsDatasetInfo> datasets,
    String message,
    Instant timestamp
) {
    public static ZfsDatasetListReport notInstalled() {
        return new ZfsDatasetListReport(false, 0, List.of(), "ZFS utilities not installed", Instant.now());
    }

    public static ZfsDatasetListReport empty(String message) {
        return new ZfsDatasetListReport(true, 0, List.of(), message, Instant.now());
    }
}
