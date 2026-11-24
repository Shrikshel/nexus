package com.nexus.adapters.cli.commands;

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
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.util.concurrent.Callable;

/**
 * CLI command to start Nexus in daemon mode.
 */
@Component
@Command(
    name = "start",
    aliases = {"daemon"},
    description = "Starts the Nexus background daemon service with Telegram and IPC enabled",
    mixinStandardHelpOptions = true
)
public class StartCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(StartCommand.class);

    @Spec
    private CommandSpec spec;

    @Option(names = {"-p", "--port"}, description = "Local IPC port", defaultValue = "29786")
    private int port;

    private final DaemonIpcServer ipcServer;
    private final TelegramBotAdapter telegramBotAdapter;
    private final NexusProperties properties;

    public StartCommand(DaemonIpcServer ipcServer, TelegramBotAdapter telegramBotAdapter, NexusProperties properties) {
        this.ipcServer = ipcServer;
        this.telegramBotAdapter = telegramBotAdapter;
        this.properties = properties;
    }

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();

        if (!"root".equalsIgnoreCase(System.getProperty("user.name"))) {
            err.println("\u001B[31m✗ Error: The Nexus daemon must run as root.\u001B[0m");
            err.println("  Running in user scope is restricted to prevent Telegram bot polling conflicts (HTTP 409).");
            err.println("  👉 Please run with sudo:");
            err.println("     sudo nexus start");
            return 1;
        }

        try {
            out.println("\u001B[1;36mStarting Nexus Host Management Daemon as root...\u001B[0m");

            String configPath = NexusConfigResolver.getResolvedConfigPath();
            if (configPath != null) {
                out.printf("  ✓ Configuration : %s%n", configPath);
            } else {
                out.println("  ℹ Configuration : Embedded defaults (no config file specified or found)");
            }

            // Start Local IPC
            ipcServer.start(port);
            out.printf("  ✓ Local IPC     : 127.0.0.1:%d (authenticated)%n", port);

            // Handle Telegram Bot Adapter
            NexusProperties.TelegramConfig tg = properties.getTelegram();
            if (tg == null || !tg.isEnabled()) {
                out.println("  ⚠ Telegram bot  : DISABLED (set 'nexus.telegram.enabled: true' in config to activate)");
                log.info("Telegram bot is disabled in configuration");
            } else if (tg.getBotToken() == null || tg.getBotToken().isBlank()) {
                out.println("  ✗ Telegram bot  : FAILED (missing or empty 'bot-token' in configuration)");
                log.error("Telegram bot is enabled but bot-token is empty");
            } else {
                if (!telegramBotAdapter.isRunning()) {
                    telegramBotAdapter.start();
                }
                if (telegramBotAdapter.isRunning()) {
                    out.printf("  ✓ Telegram bot  : ACTIVE (polling for authorized chat ID: %d)%n", tg.getAllowedChatId());
                    log.info("Telegram bot adapter is running and polling for chat ID {}", tg.getAllowedChatId());
                } else {
                    out.println("  ✗ Telegram bot  : FAILED to start (see logs/nexus.log for details)");
                }
            }

            out.println("  ✓ Application logs: logs/nexus.log");
            out.println("  ✓ Audit trail     : logs/nexus-audit.log");
            out.println("  Press Ctrl+C or run 'nexus stop' to terminate.");

            // Keep main daemon thread alive
            while (ipcServer.isRunning()) {
                Thread.sleep(1000);
            }
            return 0;
        } catch (InterruptedException e) {
            out.println("\nStopping daemon...");
            shutdownServices();
            return 0;
        } catch (Exception e) {
            err.println("\u001B[31mFailed to start daemon: " + e.getMessage() + "\u001B[0m");
            shutdownServices();
            return 1;
        }
    }

    private void shutdownServices() {
        try {
            if (ipcServer != null && ipcServer.isRunning()) {
                ipcServer.stop();
            }
        } catch (Exception ignored) {}

        try {
            if (telegramBotAdapter != null && telegramBotAdapter.isRunning()) {
                telegramBotAdapter.stop();
            }
        } catch (Exception ignored) {}
    }
}
