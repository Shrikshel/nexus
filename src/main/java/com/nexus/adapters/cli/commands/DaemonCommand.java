package com.nexus.adapters.cli.commands;

import com.nexus.adapters.cli.NexusCliRootCommand;
import com.nexus.adapters.ipc.DaemonIpcClient;
import com.nexus.adapters.ipc.DaemonIpcServer;
import com.nexus.adapters.telegram.TelegramBotAdapter;
import com.nexus.config.NexusConfigResolver;
import com.nexus.config.NexusProperties;
import com.nexus.platform.service.DaemonServiceManager;
import com.nexus.platform.service.ServiceOperationResult;
import com.nexus.platform.service.ServiceStatusDetails;
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
 * CLI command group for managing the Nexus background daemon service and supervisor integration
 * (install, enable, disable, start, stop, status, uninstall).
 */
@Component
@Command(
    name = "daemon",
    aliases = {"d"},
    description = "Manage the Nexus background daemon service (install, enable, disable, start, stop, status, logs, uninstall)",
    subcommands = {
        DaemonCommand.InstallSubcommand.class,
        DaemonCommand.EnableSubcommand.class,
        DaemonCommand.DisableSubcommand.class,
        DaemonCommand.StartSubcommand.class,
        DaemonCommand.StopSubcommand.class,
        DaemonCommand.StatusSubcommand.class,
        DaemonCommand.LogsSubcommand.class,
        DaemonCommand.UninstallSubcommand.class
    },
    mixinStandardHelpOptions = true
)
public class DaemonCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(DaemonCommand.class);

    @Spec
    private CommandSpec spec;

    @ParentCommand
    private com.nexus.adapters.cli.NexusCliRootCommand root;

    private final DaemonIpcServer ipcServer;
    private final DaemonIpcClient ipcClient;
    private final TelegramBotAdapter telegramBotAdapter;
    private final NexusProperties properties;
    private final DaemonServiceManager serviceManager;

    public DaemonCommand(DaemonIpcServer ipcServer,
                         DaemonIpcClient ipcClient,
                         TelegramBotAdapter telegramBotAdapter,
                         NexusProperties properties,
                         DaemonServiceManager serviceManager) {
        this.ipcServer = ipcServer;
        this.ipcClient = ipcClient;
        this.telegramBotAdapter = telegramBotAdapter;
        this.properties = properties;
        this.serviceManager = serviceManager;
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

    public DaemonServiceManager getServiceManager() {
        return serviceManager;
    }

    public com.nexus.adapters.cli.NexusCliRootCommand getRoot() {
        return root;
    }

    @Override
    public Integer call() {
        if (spec != null) {
            spec.commandLine().usage(spec.commandLine().getOut());
        }
        return 0;
    }

    // 1. INSTALL
    @Component
    @Command(
        name = "install",
        aliases = {"setup"},
        description = "Installs and enables the Nexus daemon as an OS background service (systemd/launchd/TaskScheduler)",
        mixinStandardHelpOptions = true
    )
    public static class InstallSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @ParentCommand
        private DaemonCommand parent;

        @Option(names = {"--system"}, description = "Install as system-wide service (requires root/sudo)")
        private boolean systemScope;

        @Option(names = {"--no-enable"}, description = "Do not enable service at boot")
        private boolean noEnable;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            PrintWriter err = spec.commandLine().getErr();

            ServiceOperationResult res = parent.getServiceManager().installService(systemScope, !noEnable);
            if (!res.success()) {
                err.println("\u001B[31mFailed to install daemon service: " + res.message() + "\u001B[0m");
                return 1;
            }

            out.println("\u001B[32m✓ Nexus daemon service successfully installed" + (!noEnable ? " and enabled" : "") + "!\u001B[0m");
            out.println("  • Service Name:  " + res.serviceName());
            out.println("  • Unit Path:     " + res.unitPath());
            out.println("  • Scope:         " + (systemScope ? "system (multi-user)" : "user (zero-root user scope)"));
            out.println("  • Status:        " + (!noEnable ? "Installed & Enabled (Stopped)" : "Installed (Disabled)"));
            out.println("");
            out.println("  👉 Start the background daemon anytime using:");
            out.println("     nexus daemon start" + (systemScope ? " --system" : ""));

            if (parent.getServiceManager().isLingerAdvised(systemScope)) {
                String user = System.getProperty("user.name");
                out.println("");
                out.println("  💡 Tip: To allow the user service to persist after SSH logout on Linux, enable lingering:");
                out.println("     sudo loginctl enable-linger " + user);
            }
            return 0;
        }
    }

    // 2. ENABLE
    @Component
    @Command(
        name = "enable",
        description = "Enables the Nexus daemon service to auto-start on boot",
        mixinStandardHelpOptions = true
    )
    public static class EnableSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @ParentCommand
        private DaemonCommand parent;

        @Option(names = {"--system"}, description = "Target system-wide service (requires root/sudo)")
        private boolean systemScope;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            PrintWriter err = spec.commandLine().getErr();

            ServiceOperationResult res = parent.getServiceManager().enableService(systemScope);
            if (res.success()) {
                out.println("\u001B[32m✓ " + res.message() + "\u001B[0m");
                return 0;
            } else {
                err.println("\u001B[31m" + res.message() + "\u001B[0m");
                return 1;
            }
        }
    }

    // 3. DISABLE
    @Component
    @Command(
        name = "disable",
        description = "Disables the Nexus daemon service from auto-starting on boot",
        mixinStandardHelpOptions = true
    )
    public static class DisableSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @ParentCommand
        private DaemonCommand parent;

        @Option(names = {"--system"}, description = "Target system-wide service (requires root/sudo)")
        private boolean systemScope;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            PrintWriter err = spec.commandLine().getErr();

            ServiceOperationResult res = parent.getServiceManager().disableService(systemScope);
            if (res.success()) {
                out.println("\u001B[32m✓ " + res.message() + "\u001B[0m");
                return 0;
            } else {
                err.println("\u001B[31m" + res.message() + "\u001B[0m");
                return 1;
            }
        }
    }

    // 4. START
    @Component
    @Command(
        name = "start",
        description = "Starts the Nexus background daemon service (or in foreground with -f)",
        mixinStandardHelpOptions = true
    )
    public static class StartSubcommand implements Callable<Integer> {

        @Spec
        private CommandSpec spec;

        @ParentCommand
        private DaemonCommand parent;

        @Option(names = {"-f", "--foreground"}, description = "Run daemon directly in the foreground (blocking)")
        private boolean foreground;

        @Option(names = {"--system"}, description = "Target system-wide service (requires root/sudo)")
        private boolean systemScope;

        @Option(names = {"-p", "--port"}, description = "Local IPC port", defaultValue = "29786")
        private int port;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            PrintWriter err = spec.commandLine().getErr();

            // When NOT foreground, manage as an OS background service
            if (!foreground) {
                if (!parent.getServiceManager().isServiceInstalled(systemScope)) {
                    err.println("\u001B[31mError: Nexus daemon service is not installed.\u001B[0m");
                    err.println("  👉 Run 'nexus daemon install" + (systemScope ? " --system" : "") + "' first to install and enable the background service.");
                    err.println("  👉 Or use 'nexus daemon start --foreground' to run directly in this terminal session.");
                    return 1;
                }

                // Ensure service is enabled
                if (!parent.getServiceManager().isServiceEnabled(systemScope)) {
                    parent.getServiceManager().enableService(systemScope);
                }

                ServiceOperationResult res = parent.getServiceManager().startService(systemScope);
                if (res.success()) {
                    out.println("\u001B[32m✓ Started Nexus daemon service in background (" + res.serviceName() + ")\u001B[0m");
                    out.println("  • Status:      RUNNING (headless background service)");
                    out.println("  • Supervisor:  " + parent.getServiceManager().getActiveProvider().getPlatformName());
                    out.println("  • Scope:       " + (systemScope ? "system" : "user"));
                    out.println("  • Telegram:    ACTIVE (polling commands)");
                    out.println("  • Local IPC:   127.0.0.1:" + port);
                    out.println("");
                    out.println("  ℹ Inspect live logs: " + parent.getServiceManager().getLogGuidance(systemScope));
                    out.println("  ℹ Query status:      nexus daemon status" + (systemScope ? " --system" : ""));
                    out.println("  ℹ Stop daemon:       nexus daemon stop" + (systemScope ? " --system" : ""));
                    return 0;
                } else {
                    err.println("\u001B[31mFailed to start daemon service: " + res.message() + "\u001B[0m");
                    return 1;
                }
            }

            // Foreground mode (used by systemd ExecStart or direct debugging)
            DaemonIpcServer srv = parent.getIpcServer();
            TelegramBotAdapter tgAdapter = parent.getTelegramBotAdapter();
            NexusProperties props = parent.getProperties();

            try {
                out.println("\u001B[1;36mStarting Nexus Host Management Daemon (foreground)...\u001B[0m");

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
                        out.println("  ✗ Telegram bot  : FAILED to start (see " + com.nexus.platform.logging.NexusLogManager.getAppLogFile() + " for details)");
                    }
                }

                out.println("  ✓ Application logs: " + com.nexus.platform.logging.NexusLogManager.getAppLogFile());
                out.println("  ✓ Audit trail     : " + com.nexus.platform.logging.NexusLogManager.getAuditLogFile());
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

    // 5. STOP
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

        @Option(names = {"--system"}, description = "Target system-wide service (requires root/sudo)")
        private boolean systemScope;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            PrintWriter err = spec.commandLine().getErr();
            boolean stopped = false;

            // 1. Try stopping through supervisor service if installed
            if (parent.getServiceManager().isServiceInstalled(systemScope)) {
                ServiceOperationResult res = parent.getServiceManager().stopService(systemScope);
                if (res.success()) {
                    out.println("\u001B[32m✓ " + res.message() + " (" + res.serviceName() + ")\u001B[0m");
                    stopped = true;
                }
            }

            // 2. Try sending shutdown command over loopback IPC
            try {
                String response = parent.getIpcClient().sendCommand("STOP", DaemonIpcServer.DEFAULT_PORT);
                out.println("\u001B[32m✓ Sent shutdown command to Nexus daemon via IPC: " + response + "\u001B[0m");
                stopped = true;
            } catch (Exception ignored) {}

            if (!stopped) {
                out.println("  ℹ Nexus daemon is not currently running or already stopped.");
            }
            return 0;
        }
    }

    // 6. STATUS
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

        @Option(names = {"--system"}, description = "Target system-wide service (requires root/sudo)")
        private boolean systemScope;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            PrintWriter err = spec.commandLine().getErr();
            boolean json = isJsonRequested(spec);

            ServiceStatusDetails details = parent.getServiceManager().getServiceStatus(systemScope);
            String ipcStatusJson = null;
            try {
                ipcStatusJson = parent.getIpcClient().sendCommand("STATUS", DaemonIpcServer.DEFAULT_PORT);
            } catch (Exception ignored) {}

            if (json) {
                out.printf("{\"serviceInstalled\":%b,\"serviceEnabled\":%b,\"serviceRunning\":%b,\"platform\":\"%s\",\"scope\":\"%s\",\"ipcRunning\":%b}%n",
                        details.installed(), details.enabled(), details.running(), details.platform(),
                        systemScope ? "system" : "user", ipcStatusJson != null);
            } else {
                out.println("\u001B[1;36m=== Nexus Daemon Status ===\u001B[0m");
                out.println("  Supervisor Service : " + details.serviceName() + " (" + details.platform() + ", " + (systemScope ? "system" : "user") + " scope)");
                out.println("  Service Installed  : " + (details.installed() ? "\u001B[32mYES\u001B[0m (" + details.unitPath() + ")" : "\u001B[33mNO (run 'nexus daemon install')\u001B[0m"));
                out.println("  Service Enabled    : " + (details.enabled() ? "\u001B[32mYES (auto-starts on boot)\u001B[0m" : "\u001B[33mNO\u001B[0m"));
                out.println("  Service State      : " + (details.running() ? "\u001B[32m" + details.stateDescription() + "\u001B[0m" : "\u001B[31m" + details.stateDescription() + "\u001B[0m"));
                out.println("  Daemon IPC         : " + (ipcStatusJson != null ? "\u001B[32mRUNNING\u001B[0m (127.0.0.1:" + DaemonIpcServer.DEFAULT_PORT + ")" : "\u001B[31mOFFLINE\u001B[0m"));
                if (ipcStatusJson != null) {
                    out.println("  " + ipcStatusJson);
                }
            }
            return 0;
        }
    }

    // 7. UNINSTALL
    @Component
    @Command(
        name = "logs",
        aliases = {"log"},
        description = "Inspect, follow, stream, and manage daemon application and audit logs (PM2-style)",
        mixinStandardHelpOptions = true
    )
    public static class LogsSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @ParentCommand
        private DaemonCommand parent;

        @Option(names = {"-n", "--lines"}, description = "Number of lines to show (default: 30)", defaultValue = "30")
        private int lines = 30;

        @Option(names = {"-f", "--follow"}, description = "Stream and follow log updates in real-time (Ctrl+C to exit)")
        private boolean follow;

        @Option(names = {"-a", "--audit"}, description = "Inspect the tamper-evident audit log (nexus-audit.log) instead of application log")
        private boolean audit;

        @Option(names = {"--clear", "--flush"}, description = "Flush / truncate active log files (like 'pm2 flush')")
        private boolean flush;

        @Option(names = {"-l", "--list"}, description = "List all log files, active sizes, and rotated archives")
        private boolean list;

        @Option(names = {"-p", "--path"}, description = "Display the absolute path to the canonical log directory")
        private boolean showPath;

        @Option(names = {"--raw"}, description = "Output raw log lines without decorative banners")
        private boolean raw;

        @Override
        public Integer call() {
            LogsCommand cmd = new LogsCommand(parent != null ? parent.getRoot() : null);
            picocli.CommandLine cmdLine = new picocli.CommandLine(cmd);
            if (spec != null) {
                cmdLine.setOut(spec.commandLine().getOut());
                cmdLine.setErr(spec.commandLine().getErr());
            }
            java.util.List<String> args = new java.util.ArrayList<>();
            args.add("-n=" + lines);
            if (follow) args.add("-f");
            if (audit) args.add("-a");
            if (flush) args.add("--flush");
            if (list) args.add("-l");
            if (showPath) args.add("-p");
            if (raw) args.add("--raw");
            return cmdLine.execute(args.toArray(new String[0]));
        }
    }

    @Component
    @Command(
        name = "uninstall",
        aliases = {"remove"},
        description = "Uninstalls the Nexus daemon background service and removes unit files",
        mixinStandardHelpOptions = true
    )
    public static class UninstallSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @ParentCommand
        private DaemonCommand parent;

        @Option(names = {"--system"}, description = "Target system-wide service (requires root/sudo)")
        private boolean systemScope;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            PrintWriter err = spec.commandLine().getErr();

            ServiceOperationResult res = parent.getServiceManager().uninstallService(systemScope);
            if (res.success()) {
                out.println("\u001B[32m✓ " + res.message() + " (" + res.serviceName() + ")\u001B[0m");
                return 0;
            } else {
                err.println("\u001B[31m" + res.message() + "\u001B[0m");
                return 1;
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
