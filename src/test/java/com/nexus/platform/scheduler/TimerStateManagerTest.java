package com.nexus.platform.scheduler;

import com.nexus.capabilities.scheduler.timer.dto.TimerScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TimerStateManagerTest {

    @TempDir
    Path tempDir;

    private TimerStateManager stateManager;

    @BeforeEach
    void setUp() {
        stateManager = new TimerStateManager() {
            @Override
            public Path getStateFilePath(TimerScope scope) {
                return tempDir.resolve("state").resolve("timers.json");
            }

            @Override
            protected Path resolveUnitDirectory(TimerScope scope) {
                return tempDir.resolve("units");
            }
        };
    }

    @Test
    @DisplayName("Sequential ID allocation assigns NX001, NX002, NX003...")
    void testSequentialIdAllocation() {
        String id1 = stateManager.allocateNextId(TimerScope.USER, "backup-db");
        assertThat(id1).isEqualTo("NX001");

        String id2 = stateManager.allocateNextId(TimerScope.USER, "sync-s3");
        assertThat(id2).isEqualTo("NX002");

        String id3 = stateManager.allocateNextId(TimerScope.USER, "clean-tmp");
        assertThat(id3).isEqualTo("NX003");
    }

    @Test
    @DisplayName("Registers and resolves timer by ID, name, full identifier, or unit filename")
    void testRegisterAndResolve() {
        TimerStateManager.TimerRecord record = new TimerStateManager.TimerRecord(
                "NX001",
                "backup-db",
                "NX001-backup-db",
                "daily",
                "Every day at 00:00:00",
                "Daily DB backup",
                TimerScope.USER,
                tempDir.resolve("backup.sh").toString(),
                tempDir.resolve("nexus-nx001-backup-db.timer").toString(),
                tempDir.resolve("nexus-nx001-backup-db.service").toString(),
                Instant.now()
        );

        stateManager.registerTimer(TimerScope.USER, record);

        // Resolve by exact ID
        assertThat(stateManager.resolveTimer(TimerScope.USER, "NX001")).contains(record);
        // Resolve by lowercase ID
        assertThat(stateManager.resolveTimer(TimerScope.USER, "nx001")).contains(record);
        // Resolve by task name
        assertThat(stateManager.resolveTimer(TimerScope.USER, "backup-db")).contains(record);
        // Resolve by full identifier
        assertThat(stateManager.resolveTimer(TimerScope.USER, "NX001-backup-db")).contains(record);
        // Resolve by unit file base name
        assertThat(stateManager.resolveTimer(TimerScope.USER, "nexus-nx001-backup-db")).contains(record);
        // Resolve by unit file name with extension
        assertThat(stateManager.resolveTimer(TimerScope.USER, "nexus-nx001-backup-db.timer")).contains(record);
    }

    @Test
    @DisplayName("Unregisters timer cleanly")
    void testUnregister() {
        TimerStateManager.TimerRecord record = new TimerStateManager.TimerRecord(
                "NX001",
                "backup-db",
                "NX001-backup-db",
                "daily",
                "Every day",
                "",
                TimerScope.USER,
                "/tmp/script.sh",
                "/tmp/unit.timer",
                "/tmp/unit.service",
                Instant.now()
        );

        stateManager.registerTimer(TimerScope.USER, record);
        assertThat(stateManager.resolveTimer(TimerScope.USER, "NX001")).isPresent();

        var removed = stateManager.unregisterTimer(TimerScope.USER, "backup-db");
        assertThat(removed).isPresent();
        assertThat(stateManager.resolveTimer(TimerScope.USER, "NX001")).isEmpty();
    }

    @Test
    @DisplayName("StateFile and TimerRecord serialization roundtrip parses accurately via parseStateNode")
    void testSerializationRoundtrip() {
        TimerStateManager.TimerRecord record = new TimerStateManager.TimerRecord(
                "NX001",
                "test1",
                "NX001-test1",
                "every 1m",
                "Every 1 minutes",
                "Automated test task",
                TimerScope.USER,
                tempDir.resolve("scripts").resolve("NX001-test1.sh").toString(),
                tempDir.resolve("nexus-nx001-test1.timer").toString(),
                tempDir.resolve("nexus-nx001-test1.service").toString(),
                Instant.now()
        );

        stateManager.registerTimer(TimerScope.USER, record);

        TimerStateManager.StateFile loaded = stateManager.loadState(TimerScope.USER);
        assertThat(loaded.timers()).containsKey("NX001");
        TimerStateManager.TimerRecord loadedRec = loaded.timers().get("NX001");
        assertThat(loadedRec.id()).isEqualTo("NX001");
        assertThat(loadedRec.name()).isEqualTo("test1");
        assertThat(loadedRec.fullIdentifier()).isEqualTo("NX001-test1");
        assertThat(loadedRec.schedule()).isEqualTo("every 1m");
        assertThat(loadedRec.scriptPath()).contains("NX001-test1.sh");
    }

    @Test
    @DisplayName("ScheduledTaskManager resolves script path with fallback to matching prefix")
    void testResolveScriptPathFallback() throws java.io.IOException {
        Path scriptsDir = tempDir.resolve("scripts");
        Files.createDirectories(scriptsDir);
        Path scriptFile = scriptsDir.resolve("NX001-test1.sh");
        Files.writeString(scriptFile, "#!/bin/bash\necho hello\n");

        ScheduledTaskProvider mockProvider = Mockito.mock(ScheduledTaskProvider.class);
        Mockito.when(mockProvider.resolveScriptPath(Mockito.anyString(), Mockito.any()))
                .thenReturn(scriptsDir.resolve("nx001.sh"));

        ScheduledTaskManager manager = new ScheduledTaskManager(List.of(mockProvider), stateManager);

        // When not in state, it scans scripts directory and finds NX001-test1.sh
        Path resolved = manager.resolveScriptPath("nx001", TimerScope.USER);
        assertThat(resolved).isEqualTo(scriptFile);
    }
}
