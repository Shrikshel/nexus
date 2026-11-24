package com.nexus.adapters.cli.commands;

import com.nexus.adapters.ipc.DaemonIpcClient;
import com.nexus.adapters.ipc.DaemonIpcServer;
import com.nexus.adapters.telegram.TelegramBotAdapter;
import com.nexus.config.NexusProperties;
import com.nexus.platform.service.DaemonServiceManager;
import com.nexus.platform.service.ServiceOperationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

class DaemonCommandTest {

    private DaemonServiceManager serviceManager;
    private DaemonIpcServer ipcServer;
    private DaemonIpcClient ipcClient;
    private TelegramBotAdapter telegramBotAdapter;
    private NexusProperties properties;
    private CommandLine commandLine;
    private StringWriter outWriter;
    private StringWriter errWriter;

    @BeforeEach
    void setUp() {
        serviceManager = Mockito.mock(DaemonServiceManager.class);
        ipcServer = Mockito.mock(DaemonIpcServer.class);
        ipcClient = Mockito.mock(DaemonIpcClient.class);
        telegramBotAdapter = Mockito.mock(TelegramBotAdapter.class);
        properties = new NexusProperties();

        DaemonCommand daemonCommand = new DaemonCommand(ipcServer, ipcClient, telegramBotAdapter, properties, serviceManager);
        commandLine = new CommandLine(daemonCommand);

        outWriter = new StringWriter();
        errWriter = new StringWriter();
        commandLine.setOut(new PrintWriter(outWriter));
        commandLine.setErr(new PrintWriter(errWriter));
    }

    @Test
    @DisplayName("Displays usage overview when executed without subcommands")
    void testUsageWithoutSubcommands() {
        int exitCode = commandLine.execute();
        assertThat(exitCode).isEqualTo(0);
        assertThat(outWriter.toString()).contains("Usage: daemon");
        assertThat(outWriter.toString()).contains("restart");
    }

    @Test
    @DisplayName("Restart subcommand fails cleanly when invoked by non-root user")
    void testRestartRequiresRoot() {
        if (!DaemonCommand.isRoot()) {
            int exitCode = commandLine.execute("restart");
            assertThat(exitCode).isEqualTo(1);
            assertThat(errWriter.toString()).contains("requires root privileges");
            assertThat(errWriter.toString()).contains("sudo nexus daemon restart");
        }
    }

    @Test
    @DisplayName("Reload alias maps to restart subcommand")
    void testReloadAliasMapsToRestart() {
        if (!DaemonCommand.isRoot()) {
            int exitCode = commandLine.execute("reload");
            assertThat(exitCode).isEqualTo(1);
            assertThat(errWriter.toString()).contains("requires root privileges");
        }
    }
}
