package com.nexus.adapters.cli.commands;

import com.nexus.adapters.cli.NexusCliRootCommand;
import com.nexus.adapters.ipc.DaemonIpcClient;
import com.nexus.adapters.ipc.DaemonIpcServer;
import com.nexus.adapters.telegram.TelegramBotAdapter;
import com.nexus.config.NexusConfigResolver;
import com.nexus.config.NexusProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.util.concurrent.Callable;

/**
 * CLI command group for managing the Nexus background daemon service (start, stop, status).
 */
@Component
@Command(
    name = "daemon",
    aliases = {"d"},
    description = "Manage the Nexus background daemon service (start, stop, status)",
    subcommands = {
        DaemonCommand.StartSubcommand.class,
        DaemonCommand.StopSubcommand.class,
        DaemonCommand.StatusSubcommand.class
    },
    mixinStandardHelpOptions = true
)
public class DaemonCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(DaemonCommand.class);

    @Spec
    private CommandSpec spec;

    private final DaemonIpcServer ipcServer;
    private final DaemonIpcClient ipcClient;
    private final TelegramBotAdapter telegramBotAdapter;
    private final NexusProperties properties;

    public DaemonCommand(DaemonIpcServer ipcServer,
                         DaemonIpcClient ipcClient,
                         TelegramBotAdapter telegramBotAdapter,
                         NexusProperties properties) {
        this.ipcServer = ipcServer;
        this.ipcClient = ipcClient;
        this.telegramBotAdapter = telegramBotAdapter;
        this.properties = properties;
    }

    public DaemonIpcServer getIpcServer() {
        return ipcServer;
    }

    public DaemonIpcClient getIpcClient() {
        return ipcClient;
    }

    public TelegramBotAdapter getTelegramBotAdapter() {
        return telegramBotAdapter;
    }

    public NexusProperties getProperties() {
        return properties;
    }

    @Override
    public Integer call() {
        if (spec != null) {
            spec.commandLine().usage(spec.commandLine().getOut());
        }
        return 0;
    }

    @Component
    @Command(
        name = "start",
        description = "Starts the Nexus background daemon service with Telegram and IPC enabled",
        mixinStandardHelpOptions = true
    )
    public static class StartSubcommand implements Callable<Integer> {

        @Spec
        private CommandSpec spec;

        @ParentCommand
        private DaemonCommand parent;

        @Option(names = {"-p", "--port"}, description = "Local IPC port", defaultValue = "29786")
        private int port;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            PrintWriter err = spec.commandLine().getErr();
            DaemonIpcServer srv = parent.getIpcServer();
            TelegramBotAdapter tgAdapter = parent.getTelegramBotAdapter();
            NexusProperties props = parent.getProperties();

            try {
                out.println("\u001B[1;36mStarting Nexus Host Management Daemon...\u001B[0m");

                String configPath = NexusConfigResolver.getResolvedConfigPath();
                if (configPath != null) {
                    out.printf("  ✓ Configuration : %s%n", configPath);
                } else {
                    out.println("  ℹ Configuration : Embedded defaults (no config file specified or found)");
                }

                // Start Local IPC
                srv.start(port);
                out.printf("  ✓ Local IPC     : 127.0.0.1:%d (authenticated)%n", port);

                // Handle Telegram Bot Adapter
                NexusProperties.TelegramConfig tg = props.getTelegram();
                if (tg == null || !tg.isEnabled()) {
                    out.println("  ⚠ Telegram bot  : DISABLED (set 'nexus.telegram.enabled: true' in config to activate)");
                    log.info("Telegram bot is disabled in configuration");
                } else if (tg.getBotToken() == null || tg.getBotToken().isBlank()) {
                    out.println("  ✗ Telegram bot  : FAILED (missing or empty 'bot-token' in configuration)");
                    log.error("Telegram bot is enabled but bot-token is empty");
                } else {
                    if (!tgAdapter.isRunning()) {
                        tgAdapter.start();
                    }
                    if (tgAdapter.isRunning()) {
                        out.printf("  ✓ Telegram bot  : ACTIVE (polling for authorized chat ID: %d)%n", tg.getAllowedChatId());
                        log.info("Telegram bot adapter is running and polling for chat ID {}", tg.getAllowedChatId());
                    } else {
                        out.println("  ✗ Telegram bot  : FAILED to start (see logs/nexus.log for details)");
                    }
                }

                out.println("  ✓ Application logs: logs/nexus.log");
                out.println("  ✓ Audit trail     : logs/nexus-audit.log");
                out.println("  Press Ctrl+C or run 'nexus daemon stop' to terminate.");

                // Keep main daemon thread alive
                while (srv.isRunning()) {
                    Thread.sleep(1000);
                }
                return 0;
            } catch (InterruptedException e) {
                out.println("\nStopping daemon...");
                shutdownServices(srv, tgAdapter);
                return 0;
            } catch (Exception e) {
                err.println("\u001B[31mFailed to start daemon: " + e.getMessage() + "\u001B[0m");
                shutdownServices(srv, tgAdapter);
                return 1;
            }
        }

        private void shutdownServices(DaemonIpcServer srv, TelegramBotAdapter tgAdapter) {
            try {
                if (srv != null && srv.isRunning()) srv.stop();
            } catch (Exception ignored) {}
            try {
                if (tgAdapter != null && tgAdapter.isRunning()) tgAdapter.stop();
            } catch (Exception ignored) {}
        }
    }

    @Component
    @Command(
        name = "stop",
        description = "Requests graceful shutdown of the running Nexus daemon",
        mixinStandardHelpOptions = true
    )
    public static class StopSubcommand implements Callable<Integer> {

        @Spec
        private CommandSpec spec;

        @ParentCommand
        private DaemonCommand parent;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            PrintWriter err = spec.commandLine().getErr();

            try {
                String response = parent.getIpcClient().sendCommand("STOP", DaemonIpcServer.DEFAULT_PORT);
                out.println("\u001B[32m✓ Sent shutdown command to Nexus daemon: " + response + "\u001B[0m");
                return 0;
            } catch (Exception e) {
                err.println("\u001B[31mFailed to stop daemon: " + e.getMessage() + "\u001B[0m");
                return 1;
            }
        }
    }

    @Component
    @Command(
        name = "status",
        description = "Displays the operational status and health of the Nexus daemon",
        mixinStandardHelpOptions = true
    )
    public static class StatusSubcommand implements Callable<Integer> {

        @Spec
        private CommandSpec spec;

        @ParentCommand
        private DaemonCommand parent;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            PrintWriter err = spec.commandLine().getErr();
            boolean json = isJsonRequested(spec);

            try {
                String jsonStatus = parent.getIpcClient().sendCommand("STATUS", DaemonIpcServer.DEFAULT_PORT);
                if (json) {
                    out.println(jsonStatus);
                } else {
                    out.println("\u001B[1;36m=== Nexus Daemon Status ===\u001B[0m");
                    out.println("  State: \u001B[32mRUNNING\u001B[0m");
                    out.println("  " + jsonStatus);
                }
                return 0;
            } catch (Exception e) {
                if (json) {
                    out.println("{\"status\":\"STOPPED\",\"error\":\"" + e.getMessage() + "\"}");
                } else {
                    out.println("\u001B[1;36m=== Nexus Daemon Status ===\u001B[0m");
                    out.println("  State: \u001B[31mSTOPPED\u001B[0m (Daemon is not running or IPC unreachable)");
                }
                return 0;
            }
        }
    }

    private static boolean isJsonRequested(CommandSpec spec) {
        if (spec != null && spec.root() != null && spec.root().commandLine().getCommand() instanceof NexusCliRootCommand root) {
            return root.isJson();
        }
        return false;
    }
}
