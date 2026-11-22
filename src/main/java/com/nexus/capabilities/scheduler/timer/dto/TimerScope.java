package com.nexus.capabilities.scheduler.timer.dto;

/**
 * Defines the scope under which a timer is registered and executed.
 */
public enum TimerScope {
    /**
     * User-level timer (systemctl --user, ~/Library/LaunchAgents, current Windows user).
     * Requires no root or elevated administrative privileges.
     */
    USER,

    /**
     * System-wide daemon timer (/etc/systemd/system, /Library/LaunchDaemons, Windows SYSTEM).
     * Requires root or elevated administrative privileges.
     */
    SYSTEM
}
