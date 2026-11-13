package com.nexus.adapters.cli.commands;

import com.nexus.adapters.cli.CliExecutionBridge;
import com.nexus.adapters.cli.NexusCliRootCommand;
import com.nexus.capabilities.network.NetworkInfoCapability;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.util.concurrent.Callable;

/**
 * CLI command to inspect network interfaces and assigned IP addresses.
 */
@Component
@Command(
    name = "network",
    aliases = {"net"},
    description = "Displays network interfaces, MAC addresses, MTU, and IP addresses",
    mixinStandardHelpOptions = true
)
public class NetworkCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    private final CliExecutionBridge bridge;

    public NetworkCommand(CliExecutionBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public Integer call() {
        NexusCliRootCommand root = spec.root().commandLine().getCommand();
        boolean json = root != null && root.isJson();
        return bridge.execute(
                NetworkInfoCapability.ID,
                null,
                json,
                spec.commandLine().getOut(),
                spec.commandLine().getErr()
        );
    }
}
