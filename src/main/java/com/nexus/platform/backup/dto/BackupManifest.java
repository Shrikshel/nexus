package com.nexus.platform.backup.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Metadata manifest packaged inside every Nexus backup archive.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BackupManifest(
        @JsonProperty("version") int version,
        @JsonProperty("createdAt") Instant createdAt,
        @JsonProperty("hostname") String hostname,
        @JsonProperty("os") String os,
        @JsonProperty("nexusVersion") String nexusVersion,
        @JsonProperty("encrypted") boolean encrypted,
        @JsonProperty("scopes") List<String> scopes,
        @JsonProperty("stats") Map<String, Object> stats,
        @JsonProperty("files") List<BackupFileEntry> files
) {
    public record BackupFileEntry(
            @JsonProperty("archivePath") String archivePath,
            @JsonProperty("targetPath") String targetPath,
            @JsonProperty("scope") String scope,
            @JsonProperty("sizeBytes") long sizeBytes,
            @JsonProperty("sha256") String sha256
    ) {}
}

