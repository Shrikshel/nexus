package com.nexus.platform.backup.dto;

import java.util.List;

public record RestoreResult(
        boolean success,
        boolean dryRun,
        int filesRestored,
        int filesSkipped,
        int timersRearmed,
        int remindersSynced,
        List<RestoredFileAction> actions,
        String message
) {
    public record RestoredFileAction(
            String archivePath,
            String targetPath,
            long sizeBytes,
            boolean alreadyExisted,
            String actionTaken
    ) {}
}
