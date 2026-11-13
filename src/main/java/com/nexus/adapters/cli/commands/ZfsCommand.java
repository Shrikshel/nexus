package com.nexus.adapters.cli.commands;

import com.nexus.adapters.cli.CliExecutionBridge;
import com.nexus.adapters.cli.NexusCliRootCommand;
import com.nexus.capabilities.storage.zfs.*;
import com.nexus.capabilities.storage.zfs.dto.ZfsDatasetDetailRequest;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * CLI command group for inspecting and querying ZFS storage pools, datasets, and snapshots.
 */
@Component
@Command(
    name = "zfs",
    description = "Inspect and query ZFS storage pools, datasets, snapshots, and properties",
    subcommands = {
        ZfsCommand.PoolsSubcommand.class,
        ZfsCommand.StatusSubcommand.class,
        ZfsCommand.DatasetsSubcommand.class,
        ZfsCommand.SnapshotsSubcommand.class,
        ZfsCommand.GetSubcommand.class
    },
    mixinStandardHelpOptions = true
)
public class ZfsCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    private static CliExecutionBridge bridge;

    public ZfsCommand(CliExecutionBridge bridge) {
        ZfsCommand.bridge = bridge;
    }

    @Override
    public Integer call() {
        NexusCliRootCommand root = spec != null && spec.root() != null ? spec.root().commandLine().getCommand() : null;
        boolean json = root != null && root.isJson();
        PrintWriter out = spec != null ? spec.commandLine().getOut() : new PrintWriter(System.out, true);
        PrintWriter err = spec != null ? spec.commandLine().getErr() : new PrintWriter(System.err, true);
        return bridge.execute(ZfsPoolListCapability.ID, null, json, out, err);
    }

    @Component
    @Command(name = "pools", aliases = {"list"}, description = "List configured ZFS storage pools and health", mixinStandardHelpOptions = true)
    public static class PoolsSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @Override
        public Integer call() {
            NexusCliRootCommand root = spec != null && spec.root() != null ? spec.root().commandLine().getCommand() : null;
            boolean json = root != null && root.isJson();
            PrintWriter out = spec != null ? spec.commandLine().getOut() : new PrintWriter(System.out, true);
            PrintWriter err = spec != null ? spec.commandLine().getErr() : new PrintWriter(System.err, true);
            return bridge.execute(ZfsPoolListCapability.ID, null, json, out, err);
        }
    }

    @Component
    @Command(name = "status", description = "Query detailed ZFS pool health, scrub status, and vdev layout", mixinStandardHelpOptions = true)
    public static class StatusSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @Parameters(index = "0", description = "Specific pool name to inspect", arity = "0..1")
        private String poolName;

        @Override
        public Integer call() {
            NexusCliRootCommand root = spec != null && spec.root() != null ? spec.root().commandLine().getCommand() : null;
            boolean json = root != null && root.isJson();
            PrintWriter out = spec != null ? spec.commandLine().getOut() : new PrintWriter(System.out, true);
            PrintWriter err = spec != null ? spec.commandLine().getErr() : new PrintWriter(System.err, true);
            return bridge.execute(ZfsPoolStatusCapability.ID, poolName, json, out, err);
        }
    }

    @Component
    @Command(name = "datasets", aliases = {"ds"}, description = "List ZFS filesystem and volume datasets", mixinStandardHelpOptions = true)
    public static class DatasetsSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @Override
        public Integer call() {
            NexusCliRootCommand root = spec != null && spec.root() != null ? spec.root().commandLine().getCommand() : null;
            boolean json = root != null && root.isJson();
            PrintWriter out = spec != null ? spec.commandLine().getOut() : new PrintWriter(System.out, true);
            PrintWriter err = spec != null ? spec.commandLine().getErr() : new PrintWriter(System.err, true);
            return bridge.execute(ZfsDatasetListCapability.ID, null, json, out, err);
        }
    }

    @Component
    @Command(name = "snapshots", aliases = {"snap", "snaps"}, description = "List ZFS snapshots", mixinStandardHelpOptions = true)
    public static class SnapshotsSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @Parameters(index = "0", description = "Optional dataset hierarchy to filter snapshots", arity = "0..1")
        private String targetDataset;

        @Override
        public Integer call() {
            NexusCliRootCommand root = spec != null && spec.root() != null ? spec.root().commandLine().getCommand() : null;
            boolean json = root != null && root.isJson();
            PrintWriter out = spec != null ? spec.commandLine().getOut() : new PrintWriter(System.out, true);
            PrintWriter err = spec != null ? spec.commandLine().getErr() : new PrintWriter(System.err, true);
            return bridge.execute(ZfsSnapshotListCapability.ID, targetDataset, json, out, err);
        }
    }

    @Component
    @Command(name = "get", description = "Inspect detailed properties and configuration for a particular ZFS dataset", mixinStandardHelpOptions = true)
    public static class GetSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @Parameters(index = "0", description = "Dataset name to query (e.g. tank/data)")
        private String datasetName;

        @Parameters(index = "1..*", description = "Optional specific properties to query (e.g. compression, quota)", arity = "0..*")
        private List<String> properties;

        @Override
        public Integer call() {
            NexusCliRootCommand root = spec != null && spec.root() != null ? spec.root().commandLine().getCommand() : null;
            boolean json = root != null && root.isJson();
            PrintWriter out = spec != null ? spec.commandLine().getOut() : new PrintWriter(System.out, true);
            PrintWriter err = spec != null ? spec.commandLine().getErr() : new PrintWriter(System.err, true);

            ZfsDatasetDetailRequest req = new ZfsDatasetDetailRequest(datasetName, properties != null ? properties : List.of());
            return bridge.execute(ZfsDatasetDetailCapability.ID, req, json, out, err);
        }
    }
}
