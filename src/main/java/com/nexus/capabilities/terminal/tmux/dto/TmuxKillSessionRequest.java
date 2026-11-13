package com.nexus.capabilities.terminal.tmux.dto;

/**
 * Parameters for terminating a tmux session or killing the tmux server.
 */
public record TmuxKillSessionRequest(
    String sessionName,
    boolean killAll
) {
    public static TmuxKillSessionRequest of(String sessionName) {
        return new TmuxKillSessionRequest(sessionName, false);
    }

    public static TmuxKillSessionRequest all() {
        return new TmuxKillSessionRequest(null, true);
    }
}
