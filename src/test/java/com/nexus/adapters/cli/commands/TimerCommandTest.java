package com.nexus.adapters.cli.commands;

import com.nexus.adapters.cli.CliExecutionBridge;
import com.nexus.capabilities.scheduler.timer.dto.TimerScope;
import com.nexus.platform.scheduler.ScheduledTaskManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class TimerCommandTest {

    private CliExecutionBridge bridge;
    private ScheduledTaskManager taskManager;
    private CommandLine commandLine;

    @BeforeEach
    void setUp() {
        bridge = Mockito.mock(CliExecutionBridge.class);
        taskManager = Mockito.mock(ScheduledTaskManager.class);
        TimerCommand timerCommand = new TimerCommand(bridge, taskManager);
        commandLine = new CommandLine(timerCommand);
    }

    @Test
    @DisplayName("Root timer command defaults to list capability")
    void testDefaultList() {
        when(bridge.execute(eq("timer.list"), any(), anyBoolean(), any(), any())).thenReturn(0);

        int exitCode = commandLine.execute();
        assertThat(exitCode).isEqualTo(0);
    }

    @Test
    @DisplayName("Timer create subcommand delegates with options")
    void testCreateSubcommand() {
        when(bridge.execute(eq("timer.create"), any(), anyBoolean(), any(), any())).thenReturn(0);
        when(taskManager.resolveScriptPath(anyString(), any())).thenReturn(Path.of("/tmp/NX001-backup.sh"));

        int exitCode = commandLine.execute("create", "backup-db", "-s", "every 2h", "-d", "Backup database");
        assertThat(exitCode).isEqualTo(0);
    }

    @Test
    @DisplayName("Timer status subcommand invokes timer.status capability")
    void testStatusSubcommand() {
        when(bridge.execute(eq("timer.status"), any(), anyBoolean(), any(), any())).thenReturn(0);

        int exitCode = commandLine.execute("status", "NX001");
        assertThat(exitCode).isEqualTo(0);
    }

    @Test
    @DisplayName("Timer run subcommand triggers execution")
    void testRunSubcommand() {
        when(bridge.execute(eq("timer.run"), any(), anyBoolean(), any(), any())).thenReturn(0);

        int exitCode = commandLine.execute("run", "NX001");
        assertThat(exitCode).isEqualTo(0);
    }

    @Test
    @DisplayName("Timer delete subcommand delegates deletion")
    void testDeleteSubcommand() {
        when(bridge.execute(eq("timer.delete"), any(), anyBoolean(), any(), any())).thenReturn(0);

        int exitCode = commandLine.execute("delete", "NX001", "--purge-script", "-y");
        assertThat(exitCode).isEqualTo(0);
    }
}
