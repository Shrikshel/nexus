package com.nexus.adapters.cli.commands;

import com.nexus.adapters.cli.CliExecutionBridge;
import com.nexus.adapters.cli.NexusCliRootCommand;
import com.nexus.capabilities.system.SystemInfoCapability;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.util.concurrent.Callable;

/**
 * CLI command to inspect host system specifications.
 */
@Component
@Command(
    name = "info",
    description = "Displays host system, architecture, kernel, CPU, and memory summary",
    mixinStandardHelpOptions = true
)
public class InfoCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    private final CliExecutionBridge bridge;

    public InfoCommand(CliExecutionBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public Integer call() {
        NexusCliRootCommand root = spec.root().commandLine().getCommand();
        boolean json = root != null && root.isJson();
        return bridge.execute(
                SystemInfoCapability.ID,
                null,
                json,
                spec.commandLine().getOut(),
                spec.commandLine().getErr()
        );
    }
}
