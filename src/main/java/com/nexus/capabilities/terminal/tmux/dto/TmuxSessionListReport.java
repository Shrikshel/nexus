package com.nexus.capabilities.terminal.tmux.dto;

import java.time.Instant;
import java.util.List;

/**
 * Report listing all active tmux sessions managed by the local tmux server.
 */
public record TmuxSessionListReport(
    boolean installed,
    boolean serverRunning,
    int totalSessions,
    List<TmuxSessionInfo> sessions,
    String message,
    Instant timestamp
) {
    public static TmuxSessionListReport notInstalled() {
        return new TmuxSessionListReport(
                false, false, 0, List.of(),
                "tmux binary not found on host system", Instant.now()
        );
    }

    public static TmuxSessionListReport empty(String message) {
        return new TmuxSessionListReport(
                true, false, 0, List.of(),
                message, Instant.now()
        );
    }
}
