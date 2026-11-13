package com.nexus.platform;

import java.io.File;
import java.util.List;

/**
 * Utility to discover the tmux binary across standard locations and system PATH.
 */
public final class TmuxLocator {

    private static final List<String> CANDIDATE_PATHS = List.of(
            "/usr/bin/tmux",
            "/bin/tmux",
            "/usr/local/bin/tmux",
            "/opt/homebrew/bin/tmux",
            "/usr/pkg/bin/tmux"
    );

    private TmuxLocator() {}

    public static String findTmuxBinary() {
        for (String path : CANDIDATE_PATHS) {
            File f = new File(path);
            if (f.exists() && f.canExecute()) {
                return f.getAbsolutePath();
            }
        }

        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String p : pathEnv.split(File.pathSeparator)) {
                File f = new File(p, "tmux");
                if (f.exists() && f.canExecute()) {
                    return f.getAbsolutePath();
                }
            }
        }

        return "tmux";
    }

    public static boolean isTmuxInstalled() {
        for (String path : CANDIDATE_PATHS) {
            File f = new File(path);
            if (f.exists()) {
                return true;
            }
        }

        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String p : pathEnv.split(File.pathSeparator)) {
                if (new File(p, "tmux").exists()) {
                    return true;
                }
            }
        }

        return false;
    }
}
