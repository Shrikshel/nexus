package com.nexus.platform.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogColorizerTest {

    @Test
    @DisplayName("Colorizes standard application log line with timestamp, thread, level and logger")
    void testColorizeStandardAppLog() {
        String line = "2026-11-24 07:15:52.152 [main] INFO  c.n.a.telegram.TelegramBotAdapter - Executing command SUCCESS";
        String colorized = LogColorizer.colorize(line);

        assertThat(colorized).contains(LogColorizer.DARK_GRAY + "2026-11-24 07:15:52.152" + LogColorizer.RESET);
        assertThat(colorized).contains(LogColorizer.MAGENTA + "[main]" + LogColorizer.RESET);
        assertThat(colorized).contains(LogColorizer.LEVEL_INFO + "INFO " + LogColorizer.RESET);
        assertThat(colorized).contains(LogColorizer.BLUE + "c.n.a.telegram.TelegramBotAdapter" + LogColorizer.RESET);
        assertThat(colorized).contains(LogColorizer.LEVEL_INFO + "SUCCESS" + LogColorizer.RESET);
    }

    @Test
    @DisplayName("Colorizes ERROR level with bold red")
    void testColorizeErrorLevel() {
        String line = "2026-11-24 07:16:03.920 [main] ERROR c.n.core.ExecutionEngine - Operation FAILED";
        String colorized = LogColorizer.colorize(line);

        assertThat(colorized).contains(LogColorizer.LEVEL_ERROR + "ERROR" + LogColorizer.RESET);
        assertThat(colorized).contains(LogColorizer.LEVEL_ERROR + "FAILED" + LogColorizer.RESET);
    }

    @Test
    @DisplayName("Colorizes WARN level with bold yellow")
    void testColorizeWarnLevel() {
        String line = "2026-11-24 07:16:03.918 [main] WARN  c.n.c.system.SystemReboot - Reboot in 5s";
        String colorized = LogColorizer.colorize(line);

        assertThat(colorized).contains(LogColorizer.LEVEL_WARN + "WARN " + LogColorizer.RESET);
    }

    @Test
    @DisplayName("Colorizes DEBUG level with cyan")
    void testColorizeDebugLevel() {
        String line = "2026-11-24 07:16:03.918 [poller] DEBUG c.n.a.ipc.IpcServer - Ping received";
        String colorized = LogColorizer.colorize(line);

        assertThat(colorized).contains(LogColorizer.LEVEL_DEBUG + "DEBUG" + LogColorizer.RESET);
    }

    @Test
    @DisplayName("Colorizes audit log lines")
    void testColorizeAuditLog() {
        String line = "2026-11-24 07:15:52.152 AUDIT EVENT: user=admin action=REBOOT status=SUCCESS";
        String colorized = LogColorizer.colorize(line);

        assertThat(colorized).contains(LogColorizer.DARK_GRAY + "2026-11-24 07:15:52.152" + LogColorizer.RESET);
        assertThat(colorized).contains(LogColorizer.LEVEL_INFO + "SUCCESS" + LogColorizer.RESET);
    }

    @Test
    @DisplayName("Colorizes stack trace elements cleanly")
    void testColorizeStackTrace() {
        String stackLine = "\tat com.nexus.Main.main(Main.java:25)";
        String colorized = LogColorizer.colorize(stackLine);

        assertThat(colorized).contains(LogColorizer.DARK_GRAY + "\tat " + LogColorizer.RESET);

        String causedBy = "Caused by: java.io.IOException: Disk full";
        String causedColorized = LogColorizer.colorize(causedBy);
        assertThat(causedColorized).contains(LogColorizer.LEVEL_ERROR + "Caused by: " + LogColorizer.RESET);
    }

    @Test
    @DisplayName("Colorizes journalctl prefixed lines")
    void testColorizeJournalctlLine() {
        String line = "Nov 24 07:15:52 server nexus[1234]: 2026-11-24 07:15:52.152 [main] INFO  c.n.Main - Running ACTIVE";
        String colorized = LogColorizer.colorize(line);

        assertThat(colorized).contains(LogColorizer.LEVEL_INFO + "INFO " + LogColorizer.RESET);
        assertThat(colorized).contains(LogColorizer.LEVEL_INFO + "ACTIVE" + LogColorizer.RESET);
    }
}
