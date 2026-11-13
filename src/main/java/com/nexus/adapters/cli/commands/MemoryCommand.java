package com.nexus.adapters.cli.commands;

import com.nexus.adapters.cli.CliExecutionBridge;
import com.nexus.adapters.cli.NexusCliRootCommand;
import com.nexus.capabilities.resource.MemoryCapability;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.util.concurrent.Callable;

/**
 * CLI command to inspect memory and swap utilization.
 */
@Component
@Command(
    name = "memory",
    aliases = {"mem"},
    description = "Displays physical RAM and swap space utilization",
    mixinStandardHelpOptions = true
)
public class MemoryCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    private final CliExecutionBridge bridge;

    public MemoryCommand(CliExecutionBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public Integer call() {
        NexusCliRootCommand root = spec.root().commandLine().getCommand();
        boolean json = root != null && root.isJson();
        return bridge.execute(
                MemoryCapability.ID,
                null,
                json,
                spec.commandLine().getOut(),
                spec.commandLine().getErr()
        );
    }
}
