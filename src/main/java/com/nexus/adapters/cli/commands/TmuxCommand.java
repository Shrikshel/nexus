package com.nexus.adapters.cli.commands;

import com.nexus.adapters.cli.CliExecutionBridge;
import com.nexus.adapters.cli.NexusCliRootCommand;
import com.nexus.capabilities.terminal.tmux.TmuxAttachSessionCapability;
import com.nexus.capabilities.terminal.tmux.TmuxKillSessionCapability;
import com.nexus.capabilities.terminal.tmux.TmuxListSessionsCapability;
import com.nexus.capabilities.terminal.tmux.TmuxNewSessionCapability;
import com.nexus.capabilities.terminal.tmux.dto.TmuxAttachSessionRequest;
import com.nexus.capabilities.terminal.tmux.dto.TmuxKillSessionRequest;
import com.nexus.capabilities.terminal.tmux.dto.TmuxNewSessionRequest;
import com.nexus.platform.TmuxLocator;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.util.concurrent.Callable;

/**
 * CLI command group for inspecting and managing tmux terminal multiplexer sessions.
 */
@Component
@Command(
    name = "tmux",
    description = "Manage tmux terminal multiplexer sessions (list, new, attach, kill)",
    subcommands = {
        TmuxCommand.ListSubcommand.class,
        TmuxCommand.NewSubcommand.class,
        TmuxCommand.AttachSubcommand.class,
        TmuxCommand.KillSubcommand.class
    },
    mixinStandardHelpOptions = true
)
public class TmuxCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    private static CliExecutionBridge bridge;

    public TmuxCommand(CliExecutionBridge bridge) {
        TmuxCommand.bridge = bridge;
    }

    @Override
    public Integer call() {
        NexusCliRootCommand root = spec != null && spec.root() != null ? spec.root().commandLine().getCommand() : null;
        boolean json = root != null && root.isJson();
        PrintWriter out = spec != null ? spec.commandLine().getOut() : new PrintWriter(System.out, true);
        PrintWriter err = spec != null ? spec.commandLine().getErr() : new PrintWriter(System.err, true);
        return bridge.execute(TmuxListSessionsCapability.ID, null, json, out, err);
    }

    @Component
    @Command(name = "list", aliases = {"ls"}, description = "List all active tmux sessions", mixinStandardHelpOptions = true)
    public static class ListSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @Override
        public Integer call() {
            NexusCliRootCommand root = spec != null && spec.root() != null ? spec.root().commandLine().getCommand() : null;
            boolean json = root != null && root.isJson();
            PrintWriter out = spec != null ? spec.commandLine().getOut() : new PrintWriter(System.out, true);
            PrintWriter err = spec != null ? spec.commandLine().getErr() : new PrintWriter(System.err, true);
            return bridge.execute(TmuxListSessionsCapability.ID, null, json, out, err);
        }
    }

    @Component
    @Command(name = "new", description = "Create a new detached tmux session", mixinStandardHelpOptions = true)
    public static class NewSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @Parameters(index = "0", description = "Name of the new session", arity = "0..1")
        private String sessionName;

        @Option(names = {"--dir"}, description = "Initial working directory for session")
        private String startDirectory;

        @Option(names = {"-n", "--window"}, description = "Initial window name")
        private String windowName;

        @Option(names = {"--cmd"}, description = "Command to execute inside session")
        private String command;

        @Override
        public Integer call() {
            NexusCliRootCommand root = spec != null && spec.root() != null ? spec.root().commandLine().getCommand() : null;
            boolean json = root != null && root.isJson();
            PrintWriter out = spec != null ? spec.commandLine().getOut() : new PrintWriter(System.out, true);
            PrintWriter err = spec != null ? spec.commandLine().getErr() : new PrintWriter(System.err, true);

            TmuxNewSessionRequest req = new TmuxNewSessionRequest(sessionName, startDirectory, windowName, command);
            return bridge.execute(TmuxNewSessionCapability.ID, req, json, out, err);
        }
    }

    @Component
    @Command(name = "attach", aliases = {"a"}, description = "Attach to an existing tmux session", mixinStandardHelpOptions = true)
    public static class AttachSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @Parameters(index = "0", description = "Name of the session to attach")
        private String sessionName;

        @Option(names = {"--dry-run"}, description = "Verify session existence without attaching terminal")
        private boolean dryRun;

        @Override
        public Integer call() {
            NexusCliRootCommand root = spec != null && spec.root() != null ? spec.root().commandLine().getCommand() : null;
            boolean json = root != null && root.isJson();
            PrintWriter out = spec != null ? spec.commandLine().getOut() : new PrintWriter(System.out, true);
            PrintWriter err = spec != null ? spec.commandLine().getErr() : new PrintWriter(System.err, true);

            if (!dryRun && !json && System.console() != null) {
                // Interactive attach in local terminal
                try {
                    String binary = TmuxLocator.findTmuxBinary();
                    Process process = new ProcessBuilder(binary, "attach-session", "-t", sessionName)
                            .inheritIO()
                            .start();
                    return process.waitFor();
                } catch (Exception e) {
                    err.println("Interactive attach failed: " + e.getMessage());
                }
            }

            TmuxAttachSessionRequest req = new TmuxAttachSessionRequest(sessionName);
            return bridge.execute(TmuxAttachSessionCapability.ID, req, json, out, err);
        }
    }

    @Component
    @Command(name = "kill", description = "Kill a specific tmux session or all sessions", mixinStandardHelpOptions = true)
    public static class KillSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @Parameters(index = "0", description = "Name of the session to terminate", arity = "0..1")
        private String sessionName;

        @Option(names = {"-a", "--all"}, description = "Kill all sessions (terminate tmux server)")
        private boolean killAll;

        @Override
        public Integer call() {
            NexusCliRootCommand root = spec != null && spec.root() != null ? spec.root().commandLine().getCommand() : null;
            boolean json = root != null && root.isJson();
            PrintWriter out = spec != null ? spec.commandLine().getOut() : new PrintWriter(System.out, true);
            PrintWriter err = spec != null ? spec.commandLine().getErr() : new PrintWriter(System.err, true);

            if (!killAll && (sessionName == null || sessionName.isBlank())) {
                err.println("Error: Specify a session name to kill or pass -a / --all to kill all sessions.");
                return 1;
            }

            TmuxKillSessionRequest req = new TmuxKillSessionRequest(sessionName, killAll);
            return bridge.execute(TmuxKillSessionCapability.ID, req, json, out, err);
        }
    }
}
