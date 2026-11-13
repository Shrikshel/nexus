package com.nexus.adapters.cli.commands;

import com.nexus.adapters.cli.CliExecutionBridge;
import com.nexus.adapters.cli.NexusCliRootCommand;
import com.nexus.capabilities.system.SystemUptimeCapability;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.util.concurrent.Callable;

/**
 * CLI command to inspect host and agent uptime.
 */
@Component
@Command(
    name = "uptime",
    description = "Displays system host uptime, agent process uptime, and boot timestamps",
    mixinStandardHelpOptions = true
)
public class UptimeCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    private final CliExecutionBridge bridge;

    public UptimeCommand(CliExecutionBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public Integer call() {
        NexusCliRootCommand root = spec.root().commandLine().getCommand();
        boolean json = root != null && root.isJson();
        return bridge.execute(
                SystemUptimeCapability.ID,
                null,
                json,
                spec.commandLine().getOut(),
                spec.commandLine().getErr()
        );
    }
}
