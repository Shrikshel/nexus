package com.nexus.platform.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NexusLogManagerTest {

    private String originalSysProp;

    @BeforeEach
    void setUp() {
        NexusLogManager.reset();
        originalSysProp = System.getProperty(NexusLogManager.PROPERTY_LOG_DIR);
    }

    @AfterEach
    void tearDown() {
        NexusLogManager.reset();
        if (originalSysProp != null) {
            System.setProperty(NexusLogManager.PROPERTY_LOG_DIR, originalSysProp);
        } else {
            System.clearProperty(NexusLogManager.PROPERTY_LOG_DIR);
        }
    }

    @Test
    @DisplayName("Initializes canonical log directory and sets JVM system property")
    void testCanonicalLogDirectoryInitialization(@TempDir Path tempDir) {
        Path customLogs = tempDir.resolve("custom-logs");
        System.setProperty(NexusLogManager.PROPERTY_LOG_DIR, customLogs.toString());

        Path resolved = NexusLogManager.initEarly();
        assertThat(resolved).isEqualTo(customLogs.toAbsolutePath().normalize());
        assertThat(Files.exists(resolved)).isTrue();
        assertThat(System.getProperty(NexusLogManager.PROPERTY_LOG_DIR)).isEqualTo(resolved.toString());
    }

    @Test
    @DisplayName("Reads recent lines efficiently from a log file (tail behavior)")
    void testReadRecentLines(@TempDir Path tempDir) throws IOException {
        Path logFile = tempDir.resolve("test.log");
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 50; i++) {
            sb.append("Log line ").append(i).append("\n");
        }
        Files.writeString(logFile, sb.toString());

        List<String> last10 = NexusLogManager.readRecentLines(logFile, 10);
        assertThat(last10).hasSize(10);
        assertThat(last10.get(0)).isEqualTo("Log line 41");
        assertThat(last10.get(9)).isEqualTo("Log line 50");

        List<String> last5 = NexusLogManager.readRecentLines(logFile, 5);
        assertThat(last5).hasSize(5);
        assertThat(last5.get(0)).isEqualTo("Log line 46");
        assertThat(last5.get(4)).isEqualTo("Log line 50");
    }

    @Test
    @DisplayName("Lists active logs and rotated archives with accurate metadata")
    void testListLogFiles(@TempDir Path tempDir) throws IOException {
        NexusLogManager.setLogDirectory(tempDir);

        Path appLog = tempDir.resolve("nexus.log");
        Path auditLog = tempDir.resolve("nexus-audit.log");
        Path rotatedGz = tempDir.resolve("nexus.2026-11-20.0.log.gz");

        Files.writeString(appLog, "Hello application log\n");
        Files.writeString(auditLog, "Hello audit trail\n");
        Files.writeString(rotatedGz, "mock compressed archive\n");

        List<NexusLogManager.LogFileInfo> list = NexusLogManager.listLogFiles();
        assertThat(list).hasSize(3);

        assertThat(list.stream().anyMatch(f -> f.fileName().equals("nexus.log") && f.isActiveLog())).isTrue();
        assertThat(list.stream().anyMatch(f -> f.fileName().equals("nexus-audit.log") && f.isActiveLog())).isTrue();
        assertThat(list.stream().anyMatch(f -> f.fileName().equals("nexus.2026-11-20.0.log.gz") && f.isCompressedArchive())).isTrue();
    }

    @Test
    @DisplayName("Flushes and truncates active log files without deleting them")
    void testFlushLogs(@TempDir Path tempDir) throws IOException {
        NexusLogManager.setLogDirectory(tempDir);

        Path appLog = tempDir.resolve("nexus.log");
        Path auditLog = tempDir.resolve("nexus-audit.log");

        Files.writeString(appLog, "Some verbose application logs");
        Files.writeString(auditLog, "Some audit entries");

        assertThat(Files.size(appLog)).isGreaterThan(0);
        assertThat(Files.size(auditLog)).isGreaterThan(0);

        boolean flushed = NexusLogManager.flushLogs(true, true);
        assertThat(flushed).isTrue();

        assertThat(Files.size(appLog)).isEqualTo(0);
        assertThat(Files.size(auditLog)).isEqualTo(0);
    }
}
