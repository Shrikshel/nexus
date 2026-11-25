package com.nexus.adapters.cli.commands;

import com.nexus.config.NexusProperties;
import com.nexus.platform.backup.BackupCryptoService;
import com.nexus.platform.backup.BackupManager;
import com.nexus.platform.backup.dto.BackupManifest;
import com.nexus.platform.backup.dto.BackupRequest;
import com.nexus.platform.backup.dto.BackupResult;
import com.nexus.platform.backup.dto.RestoreRequest;
import com.nexus.platform.backup.dto.RestoreResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

import java.io.Console;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;
import java.util.concurrent.Callable;

/**
 * CLI command group for full Nexus backup, AES-256-GCM encryption, inspection, and disaster recovery restore.
 */
@Component
@Command(
    name = "backup",
    aliases = {"snapshot"},
    description = "Full backup, AES-256-GCM encryption, inspection, and disaster recovery for Nexus",
    subcommands = {
        BackupCommand.CreateSubcommand.class,
        BackupCommand.InspectSubcommand.class,
        BackupCommand.RestoreSubcommand.class
    },
    mixinStandardHelpOptions = true
)
public class BackupCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    private final BackupManager backupManager;
    private final BackupCryptoService cryptoService;
    private final NexusProperties properties;

    @Autowired
    public BackupCommand(
            BackupManager backupManager,
            BackupCryptoService cryptoService,
            NexusProperties properties) {
        this.backupManager = backupManager;
        this.cryptoService = cryptoService;
        this.properties = properties;
    }

    public BackupManager getBackupManager() {
        return backupManager;
    }

    public BackupCryptoService getCryptoService() {
        return cryptoService;
    }

    public NexusProperties getProperties() {
        return properties;
    }

    @Override
    public Integer call() {
        if (spec != null) {
            spec.commandLine().usage(spec.commandLine().getOut());
        }
        return 0;
    }

    // =========================================================================
    // 1. CREATE SUBCOMMAND
    // =========================================================================
    @Component
    @Command(
        name = "create",
        aliases = {"export"},
        description = "Create a full backup archive of Nexus configuration, reminders, and timers",
        mixinStandardHelpOptions = true
    )
    public static class CreateSubcommand implements Callable<Integer> {

        @Spec
        private CommandSpec spec;

        @ParentCommand
        private BackupCommand parent;

        @Option(names = {"-o", "--output"}, description = "Target backup archive output path")
        private Path outputPath;

        @Option(names = {"-e", "--encrypt"}, description = "Encrypt backup archive with AES-256-GCM")
        private boolean encrypt;

        @Option(names = {"-p", "--password", "--key"}, description = "Encryption password (or set via NEXUS_BACKUP_PASSWORD)")
        private String password;

        @Option(names = {"--system"}, description = "Include system scope only (/root/.nexus)")
        private boolean systemOnly;

        @Option(names = {"--user"}, description = "Include user scope only (~/.nexus)")
        private boolean userOnly;

        @Option(names = {"-f", "--force"}, description = "Overwrite target archive if it already exists")
        private boolean force;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            PrintWriter err = spec.commandLine().getErr();

            boolean includeSystem = !userOnly;
            boolean includeUser = !systemOnly;

            char[] passChars = resolvePassword(password, encrypt, parent.getProperties());
            boolean shouldEncrypt = encrypt || passChars != null;

            if (shouldEncrypt && (passChars == null || passChars.length == 0)) {
                err.println("\u001B[31m✗ Error: Encryption requested but no password was provided.\u001B[0m");
                err.println("  👉 Provide via --password <pass>, set NEXUS_BACKUP_PASSWORD env var, or configure in config.yml.");
                return 1;
            }

            BackupRequest request = BackupRequest.builder()
                    .outputPath(outputPath)
                    .encrypt(shouldEncrypt)
                    .password(passChars)
                    .includeSystem(includeSystem)
                    .includeUser(includeUser)
                    .overwrite(force)
                    .build();

            BackupResult res = parent.getBackupManager().createBackup(request);
            if (!res.success()) {
                err.println("\u001B[31m✗ " + res.message() + "\u001B[0m");
                return 1;
            }

            out.println("\u001B[32m✓ Created Nexus backup archive successfully!\u001B[0m");
            out.printf("  • Archive File : %s%n", res.archivePath());
            out.printf("  • Archive Size : %,d bytes (%.2f KB)%n", res.archiveSizeBytes(), res.archiveSizeBytes() / 1024.0);
            out.printf("  • Encryption   : %s%n", res.encrypted() ? "\u001B[33mAES-256-GCM (Password Protected)\u001B[0m" : "None (Plain ZIP)");
            out.printf("  • Total Files  : %d files archived%n", res.totalFiles());

            if (res.manifest() != null && res.manifest().stats() != null) {
                var stats = res.manifest().stats();
                out.printf("  • Reminders    : %s system, %s user%n", stats.get("systemReminders"), stats.get("userReminders"));
                out.printf("  • Timers       : %s system, %s user%n", stats.get("systemTimers"), stats.get("userTimers"));
            }
            out.println();
            out.println("  ℹ Inspect contents: nexus backup inspect " + res.archivePath());
            out.println("  ℹ Restore archive : sudo nexus backup restore " + res.archivePath() + " --rearm");
            return 0;
        }
    }

    // =========================================================================
    // 2. INSPECT SUBCOMMAND
    // =========================================================================
    @Component
    @Command(
        name = "inspect",
        aliases = {"info", "show"},
        description = "Inspect metadata manifest, stats, and file inventory in a backup archive",
        mixinStandardHelpOptions = true
    )
    public static class InspectSubcommand implements Callable<Integer> {

        @Spec
        private CommandSpec spec;

        @ParentCommand
        private BackupCommand parent;

        @Parameters(index = "0", description = "Backup archive file path to inspect")
        private Path archivePath;

        @Option(names = {"-p", "--password", "--key"}, description = "Decryption password (if archive is encrypted)")
        private String password;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            PrintWriter err = spec.commandLine().getErr();

            if (archivePath == null || !archivePath.toFile().exists()) {
                err.println("\u001B[31m✗ Error: Archive file not found: " + archivePath + "\u001B[0m");
                return 1;
            }

            boolean encrypted = parent.getCryptoService().isEncrypted(archivePath);
            char[] passChars = null;

            if (encrypted) {
                passChars = resolvePassword(password, true, parent.getProperties());
                if (passChars == null || passChars.length == 0) {
                    err.println("\u001B[31m✗ Error: Archive is encrypted with AES-256-GCM.\u001B[0m");
                    err.println("  👉 Please provide password: nexus backup inspect " + archivePath + " --password <pass>");
                    return 1;
                }
            }

            try {
                BackupManifest m = parent.getBackupManager().inspectBackup(archivePath, passChars);
                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault());

                out.println("\u001B[1;36m=== Nexus Backup Archive Manifest ===\u001B[0m");
                out.printf("  • Archive Path : %s%n", archivePath.toAbsolutePath().normalize());
                out.printf("  • Created At   : %s%n", m.createdAt() != null ? dtf.format(m.createdAt()) : "N/A");
                out.printf("  • Host Origin  : %s (%s)%n", m.hostname(), m.os());
                out.printf("  • Nexus Ver    : %s (Format v%d)%n", m.nexusVersion(), m.version());
                out.printf("  • Encryption   : %s%n", m.encrypted() ? "\u001B[33mAES-256-GCM\u001B[0m" : "None");
                out.printf("  • Scopes       : %s%n", String.join(", ", m.scopes()));

                if (m.stats() != null) {
                    out.printf("  • Reminders    : %s system, %s user%n", m.stats().get("systemReminders"), m.stats().get("userReminders"));
                    out.printf("  • Timers       : %s system, %s user%n", m.stats().get("systemTimers"), m.stats().get("userTimers"));
                    out.printf("  • Total Files  : %s archived (%,d uncompressed bytes)%n",
                            m.stats().get("totalFiles"), ((Number) m.stats().getOrDefault("uncompressedBytes", 0)).longValue());
                }

                out.println();
                out.println("  --- Packaged File Inventory ---");
                out.printf("  %-32s %-8s %-12s %s%n", "ARCHIVE PATH", "SCOPE", "SIZE", "SHA-256 HASH (PREVIEW)");
                out.println("  " + "-".repeat(84));

                for (var file : m.files()) {
                    String hashShort = file.sha256() != null && file.sha256().length() >= 12
                            ? file.sha256().substring(0, 12) + "..." : "-";
                    out.printf("  %-32s %-8s %,10d B  %s%n",
                            file.archivePath(), file.scope(), file.sizeBytes(), hashShort);
                }
                out.println();
                return 0;

            } catch (SecurityException se) {
                err.println("\u001B[31m✗ " + se.getMessage() + "\u001B[0m");
                return 1;
            } catch (Exception e) {
                err.println("\u001B[31m✗ Failed to inspect backup archive: " + e.getMessage() + "\u001B[0m");
                return 1;
            }
        }
    }

    // =========================================================================
    // 3. RESTORE SUBCOMMAND
    // =========================================================================
    @Component
    @Command(
        name = "restore",
        aliases = {"import"},
        description = "Restore Nexus configuration, reminders, and timers from a backup archive",
        mixinStandardHelpOptions = true
    )
    public static class RestoreSubcommand implements Callable<Integer> {

        @Spec
        private CommandSpec spec;

        @ParentCommand
        private BackupCommand parent;

        @Parameters(index = "0", description = "Backup archive file path to restore")
        private Path archivePath;

        @Option(names = {"-p", "--password", "--key"}, description = "Decryption password (if archive is encrypted)")
        private String password;

        @Option(names = {"--dry-run"}, description = "Simulate restore and preview actions without modifying files")
        private boolean dryRun;

        @Option(names = {"-y", "--force"}, description = "Overwrite existing files on host without prompting")
        private boolean force;

        @Option(names = {"--rearm", "--sync"}, description = "Automatically reconcile and re-arm systemd timers and reminders")
        private boolean rearm;

        @Option(names = {"--system"}, description = "Restore system scope files only")
        private boolean systemOnly;

        @Option(names = {"--user"}, description = "Restore user scope files only")
        private boolean userOnly;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            PrintWriter err = spec.commandLine().getErr();

            if (archivePath == null || !archivePath.toFile().exists()) {
                err.println("\u001B[31m✗ Error: Archive file not found: " + archivePath + "\u001B[0m");
                return 1;
            }

            boolean isRoot = "root".equalsIgnoreCase(System.getProperty("user.name"));
            boolean restoreSystem = !userOnly;
            boolean restoreUser = !systemOnly;

            if (restoreSystem && !isRoot && !dryRun) {
                err.println("\u001B[31m✗ Error: Restoring system scope (/root/.nexus) requires root privileges.\u001B[0m");
                err.println("  👉 Please run with sudo:");
                err.println("     sudo nexus backup restore " + archivePath + (rearm ? " --rearm" : ""));
                err.println("  ℹ Or restore user scope only: nexus backup restore " + archivePath + " --user");
                return 1;
            }

            boolean encrypted = parent.getCryptoService().isEncrypted(archivePath);
            char[] passChars = null;

            if (encrypted) {
                passChars = resolvePassword(password, true, parent.getProperties());
                if (passChars == null || passChars.length == 0) {
                    err.println("\u001B[31m✗ Error: Archive is encrypted with AES-256-GCM.\u001B[0m");
                    err.println("  👉 Please provide password: nexus backup restore " + archivePath + " --password <pass>");
                    return 1;
                }
            }

            RestoreRequest request = RestoreRequest.builder()
                    .archivePath(archivePath)
                    .password(passChars)
                    .dryRun(dryRun)
                    .force(force)
                    .rearm(rearm)
                    .restoreSystem(restoreSystem)
                    .restoreUser(restoreUser)
                    .build();

            RestoreResult res = parent.getBackupManager().restoreBackup(request);
            if (!res.success()) {
                err.println("\u001B[31m✗ " + res.message() + "\u001B[0m");
                return 1;
            }

            String banner = res.dryRun()
                    ? "\u001B[1;33m=== Nexus Backup Restore Simulation [DRY RUN] ===\u001B[0m"
                    : "\u001B[1;32m=== Nexus Backup Restore Summary ===\u001B[0m";

            out.println(banner);
            out.printf("  • Archive File  : %s%n", archivePath);
            out.printf("  • Files Status  : %d %s, %d skipped%n",
                    res.filesRestored(), res.dryRun() ? "to restore" : "restored", res.filesSkipped());

            if (rearm) {
                out.printf("  • Timers Status : %d timers rearmed, %d reminders synchronized%n",
                        res.timersRearmed(), res.remindersSynced());
            }

            out.println();
            out.println("  --- Action Details ---");
            out.printf("  %-16s %-30s %s%n", "ACTION", "ARCHIVE ENTRY", "DESTINATION ON HOST");
            out.println("  " + "-".repeat(84));

            for (var act : res.actions()) {
                String color = switch (act.actionTaken()) {
                    case "RESTORED", "WOULD_RESTORE" -> "\u001B[32m";
                    case "OVERWRITTEN", "WOULD_OVERWRITE" -> "\u001B[33m";
                    default -> "\u001B[37m";
                };
                out.printf("  %s%-16s\u001B[0m %-30s %s%n",
                        color, "[" + act.actionTaken() + "]", act.archivePath(), act.targetPath());
            }
            out.println();

            if (res.dryRun()) {
                out.println("\u001B[33mℹ Dry run completed. No files were written to disk.\u001B[0m");
                out.println("  To apply changes, run without --dry-run:");
                out.println("  sudo nexus backup restore " + archivePath + " --rearm -y");
            } else {
                out.println("\u001B[32m✓ All restored files successfully written to host!\u001B[0m");
            }

            return 0;
        }
    }

    private static char[] resolvePassword(String cliPass, boolean encryptRequested, NexusProperties props) {
        if (cliPass != null && !cliPass.isBlank()) {
            return cliPass.toCharArray();
        }
        String env = System.getenv("NEXUS_BACKUP_PASSWORD");
        if (env != null && !env.isBlank()) {
            return env.toCharArray();
        }
        String envKey = System.getenv("NEXUS_BACKUP_KEY");
        if (envKey != null && !envKey.isBlank()) {
            return envKey.toCharArray();
        }
        if (props != null && props.getBackup() != null && props.getBackup().getPassword() != null && !props.getBackup().getPassword().isBlank()) {
            return props.getBackup().getPassword().toCharArray();
        }
        // If encryption requested and console is interactive, prompt securely
        if (encryptRequested) {
            Console console = System.console();
            if (console != null) {
                return console.readPassword("Enter backup encryption passphrase: ");
            }
        }
        return null;
    }
}
