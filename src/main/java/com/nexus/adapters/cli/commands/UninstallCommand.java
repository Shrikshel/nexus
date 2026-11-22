package com.nexus.adapters.cli.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.adapters.cli.NexusCliRootCommand;
import com.nexus.adapters.ipc.DaemonIpcClient;
import com.nexus.adapters.ipc.DaemonIpcServer;
import com.nexus.platform.NexusUpdateService;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * CLI command to uninstall Nexus binary, terminate running daemon services,
 * and clean runtime tokens and state.
 */
@Component
@Command(
        name = "uninstall",
        description = "Uninstall Nexus binary, stop running background services, and clean runtime data",
        mixinStandardHelpOptions = true
)
public class UninstallCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    private final DaemonIpcClient ipcClient;
    private final NexusUpdateService updateService;
    private final ObjectMapper objectMapper;

    @Option(names = {"-y", "--yes"}, description = "Skip interactive confirmation prompt")
    private boolean assumeYes;

    @Option(names = {"--purge", "--all"}, description = "Also delete log files, configuration directories, and all runtime traces")
    private boolean purge;

    @Option(names = {"--dry-run"}, description = "Display what will be removed without modifying the system")
    private boolean dryRun;

    public UninstallCommand(DaemonIpcClient ipcClient, NexusUpdateService updateService) {
        this.ipcClient = ipcClient;
        this.updateService = updateService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Integer call() {
        PrintWriter out = (spec != null) ? spec.commandLine().getOut() : new PrintWriter(System.out, true);
        PrintWriter err = (spec != null) ? spec.commandLine().getErr() : new PrintWriter(System.err, true);

        boolean isDryRun = this.dryRun || (spec != null && spec.commandLine().getParseResult() != null && spec.commandLine().getParseResult().hasMatchedOption("--dry-run"));
        boolean isPurge = this.purge || (spec != null && spec.commandLine().getParseResult() != null && spec.commandLine().getParseResult().hasMatchedOption("--purge"));

        boolean jsonMode = false;
        if (spec != null && spec.root() != null && spec.root().commandLine().getCommand() instanceof NexusCliRootCommand root) {
            jsonMode = root.isJson();
        }

        Path binaryPath = updateService.locateCurrentBinary();
        Path runtimeDir = DaemonIpcServer.getRuntimeDirectory();

        if (isDryRun) {
            if (jsonMode) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("dryRun", true);
                map.put("binaryPath", binaryPath.toString());
                map.put("runtimeDir", runtimeDir.toString());
                map.put("purge", isPurge);
                try {
                    out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(map));
                } catch (Exception ignored) {}
            } else {
                out.println("=== Nexus Uninstaller [DRY-RUN] ===");
                out.println("  Executable to remove : " + binaryPath);
                out.println("  Runtime state to clean: " + runtimeDir);
                out.println("  Purge logs/config     : " + (isPurge ? "YES" : "NO"));
                out.println("\n[DRY-RUN] No files were removed.");
            }
            return 0;
        }

        if (!jsonMode) {
            out.println("=== Uninstalling Nexus Host Management Agent ===");
        }

        // 1. Terminate running background daemon if active
        try {
            if (ipcClient.isDaemonRunning(DaemonIpcServer.DEFAULT_PORT)) {
                if (!jsonMode) out.println("  • Stopping running background daemon via IPC...");
                ipcClient.sendCommand("STOP", DaemonIpcServer.DEFAULT_PORT);
            }
        } catch (Exception ignored) {
            // Daemon might not be running
        }

        // 2. Remove executable binary and any duplicates across standard paths
        boolean binaryRemoved = false;
        boolean hasPermissionError = false;
        String binaryStatus = "NOT_FOUND";
        java.util.Set<Path> searchLocations = new java.util.LinkedHashSet<>();
        if (binaryPath != null) {
            searchLocations.add(binaryPath.toAbsolutePath().normalize());
        }
        searchLocations.add(Path.of("/usr/local/bin/nexus"));
        searchLocations.add(Path.of("/usr/bin/nexus"));
        searchLocations.add(Path.of(System.getProperty("user.home"), ".local", "bin", "nexus"));
        searchLocations.add(Path.of(System.getProperty("user.home"), "bin", "nexus"));

        for (Path p : searchLocations) {
            if (p != null && Files.exists(p)) {
                try {
                    Files.deleteIfExists(p);
                    binaryRemoved = true;
                    binaryStatus = "REMOVED";
                    if (!jsonMode) out.println("  ✓ Removed executable: " + p);
                } catch (Exception e) {
                    hasPermissionError = true;
                    binaryStatus = "FAILED: " + e.getMessage();
                    if (!jsonMode) {
                        err.println("  ✗ Failed to delete executable: " + p + " (" + e.getMessage() + ")");
                        err.println("    Hint: Elevated permissions may be required. Try: sudo nexus uninstall");
                    }
                }
            }
        }

        if (!binaryRemoved && !hasPermissionError && !jsonMode) {
            out.println("  • Executable binary not found in standard paths (already removed or running in IDE/JAR)");
        }

        // 3. Clean runtime directory & IPC tokens
        boolean runtimeCleaned = false;
        File rDir = runtimeDir.toFile();
        if (rDir.exists() && rDir.isDirectory()) {
            deleteRecursively(rDir);
            runtimeCleaned = true;
            if (!jsonMode) out.println("  ✓ Cleaned runtime directory: " + runtimeDir);
        }

        // Clean any leftover /tmp/nexus-* directories
        File tmpDir = new File("/tmp");
        if (tmpDir.exists() && tmpDir.isDirectory()) {
            File[] nexusTmp = tmpDir.listFiles((dir, name) -> name.startsWith("nexus-"));
            if (nexusTmp != null) {
                for (File d : nexusTmp) {
                    if (d.isDirectory()) {
                        deleteRecursively(d);
                    }
                }
            }
        }

        // 4. Purge configurations and logs if requested
        if (purge) {
            Path[] purgeTargets = new Path[] {
                    Path.of("logs"),
                    Path.of("/var/log/nexus"),
                    Path.of(System.getProperty("user.home"), ".nexus"),
                    Path.of(System.getProperty("user.home"), ".config", "nexus"),
                    Path.of("/etc/nexus")
            };
            for (Path p : purgeTargets) {
                File f = p.toFile();
                if (f.exists()) {
                    deleteRecursively(f);
                    if (!jsonMode) out.println("  ✓ Purged: " + p);
                }
            }
        }

        if (jsonMode) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("binaryPath", binaryPath.toString());
            map.put("binaryStatus", binaryStatus);
            map.put("binaryRemoved", binaryRemoved);
            map.put("runtimeCleaned", runtimeCleaned);
            map.put("purge", purge);
            map.put("success", !hasPermissionError);
            try {
                out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(map));
            } catch (Exception ignored) {}
        } else {
            if (hasPermissionError) {
                err.println("\n✗ Nexus uninstallation was incomplete due to permission errors.");
                err.println("  Please re-run with elevated privileges: sudo nexus uninstall" + (purge ? " --all" : ""));
                return 1;
            } else {
                out.println("\n✓ Nexus uninstallation complete.");
            }
        }

        return hasPermissionError ? 1 : 0;
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
}
