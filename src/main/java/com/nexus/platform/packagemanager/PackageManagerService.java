package com.nexus.platform.packagemanager;

import com.nexus.capabilities.system.package_mgr.dto.PackageOperationResult;
import com.nexus.core.security.privilege.PrivilegeRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Cross-platform package management abstraction supporting APT, DNF, YUM, PACMAN,
 * ZYPPER, APK, Homebrew, and Winget/Chocolatey.
 */
@Service
public class PackageManagerService {

    private static final Logger log = LoggerFactory.getLogger(PackageManagerService.class);

    private final PrivilegeRunner privilegeRunner;
    private PackageManagerType cachedType;
    private String cachedBinary;

    public PackageManagerService(PrivilegeRunner privilegeRunner) {
        this.privilegeRunner = privilegeRunner;
    }

    public synchronized PackageManagerType getDetectedType() {
        if (cachedType == null) {
            detect();
        }
        return cachedType;
    }

    public synchronized String getDetectedBinary() {
        if (cachedBinary == null) {
            detect();
        }
        return cachedBinary;
    }

    private void detect() {
        // 1. Linux package managers
        String[] candidates = new String[]{
                "/usr/bin/apt-get", "/bin/apt-get",
                "/usr/bin/dnf", "/bin/dnf",
                "/usr/bin/pacman", "/bin/pacman",
                "/usr/bin/zypper", "/bin/zypper",
                "/sbin/apk", "/usr/bin/apk",
                "/usr/bin/yum", "/bin/yum"
        };

        for (String path : candidates) {
            if (new File(path).canExecute()) {
                cachedBinary = path;
                cachedType = mapPathToType(path);
                log.debug("Detected system package manager: {} ({})", cachedType, cachedBinary);
                return;
            }
        }

        // 2. macOS Homebrew
        for (String brewPath : new String[]{"/opt/homebrew/bin/brew", "/usr/local/bin/brew"}) {
            if (new File(brewPath).canExecute()) {
                cachedBinary = brewPath;
                cachedType = PackageManagerType.BREW;
                log.debug("Detected macOS Homebrew at: {}", cachedBinary);
                return;
            }
        }

        // 3. Fallback path search using which / where
        for (PackageManagerType type : PackageManagerType.values()) {
            if (type == PackageManagerType.UNKNOWN) continue;
            String bin = findInPath(type.getBinaryName());
            if (bin != null) {
                cachedBinary = bin;
                cachedType = type;
                log.debug("Detected package manager in PATH: {} ({})", cachedType, cachedBinary);
                return;
            }
        }

        cachedType = PackageManagerType.UNKNOWN;
        cachedBinary = null;
        log.warn("No supported system package manager detected on this host");
    }

    private PackageManagerType mapPathToType(String path) {
        String lower = path.toLowerCase();
        if (lower.contains("apt-get") || lower.contains("/apt")) return PackageManagerType.APT;
        if (lower.contains("dnf")) return PackageManagerType.DNF;
        if (lower.contains("pacman")) return PackageManagerType.PACMAN;
        if (lower.contains("zypper")) return PackageManagerType.ZYPPER;
        if (lower.contains("apk")) return PackageManagerType.APK;
        if (lower.contains("yum")) return PackageManagerType.YUM;
        if (lower.contains("brew")) return PackageManagerType.BREW;
        if (lower.contains("winget")) return PackageManagerType.WINGET;
        if (lower.contains("choco")) return PackageManagerType.CHOCO;
        return PackageManagerType.UNKNOWN;
    }

