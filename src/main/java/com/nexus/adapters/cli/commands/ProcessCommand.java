package com.nexus.adapters.cli.commands;

import com.nexus.adapters.cli.CliExecutionBridge;
import com.nexus.adapters.cli.NexusCliRootCommand;
import com.nexus.capabilities.process.ProcessListCapability;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.util.concurrent.Callable;

/**
 * CLI command group for process telemetry.
 */
@Component
@Command(
    name = "process",
    aliases = {"proc", "processes"},
    description = "Inspect running system processes",
    subcommands = {
        ProcessCommand.ListSubcommand.class
    },
    mixinStandardHelpOptions = true
)
public class ProcessCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    private static CliExecutionBridge bridge;

    public ProcessCommand(CliExecutionBridge bridge) {
        ProcessCommand.bridge = bridge;
    }

    @Override
    public Integer call() {
        NexusCliRootCommand root = spec != null && spec.root() != null ? spec.root().commandLine().getCommand() : null;
        boolean json = root != null && root.isJson();
        PrintWriter out = spec != null ? spec.commandLine().getOut() : new PrintWriter(System.out, true);
        PrintWriter err = spec != null ? spec.commandLine().getErr() : new PrintWriter(System.err, true);
        return ProcessCommand.bridge.execute(ProcessListCapability.ID, null, json, out, err);
    }

    @Component
    @Command(name = "list", description = "List running processes", mixinStandardHelpOptions = true)
    public static class ListSubcommand implements Callable<Integer> {

        @Spec
        private CommandSpec spec;

        @Override
        public Integer call() {
            NexusCliRootCommand root = spec != null && spec.root() != null ? spec.root().commandLine().getCommand() : null;
            boolean json = root != null && root.isJson();
            PrintWriter out = spec != null ? spec.commandLine().getOut() : new PrintWriter(System.out, true);
            PrintWriter err = spec != null ? spec.commandLine().getErr() : new PrintWriter(System.err, true);
            return ProcessCommand.bridge.execute(ProcessListCapability.ID, null, json, out, err);
        }
    }
}
