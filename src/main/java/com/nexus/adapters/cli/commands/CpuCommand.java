package com.nexus.adapters.cli.commands;

import com.nexus.adapters.cli.CliExecutionBridge;
import com.nexus.adapters.cli.NexusCliRootCommand;
import com.nexus.capabilities.resource.CpuCapability;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.util.concurrent.Callable;

/**
 * CLI command to inspect CPU utilization and system load.
 */
@Component
@Command(
    name = "cpu",
    description = "Displays CPU processor cores, load averages (1m, 5m, 15m), and utilization percentage",
    mixinStandardHelpOptions = true
)
public class CpuCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    private final CliExecutionBridge bridge;

    public CpuCommand(CliExecutionBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public Integer call() {
        NexusCliRootCommand root = spec.root().commandLine().getCommand();
        boolean json = root != null && root.isJson();
        return bridge.execute(
                CpuCapability.ID,
                null,
                json,
                spec.commandLine().getOut(),
                spec.commandLine().getErr()
        );
    }
}