    private String findInPath(String binaryName) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return null;
        String[] dirs = pathEnv.split(File.pathSeparator);
        for (String dir : dirs) {
            File f = new File(dir, binaryName);
            if (f.canExecute()) {
                return f.getAbsolutePath();
            }
            if (privilegeRunner.isWindows()) {
                File fExe = new File(dir, binaryName + ".exe");
                if (fExe.canExecute()) {
                    return fExe.getAbsolutePath();
                }
            }
        }
        return null;
    }

    public PackageOperationResult update() {
        PackageManagerType type = getDetectedType();
        String binary = getDetectedBinary();

        if (type == PackageManagerType.UNKNOWN || binary == null) {
            return new PackageOperationResult(
                    false, "UPDATE", "NONE", -1,
                    "No supported package manager found on host (apt, dnf, pacman, zypper, apk, brew, winget)",
                    "", Instant.now()
            );
        }

        List<String> args = buildUpdateArgs(type);
        PrivilegeRunner.ExecutionOutput output = privilegeRunner.executeWhitelisted(binary, args, 300);

        // For dnf / check-update, exit code 100 means updates are available (which is success)
        boolean success = output.exitCode() == 0 || (type == PackageManagerType.DNF && output.exitCode() == 100);
        String summary = success ? "Package repositories updated successfully"
                : "Package update failed with exit code " + output.exitCode();

        return new PackageOperationResult(
                success, "UPDATE", type.name(), output.exitCode(), summary, output.stdout(), Instant.now()
        );
    }

    public PackageOperationResult upgrade(boolean assumeYes, boolean dryRun) {
        PackageManagerType type = getDetectedType();
        String binary = getDetectedBinary();

        if (type == PackageManagerType.UNKNOWN || binary == null) {
            return new PackageOperationResult(
                    false, "UPGRADE", "NONE", -1,
                    "No supported package manager found on host",
                    "", Instant.now()
            );
        }

        List<String> args = buildUpgradeArgs(type, assumeYes, dryRun);
        PrivilegeRunner.ExecutionOutput output = privilegeRunner.executeWhitelisted(binary, args, 600);

        boolean success = output.exitCode() == 0;
        String summary = success ? "Package upgrade completed successfully"
                : "Package upgrade failed with exit code " + output.exitCode();

        return new PackageOperationResult(
                success, "UPGRADE", type.name(), output.exitCode(), summary, output.stdout(), Instant.now()
        );
    }

    public PackageOperationResult updateUpgrade(boolean assumeYes, boolean dryRun) {
        PackageOperationResult updateRes = update();
        if (!updateRes.success()) {
            return new PackageOperationResult(
                    false, "UPDATE-UPGRADE", updateRes.packageManager(), updateRes.exitCode(),
                    "Update phase failed: " + updateRes.message(), updateRes.output(), Instant.now()
            );
        }

        PackageOperationResult upgradeRes = upgrade(assumeYes, dryRun);
        String combinedOutput = "=== Update Output ===\n" + updateRes.output() + "\n=== Upgrade Output ===\n" + upgradeRes.output();

        return new PackageOperationResult(
                upgradeRes.success(), "UPDATE-UPGRADE", upgradeRes.packageManager(), upgradeRes.exitCode(),
                upgradeRes.message(), combinedOutput, Instant.now()
        );
    }

    private List<String> buildUpdateArgs(PackageManagerType type) {
        List<String> args = new ArrayList<>();
        switch (type) {
            case APT -> args.add("update");
            case DNF, YUM -> args.add("check-update");
            case PACMAN -> args.add("-Sy");
            case ZYPPER -> args.add("refresh");
            case APK -> args.add("update");
            case BREW -> args.add("update");
            case WINGET -> args.add("update");
            case CHOCO -> args.add("outdated");
            default -> args.add("update");
        }
        return args;
    }

    private List<String> buildUpgradeArgs(PackageManagerType type, boolean assumeYes, boolean dryRun) {
        List<String> args = new ArrayList<>();
        switch (type) {
            case APT -> {
                args.add("upgrade");
                if (assumeYes) args.add("-y");
                if (dryRun) args.add("-s");
            }
            case DNF -> {
                args.add("upgrade");
                if (assumeYes) args.add("-y");
            }
            case YUM -> {
                args.add("update");
                if (assumeYes) args.add("-y");
            }
            case PACMAN -> {
                args.add("-Su");
                if (assumeYes) args.add("--noconfirm");
            }
            case ZYPPER -> {
                args.add("update");
                if (assumeYes) args.add("-y");
                if (dryRun) args.add("--dry-run");
            }
            case APK -> args.add("upgrade");
            case BREW -> args.add("upgrade");
            case WINGET -> {
                args.add("upgrade");
                args.add("--all");
                args.add("--include-unknown");
            }
            case CHOCO -> {
                args.add("upgrade");
                args.add("all");
                if (assumeYes) args.add("-y");
            }
            default -> args.add("upgrade");
        }
        return args;
    }
}
