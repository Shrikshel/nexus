package com.nexus.platform.service;

import com.nexus.core.security.privilege.PrivilegeRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Linux implementation of daemon service management via systemd.
 * Supports both User scope (default: systemctl --user) and System scope (systemctl).
 */
@Component
public class LinuxSystemdDaemonService implements DaemonServiceProvider {

    private static final Logger log = LoggerFactory.getLogger(LinuxSystemdDaemonService.class);
    private static final String SERVICE_NAME = "nexus.service";

    private final PrivilegeRunner privilegeRunner;

    public LinuxSystemdDaemonService(PrivilegeRunner privilegeRunner) {
        this.privilegeRunner = privilegeRunner;
    }

    @Override
    public boolean isSupported() {
        return System.getProperty("os.name", "").toLowerCase().contains("linux");
    }

    @Override
    public String getPlatformName() {
        return "systemd";
    }

    public Path resolveUnitPath(boolean systemScope) {
        if (systemScope) {
            return Path.of("/etc/systemd/system", SERVICE_NAME);
        }
        return Path.of(System.getProperty("user.home"), ".config", "systemd", "user", SERVICE_NAME);
    }

    @Override
    public boolean isServiceInstalled(boolean systemScope) {
        return Files.exists(resolveUnitPath(systemScope));
    }

    @Override
    public boolean isServiceEnabled(boolean systemScope) {
        String output = runSystemctlCapture(systemScope, List.of("is-enabled", SERVICE_NAME));
        return "enabled".equalsIgnoreCase(output);
    }

    @Override
    public boolean isServiceRunning(boolean systemScope) {
        String output = runSystemctlCapture(systemScope, List.of("is-active", SERVICE_NAME));
        return "active".equalsIgnoreCase(output);
    }

    @Override
    public ServiceOperationResult installService(boolean systemScope, boolean enable, String binaryPath) {
        Path unitPath = resolveUnitPath(systemScope);
        String wantedBy = systemScope ? "multi-user.target" : "default.target";

        String unitContent = String.format("""
                [Unit]
                Description=Nexus Host Management Agent Daemon
                After=network.target network-online.target
                Wants=network-online.target

                [Service]
                Type=simple
                ExecStart=%s daemon start --foreground
                ExecStop=%s daemon stop
                Restart=always
                RestartSec=5s
                TimeoutStopSec=15s
                StandardOutput=journal
                StandardError=journal

                [Install]
                WantedBy=%s
                """, binaryPath, binaryPath, wantedBy);

        try {
            if (systemScope) {
                // System scope requires elevated rights
                File tmp = File.createTempFile("nexus-svc-", ".service");
                Files.writeString(tmp.toPath(), unitContent, StandardCharsets.UTF_8);
                PrivilegeRunner.ExecutionOutput cpOut = privilegeRunner.executeWhitelisted(
                        "systemctl", List.of(), 5
                );
                // Attempt standard write or fallback via cp
                try {
                    Files.createDirectories(unitPath.getParent());
                    Files.writeString(unitPath, unitContent, StandardCharsets.UTF_8);
                } catch (Exception writeEx) {
                    new ProcessBuilder("sudo", "cp", tmp.getAbsolutePath(), unitPath.toString()).start().waitFor();
                    new ProcessBuilder("sudo", "chmod", "644", unitPath.toString()).start().waitFor();
                }
                tmp.delete();
            } else {
                Files.createDirectories(unitPath.getParent());
                Files.writeString(unitPath, unitContent, StandardCharsets.UTF_8);
            }

            // Reload systemd
            runSystemctl(systemScope, List.of("daemon-reload"));

            // Enable if requested
            if (enable) {
                runSystemctl(systemScope, List.of("enable", SERVICE_NAME));
            }

            String msg = "Service unit successfully installed" + (enable ? " and enabled" : "") + " at " + unitPath;
            return new ServiceOperationResult(true, "INSTALL", SERVICE_NAME, unitPath.toString(), msg);
        } catch (Exception e) {
            log.error("Failed to install systemd service: {}", e.getMessage(), e);
            return new ServiceOperationResult(false, "INSTALL", SERVICE_NAME, unitPath.toString(), "Failed to install unit: " + e.getMessage());
        }
    }

