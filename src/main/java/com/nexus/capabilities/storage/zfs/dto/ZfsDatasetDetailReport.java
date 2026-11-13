package com.nexus.capabilities.storage.zfs.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Detailed property map and configuration for a specific ZFS dataset.
 */
public record ZfsDatasetDetailReport(
    boolean installed,
    String datasetName,
    String type,
    Map<String, String> properties,
    String message,
    Instant timestamp
) {
    public static ZfsDatasetDetailReport notInstalled(String datasetName) {
        return new ZfsDatasetDetailReport(false, datasetName, "N/A", Map.of(), "ZFS utilities not installed", Instant.now());
    }

    public static ZfsDatasetDetailReport notFound(String datasetName, String message) {
        return new ZfsDatasetDetailReport(true, datasetName, "N/A", Map.of(), message, Instant.now());
    }
}
