package com.nexus.capabilities.terminal.tmux.dto;

import java.time.Instant;

/**
 * Details of an individual tmux session.
 */
public record TmuxSessionInfo(
    String name,
    int windows,
    Instant created,
    boolean attached,
    String workingDirectory
) {}