    @Override
    public ServiceOperationResult enableService(boolean systemScope) {
        Path unitPath = resolveUnitPath(systemScope);
        if (!Files.exists(unitPath)) {
            return new ServiceOperationResult(false, "ENABLE", SERVICE_NAME, unitPath.toString(), "Unit file does not exist. Run 'nexus daemon install' first.");
        }
        int code = runSystemctl(systemScope, List.of("enable", SERVICE_NAME));
        boolean ok = code == 0;
        return new ServiceOperationResult(ok, "ENABLE", SERVICE_NAME, unitPath.toString(),
                ok ? "Nexus daemon service enabled for auto-start at boot" : "Failed to enable unit (exit code " + code + ")");
    }

    @Override
    public ServiceOperationResult disableService(boolean systemScope) {
        Path unitPath = resolveUnitPath(systemScope);
        int code = runSystemctl(systemScope, List.of("disable", SERVICE_NAME));
        boolean ok = code == 0;
        return new ServiceOperationResult(ok, "DISABLE", SERVICE_NAME, unitPath.toString(),
                ok ? "Nexus daemon service disabled from auto-start at boot" : "Failed to disable unit (exit code " + code + ")");
    }

    @Override
    public ServiceOperationResult startService(boolean systemScope) {
        Path unitPath = resolveUnitPath(systemScope);
        if (!Files.exists(unitPath)) {
            return new ServiceOperationResult(false, "START", SERVICE_NAME, unitPath.toString(), "Unit file does not exist. Run 'nexus daemon install' first.");
        }
        int code = runSystemctl(systemScope, List.of("start", SERVICE_NAME));
        boolean ok = code == 0;
        return new ServiceOperationResult(ok, "START", SERVICE_NAME, unitPath.toString(),
                ok ? "Started Nexus daemon background service" : "Failed to start unit (exit code " + code + ")");
    }

    @Override
    public ServiceOperationResult stopService(boolean systemScope) {
        Path unitPath = resolveUnitPath(systemScope);
        int code = runSystemctl(systemScope, List.of("stop", SERVICE_NAME));
        boolean ok = code == 0;
        return new ServiceOperationResult(ok, "STOP", SERVICE_NAME, unitPath.toString(),
                ok ? "Stopped Nexus daemon background service" : "Failed to stop unit (exit code " + code + ")");
    }

    @Override
    public ServiceOperationResult uninstallService(boolean systemScope) {
        Path unitPath = resolveUnitPath(systemScope);
        try {
            // Disable and stop
            runSystemctl(systemScope, List.of("disable", "--now", SERVICE_NAME));

            // Remove file
            if (systemScope) {
                try {
                    Files.deleteIfExists(unitPath);
                } catch (Exception e) {
                    new ProcessBuilder("sudo", "rm", "-f", unitPath.toString()).start().waitFor();
                }
            } else {
                Files.deleteIfExists(unitPath);
            }

            // Reload systemd
            runSystemctl(systemScope, List.of("daemon-reload"));

            return new ServiceOperationResult(true, "UNINSTALL", SERVICE_NAME, unitPath.toString(), "Uninstalled service and deleted unit file");
        } catch (Exception e) {
            return new ServiceOperationResult(false, "UNINSTALL", SERVICE_NAME, unitPath.toString(), "Failed to uninstall service: " + e.getMessage());
        }
    }

    @Override
    public ServiceStatusDetails getServiceStatus(boolean systemScope) {
        Path unitPath = resolveUnitPath(systemScope);
        boolean installed = Files.exists(unitPath);
        boolean enabled = installed && isServiceEnabled(systemScope);
        boolean running = installed && isServiceRunning(systemScope);
        String state = running ? "active (running)" : (installed ? "inactive (dead)" : "not-installed");

        return new ServiceStatusDetails(installed, enabled, running, SERVICE_NAME, unitPath.toString(), "systemd", state);
    }

    @Override
    public String getLogGuidance(boolean systemScope) {
        return systemScope ? "journalctl -u nexus -f" : "journalctl --user -u nexus -f";
    }

    private int runSystemctl(boolean systemScope, List<String> args) {
        try {
            List<String> cmd = new ArrayList<>();
            if (systemScope) {
                cmd.add("systemctl");
            } else {
                cmd.add("systemctl");
                cmd.add("--user");
            }
            cmd.addAll(args);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            return proc.waitFor();
        } catch (Exception e) {
            log.debug("systemctl execution failed: {} ({})", args, e.getMessage());
            return -1;
        }
    }

    private String runSystemctlCapture(boolean systemScope, List<String> args) {
        try {
            List<String> cmd = new ArrayList<>();
            if (systemScope) {
                cmd.add("systemctl");
            } else {
                cmd.add("systemctl");
                cmd.add("--user");
            }
            cmd.addAll(args);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            return new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return "";
        }
    }
}
