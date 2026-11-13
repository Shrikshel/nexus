package com.nexus.platform;

import org.springframework.stereotype.Component;

/**
 * Detects host platform operating system, CPU architecture, and environment.
 */
@Component
public class PlatformDetector {

    private final PlatformOS currentOS;
    private final String osName;
    private final String osVersion;
    private final String osArch;

    public PlatformDetector() {
        this.osName = System.getProperty("os.name", "Unknown");
        this.osVersion = System.getProperty("os.version", "Unknown");
        this.osArch = System.getProperty("os.arch", "Unknown");

        String lowerName = osName.toLowerCase();
        if (lowerName.contains("linux")) {
            this.currentOS = PlatformOS.LINUX;
        } else if (lowerName.contains("mac") || lowerName.contains("darwin")) {
            this.currentOS = PlatformOS.MACOS;
        } else if (lowerName.contains("win")) {
            this.currentOS = PlatformOS.WINDOWS;
        } else {
            this.currentOS = PlatformOS.UNKNOWN;
        }
    }

    public PlatformOS getCurrentOS() {
        return currentOS;
    }

    public String getOsName() {
        return osName;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public String getOsArch() {
        return osArch;
    }

    public boolean isLinux() {
        return currentOS == PlatformOS.LINUX;
    }

    public boolean isWindows() {
        return currentOS == PlatformOS.WINDOWS;
    }

    public boolean isMac() {
        return currentOS == PlatformOS.MACOS;
    }
}
