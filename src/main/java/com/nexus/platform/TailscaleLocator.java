package com.nexus.platform;

import java.io.File;
import java.util.List;

/**
 * Utility to discover the Tailscale CLI binary across Linux, macOS, and Windows.
 */
public final class TailscaleLocator {

    private static final List<String> CANDIDATE_PATHS = List.of(
            "/usr/bin/tailscale",
            "/bin/tailscale",
            "/usr/local/bin/tailscale",
            "/opt/homebrew/bin/tailscale",
            "/Applications/Tailscale.app/Contents/MacOS/Tailscale",
            "C:\\Program Files\\Tailscale\\tailscale.exe",
            "C:\\Program Files (x86)\\Tailscale\\tailscale.exe"
    );

    private TailscaleLocator() {}

    /**
     * Resolves the path to the tailscale binary, or returns "tailscale" as fallback if in PATH.
     */
    public static String findTailscaleBinary() {
        for (String path : CANDIDATE_PATHS) {
            File f = new File(path);
            if (f.exists() && f.canExecute()) {
                return f.getAbsolutePath();
            }
        }
        // Fallback to searching PATH or default name
        boolean isWin = System.getProperty("os.name", "").toLowerCase().contains("win");
        return isWin ? "tailscale.exe" : "/usr/bin/tailscale";
    }

    public static boolean isTailscaleInstalled() {
        for (String path : CANDIDATE_PATHS) {
            File f = new File(path);
            if (f.exists()) {
                return true;
            }
        }
        // Also check PATH
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            String binName = System.getProperty("os.name", "").toLowerCase().contains("win") ? "tailscale.exe" : "tailscale";
            for (String p : pathEnv.split(File.pathSeparator)) {
                if (new File(p, binName).exists()) {
                    return true;
                }
            }
        }
        return false;
    }
}
