package com.nexus.platform.logging;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Synchronized log management for Nexus, inspired by PM2 log management.
 * Provides a single canonical log directory, PM2-style log rotation,
 * live tailing, log inspection, log flushing/truncation, and cleanup of rogue log directories.
 */
public final class NexusLogManager {

    public static final String PROPERTY_LOG_DIR = "nexus.log.dir";
    public static final String ENV_LOG_DIR = "NEXUS_LOG_DIR";

    public static final String APP_LOG_NAME = "nexus.log";
    public static final String AUDIT_LOG_NAME = "nexus-audit.log";

    private static volatile Path canonicalLogDir;

    private NexusLogManager() {}

    /**
     * Initializes the canonical log directory very early before SLF4J / Logback
     * or Spring Boot classes are initialized.
     * Sets the JVM system property 'nexus.log.dir' so Logback uses it immediately.
     */
    public static synchronized Path initEarly() {
        if (canonicalLogDir != null) {
            return canonicalLogDir;
        }

        Path resolved = resolveCanonicalLogDirectory();
        try {
            Files.createDirectories(resolved);
        } catch (Exception ignored) {
            // Fallback to user home if target was not writable
            resolved = Path.of(System.getProperty("user.home", "."), ".nexus", "logs");
            try {
                Files.createDirectories(resolved);
            } catch (Exception e) {
                // If even ~/.nexus/logs is not writable, fall back to java.io.tmpdir
                resolved = Path.of(System.getProperty("java.io.tmpdir", "/tmp"), "nexus-logs");
                try {
                    Files.createDirectories(resolved);
                } catch (Exception ignored2) {}
            }
        }

        canonicalLogDir = resolved.toAbsolutePath().normalize();
        System.setProperty(PROPERTY_LOG_DIR, canonicalLogDir.toString());

        // Automatically clean up rogue nested directories if they exist
        cleanRogueNestedLogDirs();

        return canonicalLogDir;
    }

    /**
     * Resolves the canonical log directory based on priority:
     * 1. System property 'nexus.log.dir'
     * 2. Environment variable 'NEXUS_LOG_DIR'
     * 3. Root/system service scope: /var/log/nexus (if root user and /var/log is writable)
     * 4. User default: ~/.nexus/logs
     */
    public static Path resolveCanonicalLogDirectory() {
        String sysProp = System.getProperty(PROPERTY_LOG_DIR);
        if (sysProp != null && !sysProp.isBlank()) {
            return Path.of(sysProp.trim());
        }

        String envProp = System.getenv(ENV_LOG_DIR);
        if (envProp != null && !envProp.isBlank()) {
            return Path.of(envProp.trim());
        }

        // Check if running as root on Linux/macOS
        String user = System.getProperty("user.name", "");
        if ("root".equalsIgnoreCase(user)) {
            return Path.of("/root/.nexus/logs");
        }

        String home = System.getProperty("user.home", ".");
        return Path.of(home, ".nexus", "logs");
    }

    public static synchronized void reset() {
        canonicalLogDir = null;
    }

    /**
     * Updates the canonical log directory (e.g. from config.yaml setting).
     */
    public static synchronized void setLogDirectory(Path customDir) {
        if (customDir != null) {
            try {
                Files.createDirectories(customDir);
                canonicalLogDir = customDir.toAbsolutePath().normalize();
                System.setProperty(PROPERTY_LOG_DIR, canonicalLogDir.toString());
            } catch (Exception ignored) {}
        }
    }

    /**
     * Returns the active canonical log directory.
     */
    public static Path getLogDirectory() {
        if (canonicalLogDir == null) {
            return initEarly();
        }
        return canonicalLogDir;
    }

    /**
     * Returns the Path to the primary application log (nexus.log).
     */
    public static Path getAppLogFile() {
        return getLogDirectory().resolve(APP_LOG_NAME);
    }

    /**
     * Returns the Path to the audit trail log (nexus-audit.log).
     */
    public static Path getAuditLogFile() {
        return getLogDirectory().resolve(AUDIT_LOG_NAME);
    }

    /**
     * Record representing a log file entry in the log directory.
     */
    public record LogFileInfo(
            String fileName,
            Path path,
            long sizeBytes,
            String formattedSize,
            Instant lastModified,
            boolean isCompressedArchive,
            boolean isActiveLog
    ) {}

