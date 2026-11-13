package com.nexus.platform;

/**
 * Operating system classification for platform abstractions.
 */
public enum PlatformOS {
    LINUX("Linux"),
    MACOS("macOS"),
    WINDOWS("Windows"),
    UNKNOWN("Unknown");

    private final String displayName;

    PlatformOS(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
