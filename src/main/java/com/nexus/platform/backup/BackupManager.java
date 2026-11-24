package com.nexus.platform.backup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.capabilities.reminder.dto.ReminderEvent;
import com.nexus.capabilities.reminder.scheduler.ReminderSchedulerProvider;
import com.nexus.capabilities.reminder.storage.ReminderStorageManager;
import com.nexus.platform.backup.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Enterprise-grade backup, disaster recovery, and restoration manager for Nexus.
 * Packages configs, reminders, timer states, and executable scripts into standard or AES-256-GCM encrypted archives.
 */
@Service
public class BackupManager {

    private static final Logger log = LoggerFactory.getLogger(BackupManager.class);

    private final ObjectMapper objectMapper;
    private final BackupCryptoService cryptoService;
    private final ReminderStorageManager reminderStorageManager;
    private final ReminderSchedulerProvider reminderSchedulerProvider;

    @Autowired
    public BackupManager(
            @Autowired(required = false) ObjectMapper objectMapper,
            BackupCryptoService cryptoService,
            @Autowired(required = false) ReminderStorageManager reminderStorageManager,
            @Autowired(required = false) ReminderSchedulerProvider reminderSchedulerProvider) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        this.cryptoService = cryptoService;
        this.reminderStorageManager = reminderStorageManager;
        this.reminderSchedulerProvider = reminderSchedulerProvider;
    }

    /**
     * Creates a full backup archive of Nexus system and user files.
     */
    public BackupResult createBackup(BackupRequest request) {
        Objects.requireNonNull(request, "BackupRequest must not be null");
        Path targetPath = request.outputPath();
        if (targetPath == null) {
            String timestamp = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                    .withZone(java.time.ZoneId.systemDefault())
                    .format(Instant.now());
            String ext = request.encrypt() ? ".zip.enc" : ".zip";
            String home = System.getProperty("user.home", ".");
            targetPath = Path.of(home, "nexus-backup-" + timestamp + ext);
        }

        if (Files.exists(targetPath) && !request.overwrite()) {
            return new BackupResult(false, targetPath, 0, 0, request.encrypt(), null,
                    "Target archive already exists: " + targetPath + ". Use --force to overwrite.");
        }

        try {
            Map<String, Path> filesToArchive = discoverFilesToArchive(request.includeSystem(), request.includeUser());
            if (filesToArchive.isEmpty()) {
                return new BackupResult(false, targetPath, 0, 0, request.encrypt(), null,
                        "No Nexus configuration, reminder, or timer files found to back up.");
            }

            ByteArrayOutputStream zipBaos = new ByteArrayOutputStream();
            List<BackupManifest.BackupFileEntry> manifestEntries = new ArrayList<>();
            Map<String, Object> stats = new LinkedHashMap<>();

            // Calculate preliminary stats
            int systemReminders = countReminders(Path.of("/root/.nexus/data/reminders.json"));
            int userReminders = countReminders(Path.of(System.getProperty("user.home"), ".nexus", "data", "reminders.json"));
            int systemTimers = countTimers(Path.of("/root/.nexus/state/timers.json"));
            int userTimers = countTimers(Path.of(System.getProperty("user.home"), ".nexus", "state", "timers.json"));

            stats.put("systemReminders", systemReminders);
            stats.put("userReminders", userReminders);
            stats.put("systemTimers", systemTimers);
            stats.put("userTimers", userTimers);
            stats.put("totalFiles", filesToArchive.size());

            long totalUncompressedBytes = 0;
            BackupManifest manifest = null;

            try (ZipOutputStream zos = new ZipOutputStream(zipBaos)) {
                // Add all files
                for (Map.Entry<String, Path> entry : filesToArchive.entrySet()) {
                    String archivePath = entry.getKey();
                    Path realPath = entry.getValue();
                    if (!Files.exists(realPath) || Files.isDirectory(realPath)) {
                        continue;
                    }

                    byte[] content = Files.readAllBytes(realPath);
                    String sha256 = computeSha256(content);
                    String scope = archivePath.startsWith("system/") ? "SYSTEM" : "USER";

                    manifestEntries.add(new BackupManifest.BackupFileEntry(
                            archivePath, realPath.toAbsolutePath().normalize().toString(), scope, content.length, sha256
                    ));
                    totalUncompressedBytes += content.length;

                    ZipEntry ze = new ZipEntry(archivePath);
                    ze.setSize(content.length);
                    ze.setTime(Files.getLastModifiedTime(realPath).toMillis());
                    zos.putNextEntry(ze);
                    zos.write(content);
                    zos.closeEntry();
                }

                stats.put("uncompressedBytes", totalUncompressedBytes);

                // Add manifest.json as the final header entry
                List<String> scopes = new ArrayList<>();
                if (request.includeSystem()) scopes.add("SYSTEM");
                if (request.includeUser()) scopes.add("USER");

                String hostname = "unknown";
                try {
                    hostname = InetAddress.getLocalHost().getHostName();
                } catch (Exception ignored) {}

                manifest = new BackupManifest(
                        1,
                        Instant.now(),
                        hostname,
                        System.getProperty("os.name") + " " + System.getProperty("os.version"),
                        "0.0.21-SNAPSHOT",
                        request.encrypt(),
                        scopes,
                        stats,
                        manifestEntries
                );

                byte[] manifestBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest);
                ZipEntry manifestEntry = new ZipEntry("manifest.json");
                manifestEntry.setSize(manifestBytes.length);
                zos.putNextEntry(manifestEntry);
                zos.write(manifestBytes);
                zos.closeEntry();
            }

            byte[] finalArchiveBytes = zipBaos.toByteArray();
            if (request.encrypt()) {
                if (request.password() == null || request.password().length == 0) {
                    throw new IllegalArgumentException("Encryption requested but no password was provided");
                }
                finalArchiveBytes = cryptoService.encrypt(finalArchiveBytes, request.password());
            }

            if (targetPath.getParent() != null) {
                Files.createDirectories(targetPath.getParent());
            }
            Files.write(targetPath, finalArchiveBytes);

            BackupManifest finalManifest = manifest;
            String msg = String.format("Successfully created %sbackup archive (%d files, %d bytes) at %s",
                    request.encrypt() ? "encrypted " : "", manifestEntries.size(), finalArchiveBytes.length, targetPath);
            return new BackupResult(true, targetPath, finalArchiveBytes.length, manifestEntries.size(),
                    request.encrypt(), finalManifest, msg);

        } catch (Exception e) {
            log.error("Failed to create backup archive: {}", e.getMessage(), e);
            return new BackupResult(false, targetPath, 0, 0, request.encrypt(), null,
                    "Backup failed: " + e.getMessage());
        }
    }

    /**
     * Inspects a backup archive and returns its metadata manifest without restoring anything.
     */
    public BackupManifest inspectBackup(Path archivePath, char[] password) throws Exception {
        if (!Files.exists(archivePath)) {
            throw new IllegalArgumentException("Backup archive not found: " + archivePath);
        }

        byte[] fileBytes = Files.readAllBytes(archivePath);
        byte[] zipBytes = fileBytes;

        if (cryptoService.isEncrypted(archivePath)) {
            if (password == null || password.length == 0) {
                throw new IllegalArgumentException("Archive is encrypted with AES-256-GCM. Please provide --password.");
            }
            zipBytes = cryptoService.decrypt(fileBytes, password);
        }

        return inspectBackupBytes(zipBytes);
    }

    /**
     * Restores files from a backup archive with dry-run and timer re-arm support.
     */
    public RestoreResult restoreBackup(RestoreRequest request) {
        Objects.requireNonNull(request, "RestoreRequest must not be null");
        Path archivePath = request.archivePath();

        if (archivePath == null || !Files.exists(archivePath)) {
            return new RestoreResult(false, request.dryRun(), 0, 0, 0, 0, Collections.emptyList(),
                    "Archive file does not exist: " + archivePath);
        }

        try {
            byte[] fileBytes = Files.readAllBytes(archivePath);
            byte[] zipBytes = fileBytes;

            if (cryptoService.isEncrypted(archivePath)) {
                if (request.password() == null || request.password().length == 0) {
                    return new RestoreResult(false, request.dryRun(), 0, 0, 0, 0, Collections.emptyList(),
                            "Archive is encrypted. Please provide decryption password with --password.");
                }
                zipBytes = cryptoService.decrypt(fileBytes, request.password());
            }

            BackupManifest manifest = inspectBackupBytes(zipBytes);
            List<RestoreResult.RestoredFileAction> actions = new ArrayList<>();
            int restoredCount = 0;
            int skippedCount = 0;

            // Extract entries
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String name = entry.getName();
                    if ("manifest.json".equals(name) || entry.isDirectory()) {
                        continue;
                    }

                    boolean isSystem = name.startsWith("system/");
                    boolean isUser = name.startsWith("user/");

                    if (isSystem && !request.restoreSystem()) {
                        skippedCount++;
                        continue;
                    }
                    if (isUser && !request.restoreUser()) {
                        skippedCount++;
                        continue;
                    }

                    Path destination = resolveDestinationPath(name);
                    if (destination == null) {
                        skippedCount++;
                        continue;
                    }

                    boolean exists = Files.exists(destination);
                    long size = entry.getSize();
                    String actionTaken;

                    if (request.dryRun()) {
                        actionTaken = exists ? "WOULD_OVERWRITE" : "WOULD_RESTORE";
                    } else {
                        if (exists && !request.force()) {
                            actionTaken = "SKIPPED_EXISTS";
                            skippedCount++;
                        } else {
                            if (destination.getParent() != null) {
                                Files.createDirectories(destination.getParent());
                            }
                            byte[] entryBytes = zis.readAllBytes();
                            Files.write(destination, entryBytes);

                            // Make scripts executable
                            if (name.contains("scripts/") || name.endsWith(".sh")) {
                                destination.toFile().setExecutable(true, false);
                            }
                            actionTaken = exists ? "OVERWRITTEN" : "RESTORED";
                            restoredCount++;
                        }
                    }

                    actions.add(new RestoreResult.RestoredFileAction(name, destination.toString(), size, exists, actionTaken));
                    zis.closeEntry();
                }
            }

            int timersRearmed = 0;
            int remindersSynced = 0;

            // Handle --rearm / --sync if requested and not dry-run
            if (!request.dryRun() && request.rearm()) {
                if (reminderStorageManager != null && reminderSchedulerProvider != null) {
                    List<ReminderEvent> events = reminderStorageManager.loadAll();
                    for (ReminderEvent e : events) {
                        if ("ACTIVE".equalsIgnoreCase(e.state().status())) {
                            boolean armed = reminderSchedulerProvider.armTimer(e);
                            if (armed) remindersSynced++;
                        }
                    }
                }

                if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux")) {
                    try {
                        Process p = new ProcessBuilder("systemctl", "daemon-reload").start();
                        p.waitFor();
                        timersRearmed++;
                    } catch (Exception ignored) {}
                }
            }

            String msg = request.dryRun()
                    ? String.format("Dry run completed: %d files would be restored, %d skipped.", actions.size(), skippedCount)
                    : String.format("Restore completed: %d files restored, %d skipped. Rearmed %d timers, %d reminders.",
                    restoredCount, skippedCount, timersRearmed, remindersSynced);

            return new RestoreResult(true, request.dryRun(), restoredCount, skippedCount,
                    timersRearmed, remindersSynced, actions, msg);

        } catch (SecurityException se) {
            return new RestoreResult(false, request.dryRun(), 0, 0, 0, 0, Collections.emptyList(),
                    se.getMessage());
        } catch (Exception e) {
            log.error("Restore failed: {}", e.getMessage(), e);
            return new RestoreResult(false, request.dryRun(), 0, 0, 0, 0, Collections.emptyList(),
                    "Restore failed: " + e.getMessage());
        }
    }

    private BackupManifest inspectBackupBytes(byte[] zipBytes) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("manifest.json".equals(entry.getName())) {
                    byte[] bytes = zis.readAllBytes();
                    return parseManifest(bytes);
                }
            }
        }
        throw new IllegalArgumentException("Backup archive is missing manifest.json header");
    }

    private BackupManifest parseManifest(byte[] bytes) {
        try {
            return objectMapper.readValue(bytes, BackupManifest.class);
        } catch (Exception e) {
            log.debug("Standard ObjectMapper deserialization failed, falling back to manual JsonNode parser: {}", e.getMessage());
            return parseManifestFromNode(bytes);
        }
    }

    private BackupManifest parseManifestFromNode(byte[] bytes) {
        try {
            var root = objectMapper.readTree(bytes);
            int version = root.path("version").asInt(1);
            Instant createdAt = null;
            String dateText = root.path("createdAt").asText(null);
            if (dateText != null && !dateText.isBlank()) {
                try {
                    createdAt = Instant.parse(dateText);
                } catch (Exception ignored) {}
            }
            String hostname = root.path("hostname").asText("unknown");
            String os = root.path("os").asText("unknown");
            String nexusVersion = root.path("nexusVersion").asText("unknown");
            boolean encrypted = root.path("encrypted").asBoolean(false);

            List<String> scopes = new ArrayList<>();
            var scopesNode = root.path("scopes");
            if (scopesNode.isArray()) {
                for (var s : scopesNode) {
                    scopes.add(s.asText());
                }
            }

            Map<String, Object> stats = new LinkedHashMap<>();
            var statsNode = root.path("stats");
            if (statsNode.isObject()) {
                var it = statsNode.fields();
                while (it.hasNext()) {
                    var entry = it.next();
                    if (entry.getValue().isNumber()) {
                        stats.put(entry.getKey(), entry.getValue().numberValue());
                    } else if (entry.getValue().isBoolean()) {
                        stats.put(entry.getKey(), entry.getValue().asBoolean());
                    } else {
                        stats.put(entry.getKey(), entry.getValue().asText());
                    }
                }
            }

            List<BackupManifest.BackupFileEntry> files = new ArrayList<>();
            var filesNode = root.path("files");
            if (filesNode.isArray()) {
                for (var f : filesNode) {
                    files.add(new BackupManifest.BackupFileEntry(
                            f.path("archivePath").asText(""),
                            f.path("targetPath").asText(""),
                            f.path("scope").asText(""),
                            f.path("sizeBytes").asLong(0),
                            f.path("sha256").asText("")
                    ));
                }
            }

            return new BackupManifest(version, createdAt, hostname, os, nexusVersion, encrypted, scopes, stats, files);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to parse manifest.json: " + ex.getMessage(), ex);
        }
    }

    private Map<String, Path> discoverFilesToArchive(boolean includeSystem, boolean includeUser) {
        Map<String, Path> map = new LinkedHashMap<>();

        if (includeSystem) {
            addIfExists(map, "system/config.yml", Path.of("/etc/nexus/config.yml"));
            addIfExists(map, "system/config.yaml", Path.of("/etc/nexus/config.yaml"));
            addIfExists(map, "system/root-config.yml", Path.of("/root/.nexus/config.yml"));
            addIfExists(map, "system/data/reminders.json", Path.of("/root/.nexus/data/reminders.json"));
            addIfExists(map, "system/state/timers.json", Path.of("/root/.nexus/state/timers.json"));
            addDirectoryFiles(map, "system/scripts", Path.of("/root/.nexus/scripts"));
        }

        if (includeUser) {
            String userHome = System.getProperty("user.home", ".");
            addIfExists(map, "user/config.yml", Path.of(userHome, ".nexus", "config.yml"));
            addIfExists(map, "user/config.yaml", Path.of(userHome, ".nexus", "config.yaml"));
            addIfExists(map, "user/data/reminders.json", Path.of(userHome, ".nexus", "data", "reminders.json"));
            addIfExists(map, "user/state/timers.json", Path.of(userHome, ".nexus", "state", "timers.json"));
            addDirectoryFiles(map, "user/scripts", Path.of(userHome, ".nexus", "scripts"));
        }

        return map;
    }

    private void addIfExists(Map<String, Path> map, String key, Path path) {
        if (Files.exists(path) && Files.isRegularFile(path)) {
            map.put(key, path);
        }
    }

    private void addDirectoryFiles(Map<String, Path> map, String prefix, Path dir) {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) return;
        try (var stream = Files.walk(dir)) {
            stream.filter(Files::isRegularFile).forEach(p -> {
                String rel = dir.relativize(p).toString();
                map.put(prefix + "/" + rel, p);
            });
        } catch (Exception ignored) {}
    }

    private Path resolveDestinationPath(String archivePath) {
        String userHome = System.getProperty("user.home", ".");
        if (archivePath.startsWith("system/")) {
            String sub = archivePath.substring("system/".length());
            if ("config.yml".equals(sub)) return Path.of("/etc/nexus/config.yml");
            if ("config.yaml".equals(sub)) return Path.of("/etc/nexus/config.yaml");
            if ("root-config.yml".equals(sub)) return Path.of("/root/.nexus/config.yml");
            if (sub.startsWith("data/")) return Path.of("/root/.nexus", sub);
            if (sub.startsWith("state/")) return Path.of("/root/.nexus", sub);
            if (sub.startsWith("scripts/")) return Path.of("/root/.nexus", sub);
            return Path.of("/etc/nexus", sub);
        } else if (archivePath.startsWith("user/")) {
            String sub = archivePath.substring("user/".length());
            return Path.of(userHome, ".nexus", sub);
        }
        return null;
    }

    private int countReminders(Path path) {
        if (!Files.exists(path)) return 0;
        try {
            var node = objectMapper.readTree(path.toFile());
            var events = node.path("events");
            return events.isArray() ? events.size() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private int countTimers(Path path) {
        if (!Files.exists(path)) return 0;
        try {
            var node = objectMapper.readTree(path.toFile());
            var timers = node.path("timers");
            return timers.isObject() ? timers.size() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private String computeSha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
