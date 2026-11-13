package com.nexus.core.security.privilege;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Executes strictly whitelisted administrative operations while preventing arbitrary command injection.
 */
@Component
public class PrivilegeRunner {

    private static final Logger log = LoggerFactory.getLogger(PrivilegeRunner.class);

    private static final Set<String> ALLOWED_BINARIES = Set.of(
            // Smartctl
            "/usr/sbin/smartctl",
            "/usr/bin/smartctl",
            "/usr/local/sbin/smartctl",
            "/usr/local/bin/smartctl",
            "smartctl",

            // Systemctl
            "/bin/systemctl",
            "/usr/bin/systemctl",

            // Tailscale VPN
            "/usr/bin/tailscale",
            "/bin/tailscale",
            "/usr/local/bin/tailscale",
            "/opt/homebrew/bin/tailscale",
            "/Applications/Tailscale.app/Contents/MacOS/Tailscale",
            "C:\\Program Files\\Tailscale\\tailscale.exe",
            "tailscale",
            "tailscale.exe",

            // System Power Management (Reboot, Poweroff, Shutdown)
            "/sbin/reboot",
            "/usr/sbin/reboot",
            "/bin/reboot",
            "/usr/bin/reboot",
            "/sbin/poweroff",
            "/usr/sbin/poweroff",
            "/bin/poweroff",
            "/usr/bin/poweroff",
            "/sbin/shutdown",
            "/usr/sbin/shutdown",
            "C:\\Windows\\System32\\shutdown.exe",
            "shutdown.exe",
            "shutdown",

            // ZFS & ZPool
            "/sbin/zfs",
            "/usr/sbin/zfs",
            "/usr/local/sbin/zfs",
            "zfs",
            "/sbin/zpool",
            "/usr/sbin/zpool",
            "/usr/local/sbin/zpool",
            "zpool",

            // Terminal multiplexer
            "/usr/bin/tmux",
            "/bin/tmux",
            "/usr/local/bin/tmux",
            "/opt/homebrew/bin/tmux",
            "tmux"
    );

    public boolean isRoot() {
        return "root".equals(System.getProperty("user.name"));
    }

    public boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    public ExecutionOutput executeWhitelisted(String binaryPath, List<String> args, long timeoutSeconds) {
        if (!ALLOWED_BINARIES.contains(binaryPath)) {
            throw new SecurityException("Binary execution forbidden: " + binaryPath);
        }

        for (String arg : args) {
            if (arg.contains(";") || arg.contains("&") || arg.contains("|") || arg.contains("`") || arg.contains("$")) {
                throw new SecurityException("Command argument contains unsafe shell characters: " + arg);
            }
        }

        List<String> commandList = new ArrayList<>();
        if (!isWindows() && !isRoot()) {
            commandList.add("sudo");
            commandList.add("-n");
        }
        commandList.add(binaryPath);
        commandList.addAll(args);

        log.debug("Executing privileged command: {}", String.join(" ", commandList));

        try {
            ProcessBuilder pb = new ProcessBuilder(commandList);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new ExecutionOutput(124, output.toString(), true);
            }

            return new ExecutionOutput(process.exitValue(), output.toString(), false);
        } catch (Exception e) {
            log.error("Failed to run privileged command: {}", e.getMessage());
            return new ExecutionOutput(-1, "Execution failed: " + e.getMessage(), false);
        }
    }

    public record ExecutionOutput(int exitCode, String stdout, boolean timedOut) {}
}
