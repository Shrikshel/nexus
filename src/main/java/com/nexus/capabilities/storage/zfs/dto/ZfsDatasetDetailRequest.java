package com.nexus.capabilities.storage.zfs.dto;

import java.util.List;

/**
 * Request to inspect all or specific properties for a particular ZFS dataset.
 */
public record ZfsDatasetDetailRequest(
    String datasetName,
    List<String> properties
) {
    public static ZfsDatasetDetailRequest of(String datasetName) {
        return new ZfsDatasetDetailRequest(datasetName, List.of());
    }
}
