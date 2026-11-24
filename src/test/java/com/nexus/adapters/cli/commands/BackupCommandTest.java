package com.nexus.adapters.cli.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nexus.capabilities.reminder.scheduler.ReminderSchedulerProvider;
import com.nexus.capabilities.reminder.storage.ReminderStorageManager;
import com.nexus.config.NexusProperties;
import com.nexus.platform.backup.BackupCryptoService;
import com.nexus.platform.backup.BackupManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BackupCommandTest {

    @TempDir
    Path tempDir;

    private CommandLine commandLine;
    private StringWriter outWriter;
    private StringWriter errWriter;
    private BackupManager backupManager;
    private BackupCryptoService cryptoService;
    private NexusProperties properties;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        cryptoService = new BackupCryptoService();
        ReminderStorageManager reminderStorageManager = Mockito.mock(ReminderStorageManager.class);
        ReminderSchedulerProvider reminderSchedulerProvider = Mockito.mock(ReminderSchedulerProvider.class);
        backupManager = new BackupManager(objectMapper, cryptoService, reminderStorageManager, reminderSchedulerProvider);
        properties = new NexusProperties();

        BackupCommand backupCommand = new BackupCommand(backupManager, cryptoService, properties);
        commandLine = new CommandLine(backupCommand);

        outWriter = new StringWriter();
        errWriter = new StringWriter();
        commandLine.setOut(new PrintWriter(outWriter));
        commandLine.setErr(new PrintWriter(errWriter));
    }

    @Test
    @DisplayName("Shows usage when invoked without subcommands")
    void testUsageWithoutSubcommand() {
        int exitCode = commandLine.execute();
        assertThat(exitCode).isEqualTo(0);
        assertThat(outWriter.toString()).contains("Usage: backup");
    }

    @Test
    @DisplayName("Creates plain backup archive and inspects manifest")
    void testCreateAndInspectPlainArchive() {
        Path archive = tempDir.resolve("plain-backup.zip");

        int createCode = commandLine.execute("create", "-o", archive.toString(), "-f");
        assertThat(createCode).isEqualTo(0);
        assertThat(Files.exists(archive)).isTrue();
        assertThat(outWriter.toString()).contains("Created Nexus backup archive successfully!");

        outWriter.getBuffer().setLength(0);
        int inspectCode = commandLine.execute("inspect", archive.toString());
        assertThat(inspectCode).isEqualTo(0);
        assertThat(outWriter.toString()).contains("=== Nexus Backup Archive Manifest ===");
        assertThat(outWriter.toString()).contains("Packaged File Inventory");
    }

    @Test
    @DisplayName("Creates AES-256-GCM encrypted backup archive and inspects with password")
    void testCreateAndInspectEncryptedArchive() {
        Path archive = tempDir.resolve("enc-backup.zip.enc");

        int createCode = commandLine.execute("create", "-e", "-p", "SecretPass2026!", "-o", archive.toString(), "-f");
        assertThat(createCode).isEqualTo(0);
        assertThat(Files.exists(archive)).isTrue();
        assertThat(outWriter.toString()).contains("AES-256-GCM (Password Protected)");

        // Inspecting without password fails
        int failInspectCode = commandLine.execute("inspect", archive.toString());
        assertThat(failInspectCode).isEqualTo(1);
        assertThat(errWriter.toString()).contains("Archive is encrypted with AES-256-GCM");

        // Inspecting with password succeeds
        outWriter.getBuffer().setLength(0);
        errWriter.getBuffer().setLength(0);
        int okInspectCode = commandLine.execute("inspect", archive.toString(), "-p", "SecretPass2026!");
        assertThat(okInspectCode).isEqualTo(0);
        assertThat(outWriter.toString()).contains("AES-256-GCM");
    }

    @Test
    @DisplayName("Restores backup archive in dry-run mode")
    void testRestoreDryRun() {
        Path archive = tempDir.resolve("test-restore.zip");
        int createCode = commandLine.execute("create", "-o", archive.toString(), "-f");
        assertThat(createCode).isEqualTo(0);

        outWriter.getBuffer().setLength(0);
        errWriter.getBuffer().setLength(0);
        int restoreCode = commandLine.execute("restore", archive.toString(), "--dry-run", "--user");
        assertThat(restoreCode).isEqualTo(0);
        assertThat(outWriter.toString()).contains("Nexus Backup Restore Simulation [DRY RUN]");
    }
}
