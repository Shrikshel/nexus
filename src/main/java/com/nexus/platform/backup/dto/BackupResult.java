package com.nexus.platform.backup.dto;

import java.nio.file.Path;
import java.util.Locale;

public record BackupResult(
        boolean success,
        Path archivePath,
        long archiveSizeBytes,
        int totalFiles,
        boolean encrypted,
        BackupManifest manifest,
        String message
) {
    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.ROOT, "%.2f KB", kb);
        double mb = kb / 1024.0;
        return String.format(Locale.ROOT, "%.2f MB", mb);
    }
}

