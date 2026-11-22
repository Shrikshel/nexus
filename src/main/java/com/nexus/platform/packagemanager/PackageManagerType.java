package com.nexus.platform.packagemanager;

public enum PackageManagerType {
    APT("apt-get"),
    DNF("dnf"),
    YUM("yum"),
    PACMAN("pacman"),
    ZYPPER("zypper"),
    APK("apk"),
    BREW("brew"),
    WINGET("winget"),
    CHOCO("choco"),
    UNKNOWN("none");

    private final String binaryName;

    PackageManagerType(String binaryName) {
        this.binaryName = binaryName;
    }

    public String getBinaryName() {
        return binaryName;
    }
}
