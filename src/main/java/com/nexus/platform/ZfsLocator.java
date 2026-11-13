package com.nexus.platform;

import java.io.File;
import java.util.List;

/**
 * Utility to discover ZFS administration utilities (zfs and zpool).
 */
public final class ZfsLocator {

    private static final List<String> ZPOOL_CANDIDATE_PATHS = List.of(
            "/usr/sbin/zpool",
            "/sbin/zpool",
            "/usr/local/sbin/zpool",
            "/opt/zfs/bin/zpool"
    );

    private static final List<String> ZFS_CANDIDATE_PATHS = List.of(
            "/usr/sbin/zfs",
            "/sbin/zfs",
            "/usr/local/sbin/zfs",
            "/opt/zfs/bin/zfs"
    );

    private ZfsLocator() {}

    public static String findZpoolBinary() {
        for (String path : ZPOOL_CANDIDATE_PATHS) {
            File f = new File(path);
            if (f.exists() && f.canExecute()) {
                return f.getAbsolutePath();
            }
        }

        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String p : pathEnv.split(File.pathSeparator)) {
                File f = new File(p, "zpool");
                if (f.exists() && f.canExecute()) {
                    return f.getAbsolutePath();
                }
            }
        }

        return "zpool";
    }

    public static String findZfsBinary() {
        for (String path : ZFS_CANDIDATE_PATHS) {
            File f = new File(path);
            if (f.exists() && f.canExecute()) {
                return f.getAbsolutePath();
            }
        }

        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String p : pathEnv.split(File.pathSeparator)) {
                File f = new File(p, "zfs");
                if (f.exists() && f.canExecute()) {
                    return f.getAbsolutePath();
                }
            }
        }

        return "zfs";
    }

    public static boolean isZfsInstalled() {
        for (String path : ZPOOL_CANDIDATE_PATHS) {
            if (new File(path).exists()) {
                return true;
            }
        }
        for (String path : ZFS_CANDIDATE_PATHS) {
            if (new File(path).exists()) {
                return true;
            }
        }

        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String p : pathEnv.split(File.pathSeparator)) {
                if (new File(p, "zpool").exists() || new File(p, "zfs").exists()) {
                    return true;
                }
            }
        }

        return false;
    }
}
