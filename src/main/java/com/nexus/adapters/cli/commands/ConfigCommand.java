package com.nexus.adapters.cli.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.adapters.cli.NexusCliRootCommand;
import com.nexus.adapters.ipc.DaemonIpcClient;
import com.nexus.adapters.ipc.DaemonIpcServer;
import com.nexus.config.NexusConfigResolver;
import com.nexus.config.NexusProperties;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * CLI command group for configuration management, editing, reloading, and validation.
 */
@Component
@Command(
    name = "config",
    description = "Inspect, edit, reload, and validate agent configuration",
    subcommands = {
        ConfigCommand.ValidateSubcommand.class,
        ConfigCommand.EditSubcommand.class,
        ConfigCommand.ReloadSubcommand.class,
        ConfigCommand.ShowSubcommand.class,
        ConfigCommand.PathSubcommand.class
    },
    mixinStandardHelpOptions = true
)
public class ConfigCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    private final NexusProperties properties;

    public ConfigCommand(NexusProperties properties) {
        this.properties = properties;
    }

    @Override
    public Integer call() {
        PrintWriter out = (spec != null) ? spec.commandLine().getOut() : new PrintWriter(System.out, true);
        return ValidateSubcommand.runValidation(out, properties);
    }

    /**
     * Resolves custom config file path from root CLI options (-c / --config) or explicit flag.
     */
    static String resolveExplicitConfigPath(CommandSpec spec, String localOption) {
        if (localOption != null && !localOption.isBlank()) {
            return localOption;
        }
        if (spec != null && spec.root() != null && spec.root().commandLine().getCommand() instanceof NexusCliRootCommand root) {
            if (root.getConfigFile() != null) {
                return root.getConfigFile().getAbsolutePath();
            }
        }
        return null;
    }

    @Component
    @Command(name = "validate", description = "Validate agent configuration properties", mixinStandardHelpOptions = true)
    public static class ValidateSubcommand implements Callable<Integer> {

        @Spec
        private CommandSpec spec;

        private final NexusProperties properties;

        public ValidateSubcommand(NexusProperties properties) {
            this.properties = properties;
        }

        @Override
        public Integer call() {
            PrintWriter out = (spec != null) ? spec.commandLine().getOut() : new PrintWriter(System.out, true);
            return runValidation(out, properties);
        }

        public static int runValidation(PrintWriter out, NexusProperties properties) {
            out.println("\u001B[1;36m=== Nexus Configuration Validation ===\u001B[0m");

            String resolvedPath = NexusConfigResolver.getResolvedConfigPath();
            if (resolvedPath != null) {
                out.printf("  ✓ Configuration file    : %s%n", resolvedPath);
            } else {
                out.println("  ℹ Configuration file    : Embedded defaults (no external file active)");
            }

            out.printf("  ✓ Operational mode      : %s%n", properties.getMode());
            out.printf("  ✓ Capability timeout    : %s%n", properties.getDefaultTimeout());
            out.printf("  ✓ Audit trail logging   : %s%n", properties.isAuditEnabled() ? "ENABLED" : "DISABLED");
            out.printf("  ✓ Host timezone         : %s%n", com.nexus.platform.HostTimezoneResolver.getFormattedTimezone());

            NexusProperties.TelegramConfig tg = properties.getTelegram();
            boolean valid = true;
            if (tg != null && tg.isEnabled()) {
                out.println("  ✓ Telegram bot status   : ENABLED");
                if (tg.getBotToken() != null && !tg.getBotToken().isBlank()) {
                    String masked = maskToken(tg.getBotToken());
                    out.printf("  ✓ Telegram bot token    : %s%n", masked);
                } else {
                    out.println("  ✗ Telegram bot token    : MISSING / EMPTY (must provide bot-token)");
                    valid = false;
                }

                if (tg.getAllowedChatId() != 0) {
                    out.printf("  ✓ Telegram allowed chat : %d%n", tg.getAllowedChatId());
                } else {
                    out.println("  ⚠ Telegram allowed chat : NOT SET (0) - all incoming messages will be rejected");
                }
            } else {
                out.println("  ℹ Telegram bot status   : DISABLED");
            }

            if (valid) {
                out.println("\u001B[32mConfiguration is valid.\u001B[0m");
                return 0;
            } else {
                out.println("\u001B[31mConfiguration has errors. Please check the warnings above.\u001B[0m");
                return 1;
            }
        }

        private static String maskToken(String token) {
            if (token == null || token.length() <= 8) {
                return "********";
            }
            return token.substring(0, 4) + "...." + token.substring(token.length() - 4);
        }
    }

    @Component
    @Command(
        name = "edit",
        description = "Edit the configuration file in an interactive editor or display editor command",
        mixinStandardHelpOptions = true
    )
    public static class EditSubcommand implements Callable<Integer> {

        @Spec
        private CommandSpec spec;

        private final NexusProperties properties;

        @Option(names = {"-e", "--editor"}, description = "Text editor executable (e.g. nano, vim, vi, notepad)")
        private String editor;

        @Option(names = {"-p", "--print"}, description = "Print the editor command instead of opening an interactive editor")
        private boolean printOnly;

        @Option(names = {"--file"}, description = "Specific configuration file to edit")
        private String targetFileOption;

        public EditSubcommand(NexusProperties properties) {
            this.properties = properties;
        }

        @Override
        public Integer call() {
            PrintWriter out = (spec != null) ? spec.commandLine().getOut() : new PrintWriter(System.out, true);
            PrintWriter err = (spec != null) ? spec.commandLine().getErr() : new PrintWriter(System.err, true);

            String explicitPath = resolveExplicitConfigPath(spec, targetFileOption);
            File configFile = NexusConfigResolver.resolveTargetConfigFileForEditing(explicitPath);

            // Create template if missing
            if (!configFile.exists()) {
                boolean created = NexusConfigResolver.createDefaultConfigFileIfMissing(configFile);
                if (created) {
                    out.println("✓ Created default configuration template at: " + configFile.getAbsolutePath());
                }
            }

            String chosenEditor = resolveEditor(editor);

            // If --print was requested, or if not in an interactive terminal and no explicit editor given
            boolean hasInteractiveConsole = System.console() != null;
            if (printOnly || (!hasInteractiveConsole && editor == null)) {
                out.println("=== Nexus Configuration File ===");
                out.println("Path: " + configFile.getAbsolutePath());
                out.println("\nTo edit this configuration file, run:");
                out.println("  " + chosenEditor + " " + configFile.getAbsolutePath());
                out.println("or:");
                out.println("  ${EDITOR:-" + chosenEditor + "} " + configFile.getAbsolutePath());
                return 0;
            }

            // Launch interactive editor
            try {
                out.println("Opening configuration in " + chosenEditor + "...");
                ProcessBuilder pb = new ProcessBuilder(chosenEditor, configFile.getAbsolutePath());
                pb.inheritIO();
                Process process = pb.start();
                int exitCode = process.waitFor();

                if (exitCode == 0) {
                    out.println("\n✓ Editor session closed.");
                    // Validate after edit
                    boolean reloaded = NexusConfigResolver.reloadPropertiesFromYaml(configFile, properties);
                    if (reloaded) {
                        out.println("\u001B[32m✓ Configuration syntax is valid.\u001B[0m");
                        out.println("ℹ Tip: Run 'nexus config reload' to apply changes to the running daemon without restarting.");
                    } else {
                        err.println("\u001B[31m⚠ Warning: Failed to parse updated configuration YAML.\u001B[0m");
                    }
                    return 0;
                } else {
                    err.println("Editor process exited with code " + exitCode);
                    return exitCode;
                }
            } catch (Exception e) {
                out.println("Could not launch interactive editor '" + chosenEditor + "': " + e.getMessage());
                out.println("\nTo edit manually, run:");
                out.println("  nano " + configFile.getAbsolutePath());
                return 0;
            }
        }

        private String resolveEditor(String explicitEditor) {
            if (explicitEditor != null && !explicitEditor.isBlank()) {
                return explicitEditor;
            }
            String envVisual = System.getenv("VISUAL");
            if (envVisual != null && !envVisual.isBlank()) {
                return envVisual;
            }
            String envEditor = System.getenv("EDITOR");
            if (envEditor != null && !envEditor.isBlank()) {
                return envEditor;
            }

            // Detect common editors in PATH
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                return "notepad";
            }
            for (String candidate : new String[]{"nano", "vim", "vi"}) {
                if (isCommandAvailable(candidate)) {
                    return candidate;
                }
            }
            return "nano";
        }

        private boolean isCommandAvailable(String cmd) {
            try {
                Process p = new ProcessBuilder("which", cmd).start();
                return p.waitFor() == 0;
            } catch (Exception e) {
                return false;
            }
        }
    }

    @Component
    @Command(
        name = "reload",
        description = "Reload configuration in the running daemon or validate on-disk changes",
        mixinStandardHelpOptions = true
    )
    public static class ReloadSubcommand implements Callable<Integer> {

        @Spec
        private CommandSpec spec;

        private final DaemonIpcClient ipcClient;
        private final NexusProperties properties;
        private final ObjectMapper objectMapper;

        @Option(names = {"--file"}, description = "Target configuration file to reload")
        private String targetFileOption;

        public ReloadSubcommand(DaemonIpcClient ipcClient, NexusProperties properties) {
            this.ipcClient = ipcClient;
            this.properties = properties;
            this.objectMapper = new ObjectMapper();
        }

        @Override
        public Integer call() {
            PrintWriter out = (spec != null) ? spec.commandLine().getOut() : new PrintWriter(System.out, true);
            PrintWriter err = (spec != null) ? spec.commandLine().getErr() : new PrintWriter(System.err, true);

            boolean jsonMode = false;
            if (spec != null && spec.root() != null && spec.root().commandLine().getCommand() instanceof NexusCliRootCommand root) {
                jsonMode = root.isJson();
            }

            String explicitPath = resolveExplicitConfigPath(spec, targetFileOption);
            File configFile = NexusConfigResolver.resolveTargetConfigFileForEditing(explicitPath);

            // Check if daemon is active
            boolean daemonRunning = ipcClient.isDaemonRunning(DaemonIpcServer.DEFAULT_PORT);

            if (daemonRunning) {
                try {
                    String cmd = (explicitPath != null) ? "RELOAD " + explicitPath : "RELOAD";
                    String response = ipcClient.sendCommand(cmd, DaemonIpcServer.DEFAULT_PORT);

                    if (response != null && (response.startsWith("{") || response.startsWith("OK"))) {
                        if (jsonMode) {
                            out.println(response);
                        } else {
                            out.println("\u001B[1;32m✓ Successfully sent reload signal to background Nexus daemon (127.0.0.1:" + DaemonIpcServer.DEFAULT_PORT + ").\u001B[0m");
                            try {
                                JsonNode node = objectMapper.readTree(response);
                                out.println("  • Active file   : " + node.path("configFile").asText(configFile.getAbsolutePath()));
                                out.println("  • Mode          : " + node.path("mode").asText(properties.getMode()));
                                out.println("  • Timeout       : " + node.path("defaultTimeout").asText(properties.getDefaultTimeout().toString()));
                                out.println("  • Telegram bot  : " + (node.path("telegramEnabled").asBoolean() ? "ENABLED" : "DISABLED"));
                            } catch (Exception ignored) {
                                out.println("  • Response: " + response);
                            }
                        }
                        return 0;
                    } else {
                        if (jsonMode) {
                            out.println("{\"error\": \"" + response + "\"}");
                        } else {
                            err.println("\u001B[31m✗ Daemon failed to reload configuration: " + response + "\u001B[0m");
                        }
                        return 1;
                    }
                } catch (Exception e) {
                    if (!jsonMode) {
                        err.println("Error communicating with daemon over IPC: " + e.getMessage());
                    }
                }
            }

            // Daemon is not running: validate on disk
            if (configFile.exists()) {
                boolean valid = NexusConfigResolver.reloadPropertiesFromYaml(configFile, properties);
                if (valid) {
                    if (jsonMode) {
                        Map<String, Object> map = new LinkedHashMap<>();
                        map.put("daemonRunning", false);
                        map.put("configFile", configFile.getAbsolutePath());
                        map.put("valid", true);
                        map.put("mode", properties.getMode());
                        try {
                            out.println(objectMapper.writeValueAsString(map));
                        } catch (Exception ignored) {}
                    } else {
                        out.println("• Background daemon is not currently running.");
                        out.println("\u001B[32m✓ Configuration at " + configFile.getAbsolutePath() + " is valid.\u001B[0m");
                        out.println("  Changes will take effect the next time the daemon or CLI is started ('nexus start').");
                    }
                    return 0;
                } else {
                    if (jsonMode) {
                        out.println("{\"daemonRunning\": false, \"valid\": false, \"error\": \"YAML syntax error\"}");
                    } else {
                        err.println("\u001B[31m✗ Configuration at " + configFile.getAbsolutePath() + " contains syntax or structure errors.\u001B[0m");
                    }
                    return 1;
                }
            } else {
                if (jsonMode) {
                    out.println("{\"error\": \"Configuration file not found: " + configFile.getAbsolutePath() + "\"}");
                } else {
                    err.println("✗ Configuration file not found at " + configFile.getAbsolutePath());
                }
                return 1;
            }
        }
    }

    @Component
    @Command(
        name = "show",
        aliases = {"cat", "view"},
        description = "Display contents of the active configuration file",
        mixinStandardHelpOptions = true
    )
    public static class ShowSubcommand implements Callable<Integer> {

        @Spec
        private CommandSpec spec;

        @Option(names = {"--file"}, description = "Target configuration file to display")
        private String targetFileOption;

        @Override
        public Integer call() {
            PrintWriter out = (spec != null) ? spec.commandLine().getOut() : new PrintWriter(System.out, true);
            PrintWriter err = (spec != null) ? spec.commandLine().getErr() : new PrintWriter(System.err, true);

            String explicitPath = resolveExplicitConfigPath(spec, targetFileOption);
            File configFile = NexusConfigResolver.resolveTargetConfigFileForEditing(explicitPath);

            if (configFile.exists() && configFile.isFile()) {
                try {
                    out.println("# Configuration File: " + configFile.getAbsolutePath());
                    out.println(Files.readString(configFile.toPath()));
                    return 0;
                } catch (Exception e) {
                    err.println("Could not read configuration file: " + e.getMessage());
                    return 1;
                }
            } else {
                out.println("# Configuration File: " + configFile.getAbsolutePath() + " (not found)");
                out.println("# Embedded defaults are active. To create a configuration file, run: nexus config edit");
                return 0;
            }
        }
    }

    @Component
    @Command(
        name = "path",
        description = "Print the absolute path of the active configuration file",
        mixinStandardHelpOptions = true
    )
    public static class PathSubcommand implements Callable<Integer> {

        @Spec
        private CommandSpec spec;

        @Option(names = {"--file"}, description = "Target configuration file")
        private String targetFileOption;

        @Override
        public Integer call() {
            PrintWriter out = (spec != null) ? spec.commandLine().getOut() : new PrintWriter(System.out, true);

            String explicitPath = resolveExplicitConfigPath(spec, targetFileOption);
            File configFile = NexusConfigResolver.resolveTargetConfigFileForEditing(explicitPath);
            out.println(configFile.getAbsolutePath());
            return 0;
        }
    }
}
