package com.nexus.adapters.cli.commands;

import com.nexus.adapters.cli.CliExecutionBridge;
import com.nexus.adapters.cli.NexusCliRootCommand;
import com.nexus.capabilities.network.tailscale.TailscaleDownCapability;
import com.nexus.capabilities.network.tailscale.TailscaleStatusCapability;
import com.nexus.capabilities.network.tailscale.TailscaleUpCapability;
import com.nexus.capabilities.network.tailscale.dto.TailscaleOperationRequest;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.util.concurrent.Callable;

/**
 * CLI command group for inspecting and controlling the Tailscale VPN connection.
 */
@Component
@Command(
    name = "tailscale",
    aliases = {"ts"},
    description = "Inspect and control Tailscale VPN mesh network (status, up, down)",
    subcommands = {
        TailscaleCommand.StatusSubcommand.class,
        TailscaleCommand.UpSubcommand.class,
        TailscaleCommand.DownSubcommand.class
    },
    mixinStandardHelpOptions = true
)
public class TailscaleCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    private static CliExecutionBridge bridge;

    public TailscaleCommand(CliExecutionBridge bridge) {
        TailscaleCommand.bridge = bridge;
    }

    @Override
    public Integer call() {
        // Default action is status
        NexusCliRootCommand root = spec != null && spec.root() != null ? spec.root().commandLine().getCommand() : null;
        boolean json = root != null && root.isJson();
        PrintWriter out = spec != null ? spec.commandLine().getOut() : new PrintWriter(System.out, true);
        PrintWriter err = spec != null ? spec.commandLine().getErr() : new PrintWriter(System.err, true);
        return bridge.execute(TailscaleStatusCapability.ID, null, json, out, err);
    }

    @Component
    @Command(name = "status", description = "Query local Tailscale VPN connection state and peers", mixinStandardHelpOptions = true)
    public static class StatusSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @Override
        public Integer call() {
            NexusCliRootCommand root = spec != null && spec.root() != null ? spec.root().commandLine().getCommand() : null;
            boolean json = root != null && root.isJson();
            PrintWriter out = spec != null ? spec.commandLine().getOut() : new PrintWriter(System.out, true);
            PrintWriter err = spec != null ? spec.commandLine().getErr() : new PrintWriter(System.err, true);
            return bridge.execute(TailscaleStatusCapability.ID, null, json, out, err);
        }
    }

    @Component
    @Command(name = "up", description = "Connect to Tailscale and bring VPN up (requires sudo)", mixinStandardHelpOptions = true)
    public static class UpSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @Option(names = {"--accept-routes"}, description = "Accept subnet routes advertised by other nodes")
        private boolean acceptRoutes;

        @Option(names = {"--ssh"}, description = "Run Tailscale SSH server on this host")
        private boolean ssh;

        @Option(names = {"--exit-node"}, description = "Route internet traffic through specified exit node")
        private String exitNode;

        @Option(names = {"--auth-key"}, description = "Auth key for non-interactive node login")
        private String authKey;

        @Option(names = {"--hostname"}, description = "Custom hostname override for this node")
        private String hostname;

        @Option(names = {"--reset"}, description = "Reset unspecified settings to their default values")
        private boolean reset;

        @Override
        public Integer call() {
            NexusCliRootCommand root = spec != null && spec.root() != null ? spec.root().commandLine().getCommand() : null;
            boolean json = root != null && root.isJson();
            PrintWriter out = spec != null ? spec.commandLine().getOut() : new PrintWriter(System.out, true);
            PrintWriter err = spec != null ? spec.commandLine().getErr() : new PrintWriter(System.err, true);

            TailscaleOperationRequest request = new TailscaleOperationRequest(
                    acceptRoutes, ssh, exitNode, authKey, hostname, reset
            );
            return bridge.execute(TailscaleUpCapability.ID, request, json, out, err);
        }
    }

    @Component
    @Command(name = "down", description = "Disconnect from Tailscale and tear VPN down (requires sudo)", mixinStandardHelpOptions = true)
    public static class DownSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @Override
        public Integer call() {
            NexusCliRootCommand root = spec != null && spec.root() != null ? spec.root().commandLine().getCommand() : null;
            boolean json = root != null && root.isJson();
            PrintWriter out = spec != null ? spec.commandLine().getOut() : new PrintWriter(System.out, true);
            PrintWriter err = spec != null ? spec.commandLine().getErr() : new PrintWriter(System.err, true);
            return bridge.execute(TailscaleDownCapability.ID, null, json, out, err);
        }
    }
}