    /**
     * Lists all log files and rotated archives in the canonical log directory.
     */
    public static List<LogFileInfo> listLogFiles() {
        Path logDir = getLogDirectory();
        if (!Files.exists(logDir)) {
            return Collections.emptyList();
        }

        List<LogFileInfo> results = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(logDir, "*.{log,gz}")) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)) {
                    String name = entry.getFileName().toString();
                    long size = Files.size(entry);
                    Instant modified = Files.getLastModifiedTime(entry).toInstant();
                    boolean isGz = name.endsWith(".gz");
                    boolean isActive = APP_LOG_NAME.equals(name) || AUDIT_LOG_NAME.equals(name);

                    results.add(new LogFileInfo(
                            name,
                            entry.toAbsolutePath(),
                            size,
                            formatFileSize(size),
                            modified,
                            isGz,
                            isActive
                    ));
                }
            }
        } catch (IOException ignored) {}

        // Sort: active logs first, then latest modified to oldest
        results.sort((a, b) -> {
            if (a.isActiveLog() && !b.isActiveLog()) return -1;
            if (!a.isActiveLog() && b.isActiveLog()) return 1;
            return b.lastModified().compareTo(a.lastModified());
        });

        return results;
    }

    /**
     * Reads the last N lines of a log file efficiently.
     */
    public static List<String> readRecentLines(Path logFile, int lineCount) {
        if (logFile == null || !Files.exists(logFile) || Files.isDirectory(logFile)) {
            return Collections.emptyList();
        }
        if (lineCount <= 0) {
            lineCount = 30;
        }

        try (RandomAccessFile raf = new RandomAccessFile(logFile.toFile(), "r")) {
            long length = raf.length();
            if (length == 0) {
                return Collections.emptyList();
            }

            List<String> lines = new ArrayList<>();
            long pos = length - 1;
            ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream();

            while (pos >= 0 && lines.size() < lineCount) {
                raf.seek(pos);
                int b = raf.read();

                if (b == '\n') {
                    if (pos < length - 1 && lineBuffer.size() > 0) {
                        lines.add(lineBuffer.toString(StandardCharsets.UTF_8));
                        lineBuffer.reset();
                    }
                } else if (b != '\r') {
                    lineBuffer.write(b);
                }
                pos--;
            }

            if (lineBuffer.size() > 0 && lines.size() < lineCount) {
                lines.add(lineBuffer.toString(StandardCharsets.UTF_8));
            }

            // Since we read backwards, characters in each line were added backwards
            List<String> reversed = new ArrayList<>(lines.size());
            for (int i = lines.size() - 1; i >= 0; i--) {
                String line = lines.get(i);
                reversed.add(new StringBuilder(line).reverse().toString());
            }
            return reversed;
        } catch (Exception e) {
            // Fallback for smaller files or errors
            try {
                List<String> all = Files.readAllLines(logFile, StandardCharsets.UTF_8);
                int start = Math.max(0, all.size() - lineCount);
                return all.subList(start, all.size());
            } catch (Exception ex) {
                return Collections.emptyList();
            }
        }
    }

    /**
     * Flushes (truncates) the active log files, preserving file descriptors (like PM2 flush).
     */
    public static boolean flushLogs(boolean appLog, boolean auditLog) {
        boolean success = true;
        if (appLog) {
            Path app = getAppLogFile();
            if (Files.exists(app)) {
                try {
                    Files.write(app, new byte[0], StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                } catch (IOException e) {
                    success = false;
                }
            }
        }
        if (auditLog) {
            Path audit = getAuditLogFile();
            if (Files.exists(audit)) {
                try {
                    Files.write(audit, new byte[0], StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                } catch (IOException e) {
                    success = false;
                }
            }
        }
        return success;
    }

    /**
     * Cleans up stray rogue nested directories accidentally generated under ~/.nexus/
     * (e.g. ~/.nexus/logs/logs, ~/.nexus/scripts/logs, ~/.nexus/state/logs).
     */
    public static void cleanRogueNestedLogDirs() {
        List<Path> nexusRoots = new ArrayList<>();
        nexusRoots.add(Path.of(System.getProperty("user.home", "."), ".nexus"));
        nexusRoots.add(Path.of("/root/.nexus"));

        for (Path nexusHome : nexusRoots) {
            if (!Files.exists(nexusHome)) {
                continue;
            }

            List<Path> strayDirs = List.of(
                    nexusHome.resolve("logs").resolve("logs"),
                    nexusHome.resolve("scripts").resolve("logs"),
                    nexusHome.resolve("state").resolve("logs")
            );

            for (Path stray : strayDirs) {
                if (Files.exists(stray) && Files.isDirectory(stray)) {
                    try {
                        deleteRecursively(stray);
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(dir)) {
            for (Path entry : entries) {
                if (Files.isDirectory(entry)) {
                    deleteRecursively(entry);
                } else {
                    Files.deleteIfExists(entry);
                }
            }
        }
        Files.deleteIfExists(dir);
    }

    public static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format(Locale.ROOT, "%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    private static class ByteArrayOutputStream extends java.io.ByteArrayOutputStream {
        public String toString(java.nio.charset.Charset charset) {
            return new String(buf, 0, count, charset);
        }
    }
}
