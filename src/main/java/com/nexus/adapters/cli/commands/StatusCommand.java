package com.nexus.adapters.cli.commands;

import com.nexus.adapters.cli.NexusCliRootCommand;
import com.nexus.adapters.ipc.DaemonIpcClient;
import com.nexus.adapters.ipc.DaemonIpcServer;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.util.concurrent.Callable;

/**
 * CLI command to inspect the operational state of the Nexus background daemon.
 */
@Component
@Command(
    name = "status",
    description = "Displays the operational status and health of the Nexus daemon",
    mixinStandardHelpOptions = true
)
public class StatusCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    private final DaemonIpcClient ipcClient;

    public StatusCommand(DaemonIpcClient ipcClient) {
        this.ipcClient = ipcClient;
    }

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();
        NexusCliRootCommand root = spec.root().commandLine().getCommand();
        boolean json = root != null && root.isJson();

        try {
            String jsonStatus = ipcClient.sendCommand("STATUS", DaemonIpcServer.DEFAULT_PORT);
            if (json) {
                out.println(jsonStatus);
            } else {
                out.println("\u001B[1;36m=== Nexus Daemon Status ===\u001B[0m");
                out.println("  State: \u001B[32mRUNNING\u001B[0m");
                out.println("  " + jsonStatus);
            }
            return 0;
        } catch (Exception e) {
            if (json) {
                out.println("{\"status\":\"STOPPED\",\"error\":\"" + e.getMessage() + "\"}");
            } else {
                out.println("\u001B[1;36m=== Nexus Daemon Status ===\u001B[0m");
                out.println("  State: \u001B[31mSTOPPED\u001B[0m (Daemon is not running or IPC unreachable)");
            }
            return 0;
        }
    }
}
