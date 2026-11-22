package com.nexus.platform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * macOS implementation of daemon service management via launchd Property List agents.
 */
@Component
public class MacLaunchdDaemonService implements DaemonServiceProvider {

    private static final Logger log = LoggerFactory.getLogger(MacLaunchdDaemonService.class);
    private static final String LABEL = "com.nexus.daemon";

    @Override
    public boolean isSupported() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }

    @Override
    public String getPlatformName() {
        return "launchd";
    }

    public Path resolvePlistPath(boolean systemScope) {
        if (systemScope) {
            return Path.of("/Library/LaunchDaemons", LABEL + ".plist");
        }
        return Path.of(System.getProperty("user.home"), "Library", "LaunchAgents", LABEL + ".plist");
    }

    @Override
    public boolean isServiceInstalled(boolean systemScope) {
        return Files.exists(resolvePlistPath(systemScope));
    }

    @Override
    public boolean isServiceEnabled(boolean systemScope) {
        return isServiceInstalled(systemScope);
    }

    @Override
    public boolean isServiceRunning(boolean systemScope) {
        String output = runLaunchctlCapture(List.of("list", LABEL));
        return !output.isBlank() && !output.contains("Could not find service");
    }

    @Override
    public ServiceOperationResult installService(boolean systemScope, boolean enable, String binaryPath) {
        Path plistPath = resolvePlistPath(systemScope);
        Path logDir = com.nexus.platform.logging.NexusLogManager.getLogDirectory();
        Path stdoutLog = logDir.resolve("nexus-stdout.log");
        Path stderrLog = logDir.resolve("nexus-stderr.log");

        String plistContent = String.format("""
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                <plist version="1.0">
                <dict>
                    <key>Label</key>
                    <string>%s</string>
                    <key>ProgramArguments</key>
                    <array>
                        <string>%s</string>
                        <string>daemon</string>
                        <string>start</string>
                        <string>--foreground</string>
                    </array>
                    <key>RunAtLoad</key>
                    <%s/>
                    <key>KeepAlive</key>
                    <true/>
                    <key>StandardOutPath</key>
                    <string>%s</string>
                    <key>StandardErrorPath</key>
                    <string>%s</string>
                </dict>
                </plist>
                """, LABEL, binaryPath, enable ? "true" : "false", stdoutLog, stderrLog);

        try {
            Files.createDirectories(plistPath.getParent());
            Files.writeString(plistPath, plistContent, StandardCharsets.UTF_8);

            if (enable) {
                runLaunchctl(List.of("load", "-w", plistPath.toString()));
            }

            return new ServiceOperationResult(true, "INSTALL", LABEL, plistPath.toString(),
                    "Launchd agent installed" + (enable ? " and loaded" : "") + " at " + plistPath);
        } catch (IOException e) {
            return new ServiceOperationResult(false, "INSTALL", LABEL, plistPath.toString(), "Failed to write plist: " + e.getMessage());
        }
    }

    @Override
    public ServiceOperationResult enableService(boolean systemScope) {
        Path plistPath = resolvePlistPath(systemScope);
        if (!Files.exists(plistPath)) {
            return new ServiceOperationResult(false, "ENABLE", LABEL, plistPath.toString(), "Launchd plist not found.");
        }
        int code = runLaunchctl(List.of("load", "-w", plistPath.toString()));
        return new ServiceOperationResult(code == 0, "ENABLE", LABEL, plistPath.toString(),
                code == 0 ? "Launchd agent enabled and loaded" : "Failed to load launchd agent");
    }

    @Override
    public ServiceOperationResult disableService(boolean systemScope) {
        Path plistPath = resolvePlistPath(systemScope);
        int code = runLaunchctl(List.of("unload", "-w", plistPath.toString()));
        return new ServiceOperationResult(code == 0, "DISABLE", LABEL, plistPath.toString(),
                code == 0 ? "Launchd agent disabled and unloaded" : "Failed to unload launchd agent");
    }

    @Override
    public ServiceOperationResult startService(boolean systemScope) {
        Path plistPath = resolvePlistPath(systemScope);
        runLaunchctl(List.of("load", plistPath.toString()));
        int code = runLaunchctl(List.of("start", LABEL));
        return new ServiceOperationResult(code == 0, "START", LABEL, plistPath.toString(),
                code == 0 ? "Started Nexus launchd daemon service" : "Failed to start launchd agent");
    }

    @Override
    public ServiceOperationResult stopService(boolean systemScope) {
        Path plistPath = resolvePlistPath(systemScope);
        int code = runLaunchctl(List.of("stop", LABEL));
        runLaunchctl(List.of("unload", plistPath.toString()));
        return new ServiceOperationResult(code == 0, "STOP", LABEL, plistPath.toString(),
                code == 0 ? "Stopped Nexus launchd daemon service" : "Failed to stop launchd agent");
    }

    @Override
    public ServiceOperationResult uninstallService(boolean systemScope) {
        Path plistPath = resolvePlistPath(systemScope);
        try {
            runLaunchctl(List.of("unload", "-w", plistPath.toString()));
            Files.deleteIfExists(plistPath);
            return new ServiceOperationResult(true, "UNINSTALL", LABEL, plistPath.toString(), "Uninstalled launchd agent");
        } catch (Exception e) {
            return new ServiceOperationResult(false, "UNINSTALL", LABEL, plistPath.toString(), "Failed to remove plist: " + e.getMessage());
        }
    }

    @Override
    public ServiceStatusDetails getServiceStatus(boolean systemScope) {
        Path plistPath = resolvePlistPath(systemScope);
        boolean installed = Files.exists(plistPath);
        boolean running = isServiceRunning(systemScope);
        return new ServiceStatusDetails(installed, installed, running, LABEL, plistPath.toString(), "launchd",
                running ? "running" : (installed ? "stopped" : "not-installed"));
    }

    @Override
    public String getLogGuidance(boolean systemScope) {
        return "tail -f /tmp/nexus-daemon.log";
    }

    private int runLaunchctl(List<String> args) {
        try {
            List<String> cmd = new java.util.ArrayList<>();
            cmd.add("launchctl");
            cmd.addAll(args);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            return pb.start().waitFor();
        } catch (Exception e) {
            return -1;
        }
    }

    private String runLaunchctlCapture(List<String> args) {
        try {
            List<String> cmd = new java.util.ArrayList<>();
            cmd.add("launchctl");
            cmd.addAll(args);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            return new String(pb.start().getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return "";
        }
    }
}
