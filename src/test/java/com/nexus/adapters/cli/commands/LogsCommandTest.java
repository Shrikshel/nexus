package com.nexus.adapters.cli.commands;

import com.nexus.platform.logging.NexusLogManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LogsCommandTest {

    private Path tempLogsDir;
    private String originalSysProp;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException {
        tempLogsDir = tempDir.resolve("nexus-logs");
        Files.createDirectories(tempLogsDir);
        originalSysProp = System.getProperty(NexusLogManager.PROPERTY_LOG_DIR);
        NexusLogManager.setLogDirectory(tempLogsDir);
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

    private CommandLine createLogsCommandLine(StringWriter sw) {
        LogsCommand cmd = new LogsCommand();
        CommandLine cl = new CommandLine(cmd);
        cl.setOut(new PrintWriter(sw));
        cl.setErr(new PrintWriter(sw));
        return cl;
    }

    @Test
    @DisplayName("Displays recent log lines for application log")
    void testLogsTail() throws IOException {
        Path appLog = tempLogsDir.resolve("nexus.log");
        Files.writeString(appLog, "INFO  App started\nWARN  Rate limit near\nINFO  Task scheduled\n");

        StringWriter sw = new StringWriter();
        CommandLine cmd = createLogsCommandLine(sw);

        int exitCode = cmd.execute("-n", "2");
        assertThat(exitCode).isEqualTo(0);

        String output = sw.toString();
        assertThat(output).contains("Application Logs (nexus.log)");
        assertThat(output).contains("WARN  Rate limit near");
        assertThat(output).contains("INFO  Task scheduled");
    }

    @Test
    @DisplayName("Displays recent log lines for audit trail (--audit)")
    void testLogsAudit() throws IOException {
        Path auditLog = tempLogsDir.resolve("nexus-audit.log");
        Files.writeString(auditLog, "AUDIT [SYSTEM_REBOOT] triggeredBy=admin\nAUDIT [TIMER_CREATE] id=NX001\n");

        StringWriter sw = new StringWriter();
        CommandLine cmd = createLogsCommandLine(sw);

        int exitCode = cmd.execute("--audit");
        assertThat(exitCode).isEqualTo(0);

        String output = sw.toString();
        assertThat(output).contains("Audit Trail (nexus-audit.log)");
        assertThat(output).contains("AUDIT [SYSTEM_REBOOT]");
    }

    @Test
    @DisplayName("Lists all active log files and rotated archives (--list)")
    void testLogsList() throws IOException {
        Files.writeString(tempLogsDir.resolve("nexus.log"), "active app log\n");
        Files.writeString(tempLogsDir.resolve("nexus.2026-11-20.0.log.gz"), "rotated compressed\n");

        StringWriter sw = new StringWriter();
        CommandLine cmd = createLogsCommandLine(sw);

        int exitCode = cmd.execute("--list");
        assertThat(exitCode).isEqualTo(0);

        String output = sw.toString();
        assertThat(output).contains("Nexus Log Files & Rotated Archives");
        assertThat(output).contains("nexus.log");
        assertThat(output).contains("nexus.2026-11-20.0.log.gz");
        assertThat(output).contains("ACTIVE LOG");
        assertThat(output).contains("ROTATED (GZ)");
    }

    @Test
    @DisplayName("Prints canonical log directory path (--path)")
    void testLogsPath() {
        StringWriter sw = new StringWriter();
        CommandLine cmd = createLogsCommandLine(sw);

        int exitCode = cmd.execute("--path");
        assertThat(exitCode).isEqualTo(0);
        assertThat(sw.toString().trim()).isEqualTo(tempLogsDir.toAbsolutePath().toString());
    }

    @Test
    @DisplayName("Flushes active log files (--flush)")
    void testLogsFlush() throws IOException {
        Path appLog = tempLogsDir.resolve("nexus.log");
        Path auditLog = tempLogsDir.resolve("nexus-audit.log");
        Files.writeString(appLog, "sample verbose log");
        Files.writeString(auditLog, "sample audit log");

        StringWriter sw = new StringWriter();
        CommandLine cmd = createLogsCommandLine(sw);

        int exitCode = cmd.execute("--flush");
        assertThat(exitCode).isEqualTo(0);
        assertThat(sw.toString()).contains("Nexus active log files successfully flushed and truncated");

        assertThat(Files.size(appLog)).isEqualTo(0);
        assertThat(Files.size(auditLog)).isEqualTo(0);
    }

    @Test
    @DisplayName("Colorizes output when --color flag is provided")
    void testLogsWithColorFlag() throws IOException {
        Path appLog = tempLogsDir.resolve("nexus.log");
        Files.writeString(appLog, "2026-11-24 07:15:52.152 [main] ERROR c.n.Main - Failure\n");

        StringWriter sw = new StringWriter();
        CommandLine cmd = createLogsCommandLine(sw);

        int exitCode = cmd.execute("--color");
        assertThat(exitCode).isEqualTo(0);
        String output = sw.toString();
        assertThat(output).contains(com.nexus.platform.logging.LogColorizer.LEVEL_ERROR);
    }

    @Test
    @DisplayName("Disables color when --no-color flag is provided")
    void testLogsWithNoColorFlag() throws IOException {
        Path appLog = tempLogsDir.resolve("nexus.log");
        Files.writeString(appLog, "2026-11-24 07:15:52.152 [main] ERROR c.n.Main - Failure\n");

        StringWriter sw = new StringWriter();
        CommandLine cmd = createLogsCommandLine(sw);

        int exitCode = cmd.execute("--no-color");
        assertThat(exitCode).isEqualTo(0);
        String output = sw.toString();
        assertThat(output).doesNotContain(com.nexus.platform.logging.LogColorizer.LEVEL_ERROR);
        assertThat(output).contains("ERROR");
    }
}
