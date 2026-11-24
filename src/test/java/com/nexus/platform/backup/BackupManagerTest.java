package com.nexus.platform.backup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nexus.capabilities.reminder.scheduler.ReminderSchedulerProvider;
import com.nexus.capabilities.reminder.storage.ReminderStorageManager;
import com.nexus.platform.backup.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class BackupManagerTest {

    private ObjectMapper objectMapper;
    private BackupCryptoService cryptoService;
    private ReminderStorageManager reminderStorageManager;
    private ReminderSchedulerProvider reminderSchedulerProvider;
    private BackupManager backupManager;

    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        cryptoService = new BackupCryptoService();
        reminderStorageManager = mock(ReminderStorageManager.class);
        reminderSchedulerProvider = mock(ReminderSchedulerProvider.class);
        backupManager = new BackupManager(objectMapper, cryptoService, reminderStorageManager, reminderSchedulerProvider);
    }

    @Test
    @DisplayName("Creates and inspects an unencrypted backup archive")
    void testCreateAndInspectPlainBackup(@TempDir Path tempDir) throws Exception {
        Path archiveOut = tempDir.resolve("backup-plain.zip");

        BackupRequest request = BackupRequest.builder()
                .outputPath(archiveOut)
                .encrypt(false)
                .includeSystem(true)
                .includeUser(true)
                .overwrite(true)
                .build();

        BackupResult result = backupManager.createBackup(request);

        assertThat(result.success()).isTrue();
        assertThat(result.encrypted()).isFalse();
        assertThat(Files.exists(archiveOut)).isTrue();
        assertThat(result.archiveSizeBytes()).isGreaterThan(0);
        assertThat(result.manifest()).isNotNull();

        BackupManifest inspected = backupManager.inspectBackup(archiveOut, null);
        assertThat(inspected).isNotNull();
        assertThat(inspected.encrypted()).isFalse();
        assertThat(inspected.files()).isNotEmpty();
    }

    @Test
    @DisplayName("Creates, encrypts with AES-256-GCM, and inspects with password")
    void testCreateAndInspectEncryptedBackup(@TempDir Path tempDir) throws Exception {
        Path archiveOut = tempDir.resolve("backup-encrypted.zip.enc");
        char[] password = "StrongBackupPassword999!".toCharArray();

        BackupRequest request = BackupRequest.builder()
                .outputPath(archiveOut)
                .encrypt(true)
                .password(password)
                .includeSystem(true)
                .includeUser(true)
                .overwrite(true)
                .build();

        BackupResult result = backupManager.createBackup(request);

        assertThat(result.success()).isTrue();
        assertThat(result.encrypted()).isTrue();
        assertThat(cryptoService.isEncrypted(archiveOut)).isTrue();

        // Inspect with correct password
        BackupManifest inspected = backupManager.inspectBackup(archiveOut, password);
        assertThat(inspected).isNotNull();
        assertThat(inspected.encrypted()).isTrue();
        assertThat(inspected.files()).isNotEmpty();

        // Inspect with wrong password throws SecurityException
        char[] wrongPassword = "WrongPassword123".toCharArray();
        assertThatThrownBy(() -> backupManager.inspectBackup(archiveOut, wrongPassword))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("Restores archive with dry-run mode without modifying actual disk files")
    void testRestoreDryRun(@TempDir Path tempDir) throws Exception {
        Path archiveOut = tempDir.resolve("backup-dryrun.zip");

        BackupRequest backupReq = BackupRequest.builder()
                .outputPath(archiveOut)
                .encrypt(false)
                .includeSystem(true)
                .includeUser(true)
                .overwrite(true)
                .build();

        backupManager.createBackup(backupReq);

        RestoreRequest restoreReq = RestoreRequest.builder()
                .archivePath(archiveOut)
                .dryRun(true)
                .restoreSystem(true)
                .restoreUser(true)
                .force(false)
                .build();

        RestoreResult restoreRes = backupManager.restoreBackup(restoreReq);

        assertThat(restoreRes.success()).isTrue();
        assertThat(restoreRes.dryRun()).isTrue();
        assertThat(restoreRes.actions()).isNotEmpty();
        assertThat(restoreRes.actions().stream().allMatch(a -> a.actionTaken().startsWith("WOULD_"))).isTrue();
    }
}
