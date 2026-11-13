package com.nexus.capabilities.terminal.tmux.dto;

/**
 * Parameters for creating a new detached tmux session.
 */
public record TmuxNewSessionRequest(
    String sessionName,
    String startDirectory,
    String windowName,
    String command
) {
    public static TmuxNewSessionRequest of(String sessionName) {
        return new TmuxNewSessionRequest(sessionName, null, null, null);
    }
}
