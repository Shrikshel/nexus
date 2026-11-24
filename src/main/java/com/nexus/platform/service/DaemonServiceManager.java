package com.nexus.platform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Cross-platform manager for host daemon service lifecycle and supervisor integration.
 */
@Service
public class DaemonServiceManager {

    private static final Logger log = LoggerFactory.getLogger(DaemonServiceManager.class);

    private final List<DaemonServiceProvider> providers;
    private DaemonServiceProvider activeProvider;

    public DaemonServiceManager(List<DaemonServiceProvider> providers) {
        this.providers = providers;
        for (DaemonServiceProvider p : providers) {
            if (p.isSupported()) {
                this.activeProvider = p;
                log.debug("Initialized DaemonServiceManager with supervisor: {}", p.getPlatformName());
                break;
            }
        }
    }

    public DaemonServiceProvider getActiveProvider() {
        return activeProvider;
    }

    public boolean isSupported() {
        return activeProvider != null;
    }

    public boolean isServiceInstalled(boolean systemScope) {
        return activeProvider != null && activeProvider.isServiceInstalled(systemScope);
    }

    public boolean isServiceEnabled(boolean systemScope) {
        return activeProvider != null && activeProvider.isServiceEnabled(systemScope);
    }

    public boolean isServiceRunning(boolean systemScope) {
        return activeProvider != null && activeProvider.isServiceRunning(systemScope);
    }

    public ServiceOperationResult installService(boolean systemScope, boolean enable) {
        if (activeProvider == null) {
            return new ServiceOperationResult(false, "INSTALL", "nexus", "", "No supported service supervisor detected on this OS.");
        }
        if (systemScope) {
            cleanupLegacyUserService();
        }
        String binaryPath = resolveBinaryPath();
        return activeProvider.installService(systemScope, enable, binaryPath);
    }

    public boolean cleanupLegacyUserService() {
        if (activeProvider == null) {
            return false;
        }
        return activeProvider.cleanupLegacyUserService();
    }

    public ServiceOperationResult enableService(boolean systemScope) {
        if (activeProvider == null) {
            return new ServiceOperationResult(false, "ENABLE", "nexus", "", "No supported service supervisor detected.");
        }
        return activeProvider.enableService(systemScope);
    }

    public ServiceOperationResult disableService(boolean systemScope) {
        if (activeProvider == null) {
            return new ServiceOperationResult(false, "DISABLE", "nexus", "", "No supported service supervisor detected.");
        }
        return activeProvider.disableService(systemScope);
    }

    public ServiceOperationResult startService(boolean systemScope) {
        if (activeProvider == null) {
            return new ServiceOperationResult(false, "START", "nexus", "", "No supported service supervisor detected.");
        }
        return activeProvider.startService(systemScope);
    }

    public ServiceOperationResult stopService(boolean systemScope) {
        if (activeProvider == null) {
            return new ServiceOperationResult(false, "STOP", "nexus", "", "No supported service supervisor detected.");
        }
        return activeProvider.stopService(systemScope);
    }

    public ServiceOperationResult uninstallService(boolean systemScope) {
        if (activeProvider == null) {
            return new ServiceOperationResult(false, "UNINSTALL", "nexus", "", "No supported service supervisor detected.");
        }
        return activeProvider.uninstallService(systemScope);
    }

    public ServiceStatusDetails getServiceStatus(boolean systemScope) {
        if (activeProvider == null) {
            return new ServiceStatusDetails(false, false, false, "nexus", "", "none", "unsupported platform");
        }
        return activeProvider.getServiceStatus(systemScope);
    }

    public String getLogGuidance(boolean systemScope) {
        return activeProvider != null ? activeProvider.getLogGuidance(systemScope) : "";
    }

    public String resolveBinaryPath() {
        // 1. ProcessHandle command (works in GraalVM native binary)
        try {
            Optional<String> cmd = ProcessHandle.current().info().command();
            if (cmd.isPresent() && !cmd.get().isBlank()) {
                String c = cmd.get();
                if (!c.endsWith("java") && !c.endsWith("java.exe")) {
                    File f = new File(c);
                    if (f.canExecute()) {
                        return f.getAbsolutePath();
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 2. Standard install locations
        for (String candidate : List.of(
                "/usr/local/bin/nexus",
                Path.of(System.getProperty("user.home"), ".local", "bin", "nexus").toString(),
                "/usr/bin/nexus",
                Path.of(System.getProperty("user.home"), "bin", "nexus").toString()
        )) {
            File f = new File(candidate);
            if (f.canExecute()) {
                return f.getAbsolutePath();
            }
        }

        // 3. Fallback to which
        try {
            Process p = new ProcessBuilder("which", "nexus").start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (p.waitFor() == 0 && !out.isBlank() && new File(out).canExecute()) {
                return out;
            }
        } catch (Throwable ignored) {}

        // 4. Fallback to current directory wrapper
        File localNexus = new File("./nexus");
        if (localNexus.canExecute()) {
            return localNexus.getAbsolutePath();
        }

        return "/usr/local/bin/nexus";
    }

    public boolean isLingerAdvised(boolean systemScope) {
        if (systemScope || activeProvider == null || !"systemd".equals(activeProvider.getPlatformName())) {
            return false;
        }
        String user = System.getProperty("user.name");
        File lingerFile = new File("/var/lib/systemd/linger/" + user);
        return !lingerFile.exists();
    }
}
