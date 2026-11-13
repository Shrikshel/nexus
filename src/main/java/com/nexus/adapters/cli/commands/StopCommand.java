package com.nexus.adapters.cli.commands;

import com.nexus.adapters.ipc.DaemonIpcClient;
import com.nexus.adapters.ipc.DaemonIpcServer;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.util.concurrent.Callable;

/**
 * CLI command to request graceful shutdown of the background Nexus daemon.
 */
@Component
@Command(
    name = "stop",
    description = "Requests graceful shutdown of the running Nexus daemon",
    mixinStandardHelpOptions = true
)
public class StopCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    private final DaemonIpcClient ipcClient;

    public StopCommand(DaemonIpcClient ipcClient) {
        this.ipcClient = ipcClient;
    }

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();

        try {
            String response = ipcClient.sendCommand("STOP", DaemonIpcServer.DEFAULT_PORT);
            out.println("\u001B[32m✓ Sent shutdown command to Nexus daemon: " + response + "\u001B[0m");
            return 0;
        } catch (Exception e) {
            err.println("\u001B[31mFailed to stop daemon: " + e.getMessage() + "\u001B[0m");
            return 1;
        }
    }
}
