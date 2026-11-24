package com.nexus.adapters.cli.commands;

import com.nexus.adapters.cli.NexusCliRootCommand;
import com.nexus.platform.logging.LogColorizer;
import com.nexus.platform.logging.NexusLogManager;
import com.nexus.platform.logging.NexusLogManager.LogFileInfo;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

import java.io.File;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * CLI command for inspecting, following, rotating, and managing Nexus application and audit logs,
 * inspired by PM2 log management.
 */
@Component
@Command(
    name = "logs",
    aliases = {"log"},
    description = "Inspect, follow, stream, and manage Nexus application and audit logs (PM2-style)",
    subcommands = {
        LogsCommand.FlushSubcommand.class,
        LogsCommand.ListSubcommand.class,
        LogsCommand.PathSubcommand.class
    },
    mixinStandardHelpOptions = true
)
public class LogsCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @ParentCommand
    private NexusCliRootCommand root;

    @Option(names = {"-n", "--lines"}, description = "Number of lines to show (default: 30)", defaultValue = "30")
    private int lines = 30;

    @Option(names = {"-f", "--follow"}, description = "Stream and follow log updates in real-time (Ctrl+C to exit)")
    private boolean follow;

    @Option(names = {"-a", "--audit"}, description = "Inspect the tamper-evident audit log (nexus-audit.log) instead of application log")
    private boolean audit;

    @Option(names = {"--clear", "--flush"}, description = "Flush / truncate active log files (like 'pm2 flush')")
    private boolean flush;

    @Option(names = {"-l", "--list"}, description = "List all log files, active sizes, and rotated archives")
    private boolean list;

    @Option(names = {"-p", "--path"}, description = "Display the absolute path to the canonical log directory")
    private boolean showPath;

    @Option(names = {"--raw"}, description = "Output raw log lines without decorative banners")
    private boolean raw;

    @Option(names = {"--system"}, description = "Target system daemon logs (/root/.nexus/logs/)")
    private boolean systemScope;

    @Option(names = {"--user"}, description = "Target local user logs (~/.nexus/logs/)")
    private boolean userScope;

    @Option(names = {"--color"}, description = "Force ANSI color formatting (default: auto)")
    private boolean color;

    @Option(names = {"--no-color"}, description = "Disable ANSI color formatting")
    private boolean noColor;

    public LogsCommand() {}

    public LogsCommand(NexusCliRootCommand root) {
        this.root = root;
    }

    @Override
    public Integer call() {
        PrintWriter out = spec != null ? spec.commandLine().getOut() : new PrintWriter(System.out, true);
        PrintWriter err = spec != null ? spec.commandLine().getErr() : new PrintWriter(System.err, true);
        boolean json = root != null && root.isJson();

        // 1. Show path option
        if (showPath) {
            Path logDir = NexusLogManager.getLogDirectory();
            if (json) {
                out.println(String.format("{\"logDirectory\":\"%s\",\"appLog\":\"%s\",\"auditLog\":\"%s\"}",
                        logDir, NexusLogManager.getAppLogFile(), NexusLogManager.getAuditLogFile()));
            } else {
                out.println(logDir.toString());
            }
            return 0;
        }

        // 2. Flush / Clear option
        if (flush) {
            return executeFlush(out, json);
        }

        // 3. List option
        if (list) {
            return executeList(out, json);
        }

        // 4. View / Follow logs
        Path targetLog = resolveTargetLog(err);
        if (targetLog == null) {
            return 1;
        }
        String logLabel = audit ? "Audit Trail" : "Application Logs";

        if (!Files.exists(targetLog)) {
            if (json) {
                out.println("{\"lines\":[],\"file\":\"" + targetLog + "\",\"exists\":false}");
            } else {
                out.println("=== " + logLabel + " ===");
                out.println("  Log file not found at: " + targetLog);
                out.println("  (Logs will be created once the daemon runs or an event is recorded)");
            }
            return 0;
        }

        boolean useColor = isColorEnabled();
        if (follow) {
            return executeFollow(targetLog, logLabel, out, useColor);
        } else {
            return executeTail(targetLog, logLabel, out, json, useColor);
        }
    }

    private Path resolveTargetLog(PrintWriter err) {
        String logName = audit ? NexusLogManager.AUDIT_LOG_NAME : NexusLogManager.APP_LOG_NAME;
        Path systemLog = Path.of("/root/.nexus/logs", logName);
        Path userLog = NexusLogManager.getLogDirectory().resolve(logName);

        if (systemScope || "root".equalsIgnoreCase(System.getProperty("user.name"))) {
            return systemLog;
        }

        if (userScope) {
            return userLog;
        }

        // Default: if running as regular user, check if system daemon log is readable or active
        if (Files.isReadable(systemLog)) {
            return systemLog;
        }

        if (Files.exists(Path.of("/etc/systemd/system/nexus.service")) && (!Files.exists(userLog) || getFileSize(userLog) == 0)) {
            if (!Files.isReadable(systemLog)) {
                err.println("\u001B[33mℹ The active Nexus daemon runs as a root system service.\u001B[0m");
                err.println("  Reading system daemon logs requires root privileges:");
                err.println("  👉 Run with sudo: sudo nexus logs" + (follow ? " -f" : ""));
                return null;
            }
        }

        return userLog;
    }

    private static long getFileSize(Path p) {
        try {
            return Files.size(p);
        } catch (Exception e) {
            return 0;
        }
    }

    private boolean isColorEnabled() {
        if (noColor || raw || (root != null && root.isJson())) {
            return false;
        }
        if (color) {
            return true;
        }
        if (System.getenv("NO_COLOR") != null) {
            return false;
        }
        if (spec != null && spec.commandLine() != null) {
            return spec.commandLine().getColorScheme().ansi().enabled();
        }
        return System.console() != null;
    }

    private int executeTail(Path targetLog, String logLabel, PrintWriter out, boolean json, boolean useColor) {
        List<String> recent = NexusLogManager.readRecentLines(targetLog, lines);

        if (json) {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"file\":\"").append(targetLog).append("\",\"lines\":[");
            for (int i = 0; i < recent.size(); i++) {
                sb.append("\"").append(escapeJson(recent.get(i))).append("\"");
                if (i < recent.size() - 1) sb.append(",");
            }
            sb.append("]}");
            out.println(sb.toString());
            return 0;
        }

        if (!raw) {
            out.println("\u001B[1;36m=== " + logLabel + " (" + targetLog.getFileName() + ") ===\u001B[0m");
            out.println("  Location : " + targetLog);
            out.println("  Displaying last " + recent.size() + " lines (use -f to follow, -n <count> for more):");
            out.println("--------------------------------------------------------------------------------");
        }

        for (String line : recent) {
            out.println(useColor ? LogColorizer.colorize(line) : line);
        }
        out.flush();

        if (!raw && recent.isEmpty()) {
            out.println("  (log file is currently empty)");
        }
        return 0;
    }

    private int executeFollow(Path targetLog, String logLabel, PrintWriter out, boolean useColor) {
        if (!raw) {
            out.println("\u001B[1;36m=== Streaming " + logLabel + " (" + targetLog.getFileName() + ") ===\u001B[0m");
            out.println("  Location : " + targetLog);
            out.println("  Press Ctrl+C to exit streaming.");
            out.println("--------------------------------------------------------------------------------");
        }

        // Print initial recent lines
        List<String> initialLines = NexusLogManager.readRecentLines(targetLog, Math.min(lines, 30));
        for (String line : initialLines) {
            out.println(useColor ? LogColorizer.colorize(line) : line);
        }
        out.flush();

        try (RandomAccessFile raf = new RandomAccessFile(targetLog.toFile(), "r")) {
            long pos = targetLog.toFile().length();
            raf.seek(pos);

            while (!Thread.currentThread().isInterrupted()) {
                long len = targetLog.toFile().length();
                if (len < pos) {
                    // File was rotated or truncated (flush)
                    out.println("\u001B[33m\n[NOTICE: Log file rotated or truncated - resuming from start]\u001B[0m");
                    pos = 0;
                    raf.seek(0);
                } else if (len > pos) {
                    raf.seek(pos);
                    String line;
                    while ((line = raf.readLine()) != null) {
                        String decoded = new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                        out.println(useColor ? LogColorizer.colorize(decoded) : decoded);
                    }
                    pos = raf.getFilePointer();
                    out.flush();
                }
                Thread.sleep(150);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            out.println("\n[Log streaming stopped: " + e.getMessage() + "]");
        }
        return 0;
    }

    private int executeFlush(PrintWriter out, boolean json) {
        boolean ok = NexusLogManager.flushLogs(!audit || true, audit || true);
        if (json) {
            out.println(String.format("{\"action\":\"flush\",\"success\":%s,\"logDir\":\"%s\"}",
                    ok, NexusLogManager.getLogDirectory()));
        } else {
            if (ok) {
                out.println("✓ Nexus active log files successfully flushed and truncated.");
                out.println("  • " + NexusLogManager.getAppLogFile());
                out.println("  • " + NexusLogManager.getAuditLogFile());
            } else {
                out.println("⚠ Failed to truncate some log files. Check file permissions.");
            }
        }
        return ok ? 0 : 1;
    }

    private int executeList(PrintWriter out, boolean json) {
        List<LogFileInfo> files = NexusLogManager.listLogFiles();
        Path logDir = NexusLogManager.getLogDirectory();

        if (json) {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"logDirectory\":\"").append(logDir).append("\",\"files\":[");
            for (int i = 0; i < files.size(); i++) {
                LogFileInfo f = files.get(i);
                sb.append(String.format("{\"fileName\":\"%s\",\"sizeBytes\":%d,\"formattedSize\":\"%s\",\"lastModified\":\"%s\",\"isArchive\":%s,\"isActive\":%s}",
                        f.fileName(), f.sizeBytes(), f.formattedSize(), f.lastModified(), f.isCompressedArchive(), f.isActiveLog()));
                if (i < files.size() - 1) sb.append(",");
            }
            sb.append("]}");
            out.println(sb.toString());
            return 0;
        }

        out.println("=== Nexus Log Files & Rotated Archives ===");
        out.println("  Directory: " + logDir);
        out.println();

        if (files.isEmpty()) {
            out.println("  ℹ No log files currently found in directory.");
            return 0;
        }

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

        out.printf("  %-30s  %-10s  %-20s  %s%n", "FILE NAME", "SIZE", "LAST MODIFIED", "TYPE");
        out.println("  ----------------------------------------------------------------------------");

        long totalBytes = 0;
        for (LogFileInfo f : files) {
            totalBytes += f.sizeBytes();
            String type = f.isActiveLog() ? "ACTIVE LOG" : (f.isCompressedArchive() ? "ROTATED (GZ)" : "ARCHIVE");
            out.printf("  %-30s  %-10s  %-20s  %s%n",
                    f.fileName(),
                    f.formattedSize(),
                    dtf.format(f.lastModified()),
                    type);
        }

        out.println("  ----------------------------------------------------------------------------");
        out.printf("  Total Storage: %s across %d file(s)%n", NexusLogManager.formatFileSize(totalBytes), files.size());
        out.println("  💡 Retention: 10MB chunks, 14 days history (30d audit), 500MB total cap.");
        return 0;
    }

    private static String escapeJson(String raw) {
        if (raw == null) return "";
        return raw.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\b", "\\b")
                  .replace("\f", "\\f")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    // --- Subcommands ---

    @Component
    @Command(name = "flush", aliases = {"clear"}, description = "Flush and truncate active log files (PM2 style)")
    public static class FlushSubcommand implements Callable<Integer> {
        @ParentCommand
        private LogsCommand parent;

        @Spec
        private CommandSpec spec;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            boolean json = parent != null && parent.root != null && parent.root.isJson();
            return parent != null ? parent.executeFlush(out, json) : 0;
        }
    }

    @Component
    @Command(name = "list", aliases = {"ls"}, description = "List all log files, sizes, and rotated archives")
    public static class ListSubcommand implements Callable<Integer> {
        @ParentCommand
        private LogsCommand parent;

        @Spec
        private CommandSpec spec;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            boolean json = parent != null && parent.root != null && parent.root.isJson();
            return parent != null ? parent.executeList(out, json) : 0;
        }
    }

    @Component
    @Command(name = "path", description = "Print the canonical log directory and file paths")
    public static class PathSubcommand implements Callable<Integer> {
        @ParentCommand
        private LogsCommand parent;

        @Spec
        private CommandSpec spec;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            boolean json = parent != null && parent.root != null && parent.root.isJson();
            Path logDir = NexusLogManager.getLogDirectory();
            if (json) {
                out.println(String.format("{\"logDirectory\":\"%s\",\"appLog\":\"%s\",\"auditLog\":\"%s\"}",
                        logDir, NexusLogManager.getAppLogFile(), NexusLogManager.getAuditLogFile()));
            } else {
                out.println(logDir.toString());
            }
            return 0;
        }
    }
}
