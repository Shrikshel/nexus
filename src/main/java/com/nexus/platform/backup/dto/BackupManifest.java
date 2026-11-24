package com.nexus.platform.backup.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Metadata manifest packaged inside every Nexus backup archive.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BackupManifest(
        int version,
        Instant createdAt,
        String hostname,
        String os,
        String nexusVersion,
        boolean encrypted,
        List<String> scopes,
        Map<String, Object> stats,
        List<BackupFileEntry> files
) {
    public record BackupFileEntry(
            String archivePath,
            String targetPath,
            String scope,
            long sizeBytes,
            String sha256
    ) {}
}
