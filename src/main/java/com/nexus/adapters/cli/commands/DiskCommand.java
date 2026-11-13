package com.nexus.adapters.cli.commands;

import com.nexus.adapters.cli.CliExecutionBridge;
import com.nexus.adapters.cli.NexusCliRootCommand;
import com.nexus.capabilities.storage.DiskListCapability;
import com.nexus.capabilities.storage.DiskSmartCapability;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.util.concurrent.Callable;

/**
 * CLI command group for filesystem and physical drive management.
 */
@Component
@Command(
    name = "disk",
    description = "Inspect mounted filesystems and physical drive SMART health",
    subcommands = {
        DiskCommand.ListSubcommand.class,
        DiskCommand.SmartSubcommand.class
    },
    mixinStandardHelpOptions = true
)
public class DiskCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    private static CliExecutionBridge bridge;

    public DiskCommand(CliExecutionBridge bridge) {
        DiskCommand.bridge = bridge;
    }

    @Override
    public Integer call() {
        NexusCliRootCommand root = spec != null && spec.root() != null ? spec.root().commandLine().getCommand() : null;
        boolean json = root != null && root.isJson();
        PrintWriter out = spec != null ? spec.commandLine().getOut() : new PrintWriter(System.out, true);
        PrintWriter err = spec != null ? spec.commandLine().getErr() : new PrintWriter(System.err, true);
        return bridge.execute(
                DiskListCapability.ID,
                null,
                json,
                out,
                err
        );
    }

    @Component
    @Command(name = "list", description = "List all mounted filesystems and storage utilization", mixinStandardHelpOptions = true)
    public static class ListSubcommand implements Callable<Integer> {

        @Spec
        private CommandSpec spec;

        @Override
        public Integer call() {
            NexusCliRootCommand root = spec != null && spec.root() != null ? spec.root().commandLine().getCommand() : null;
            boolean json = root != null && root.isJson();
            PrintWriter out = spec != null ? spec.commandLine().getOut() : new PrintWriter(System.out, true);
            PrintWriter err = spec != null ? spec.commandLine().getErr() : new PrintWriter(System.err, true);
            return DiskCommand.bridge.execute(
                    DiskListCapability.ID,
                    null,
                    json,
                    out,
                    err
            );
        }
    }

    @Component
    @Command(name = "smart", description = "Query physical drive SMART health metrics", mixinStandardHelpOptions = true)
    public static class SmartSubcommand implements Callable<Integer> {

        @Spec
        private CommandSpec spec;

        @Parameters(index = "0", arity = "0..1", description = "Target drive device path (e.g. /dev/sda, /dev/nvme0)")
        private String device;

        @Override
        public Integer call() {
            NexusCliRootCommand root = spec != null && spec.root() != null ? spec.root().commandLine().getCommand() : null;
            boolean json = root != null && root.isJson();
            PrintWriter out = spec != null ? spec.commandLine().getOut() : new PrintWriter(System.out, true);
            PrintWriter err = spec != null ? spec.commandLine().getErr() : new PrintWriter(System.err, true);
            return DiskCommand.bridge.execute(
                    DiskSmartCapability.ID,
                    device,
                    json,
                    out,
                    err
            );
        }
    }
}
