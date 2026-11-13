package com.nexus.capabilities.terminal.tmux.dto;

/**
 * Parameters for verifying and attaching to an existing tmux session.
 */
public record TmuxAttachSessionRequest(
    String sessionName
) {}
