package com.nexus.core.security;

/**
 * Granular permissions required to execute capabilities.
 */
public enum Permission {
    READ_SYSTEM("Permission to inspect system and hardware metadata"),
    READ_RESOURCES("Permission to read CPU and memory utilization"),
    READ_STORAGE("Permission to read disk metrics and SMART health"),
    READ_PROCESSES("Permission to list and inspect running processes"),
    WRITE_PROCESSES("Permission to manage and terminate processes or terminal sessions"),
    READ_NETWORK("Permission to inspect network interfaces and sockets"),
    READ_SERVICES("Permission to list and inspect system services"),
    WRITE_SERVICES("Permission to start, stop, or restart system services"),
    WRITE_NETWORK("Permission to configure network settings, Tailscale VPN and routes"),
    POWER_MANAGEMENT("Permission to restart, reboot, or shut down the host machine"),
    EXECUTE_DIAGNOSTICS("Permission to run system diagnostics and log sweeps"),
    ADMIN_ALL("Superuser administrative access across all capabilities");

    private final String description;

    Permission(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
