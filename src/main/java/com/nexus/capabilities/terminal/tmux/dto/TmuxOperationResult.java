package com.nexus.capabilities.terminal.tmux.dto;

import java.time.Instant;

/**
 * Result returned after performing a tmux session operation (new, kill, attach).
 */
public record TmuxOperationResult(
    boolean success,
    String action, // "NEW", "KILL", "ATTACH"
    String sessionName,
    String message,
    int exitCode,
    Instant timestamp
) {}
