package com.nexus.adapters.cli.commands;

import com.nexus.adapters.cli.CliExecutionBridge;
import com.nexus.adapters.cli.NexusCliRootCommand;
import com.nexus.capabilities.meta.CapabilitiesDescribeCapability;
import com.nexus.capabilities.meta.CapabilitiesListCapability;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.util.concurrent.Callable;

/**
 * CLI command group for inspecting capabilities registered in Nexus.
 */
@Component
@Command(
    name = "capabilities",
    aliases = {"cap", "caps"},
    description = "Introspect registered agent capabilities and schemas",
    subcommands = {
        CapabilitiesCommand.ListSubcommand.class,
        CapabilitiesCommand.DescribeSubcommand.class
    },
    mixinStandardHelpOptions = true
)
public class CapabilitiesCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    private static CliExecutionBridge bridge;

    public CapabilitiesCommand(CliExecutionBridge bridge) {
        CapabilitiesCommand.bridge = bridge;
    }

    @Override
    public Integer call() {
        NexusCliRootCommand root = spec != null && spec.root() != null ? spec.root().commandLine().getCommand() : null;
        boolean json = root != null && root.isJson();
        PrintWriter out = spec != null ? spec.commandLine().getOut() : new PrintWriter(System.out, true);
        PrintWriter err = spec != null ? spec.commandLine().getErr() : new PrintWriter(System.err, true);
        return bridge.execute(
                CapabilitiesListCapability.ID,
                null,
                json,
                out,
                err
        );
    }

    @Component
    @Command(name = "list", description = "List all available agent capabilities", mixinStandardHelpOptions = true)
    public static class ListSubcommand implements Callable<Integer> {

        @Spec
        private CommandSpec spec;

        @Override
        public Integer call() {
            NexusCliRootCommand root = spec != null && spec.root() != null ? spec.root().commandLine().getCommand() : null;
            boolean json = root != null && root.isJson();
            PrintWriter out = spec != null ? spec.commandLine().getOut() : new PrintWriter(System.out, true);
            PrintWriter err = spec != null ? spec.commandLine().getErr() : new PrintWriter(System.err, true);
            return CapabilitiesCommand.bridge.execute(
                    CapabilitiesListCapability.ID,
                    null,
                    json,
                    out,
                    err
            );
        }
    }

    @Component
    @Command(name = "describe", description = "Describe a specific capability schema and permissions", mixinStandardHelpOptions = true)
    public static class DescribeSubcommand implements Callable<Integer> {

        @Spec
        private CommandSpec spec;

        @Parameters(index = "0", description = "ID of the capability to describe (e.g. sys.info, sys.uptime)")
        private String capabilityId;

        @Override
        public Integer call() {
            NexusCliRootCommand root = spec != null && spec.root() != null ? spec.root().commandLine().getCommand() : null;
            boolean json = root != null && root.isJson();
            PrintWriter out = spec != null ? spec.commandLine().getOut() : new PrintWriter(System.out, true);
            PrintWriter err = spec != null ? spec.commandLine().getErr() : new PrintWriter(System.err, true);
            return CapabilitiesCommand.bridge.execute(
                    CapabilitiesDescribeCapability.ID,
                    capabilityId,
                    json,
                    out,
                    err
            );
        }
    }
}
