package com.nexus.adapters.cli;

import picocli.CommandLine;
import picocli.CommandLine.Help;
import picocli.CommandLine.Help.ColorScheme;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.PositionalParamSpec;
import picocli.CommandLine.Model.UsageMessageSpec;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Standard enterprise-grade CLI help customizer for Nexus.
 * Configures dynamic terminal width, ANSI multi-color palette, grouped categories,
 * dedicated alias sections on subcommands, clean 2-column command tables, and targeted examples.
 */
public final class NexusCliHelpCustomizer {

    private static final int DEFAULT_HELP_WIDTH = 110;
    private static final int MIN_HELP_WIDTH = 95;
    private static final int MAX_HELP_WIDTH = 140;

    public record CommandEntry(String name, String description) {}
    public record CommandGroup(String title, List<CommandEntry> commands) {}

    private static final List<CommandGroup> ROOT_COMMAND_GROUPS = List.of(
            new CommandGroup("CORE & SYSTEM", List.of(
                    new CommandEntry("system", "Host hardware, CPU, memory, OS audit, and privileged power"),
                    new CommandEntry("daemon", "Manage background agent service (install, start, stop, status)"),
                    new CommandEntry("logs", "Stream and inspect centralized PM2-style application logs")
            )),
            new CommandGroup("AUTOMATION & SERVICES", List.of(
                    new CommandEntry("timer", "Manage recurring timers and scripts (systemd, launchd, task scheduler)"),
                    new CommandEntry("reminder", "Multi-stage birthday, bill, and event notification engine"),
                    new CommandEntry("telegram", "Dispatch notifications, log files, and media via Telegram"),
                    new CommandEntry("tailscale", "Inspect and control Tailscale VPN mesh network and peers")
            )),
            new CommandGroup("STORAGE & WORKSPACES", List.of(
                    new CommandEntry("zfs", "Inspect OpenZFS storage pools, datasets, snapshots, and properties"),
                    new CommandEntry("tmux", "Terminal multiplexer session lifecycle (list, new, attach, kill)")
            )),
            new CommandGroup("AGENT CONFIGURATION", List.of(
                    new CommandEntry("config", "Inspect, edit, reload, and validate config.yml"),
                    new CommandEntry("capabilities", "Introspect registered agent capabilities and schemas"),
                    new CommandEntry("update", "Check for and install the latest Nexus binary release"),
                    new CommandEntry("uninstall", "Stop background services and clean agent runtime data"),
                    new CommandEntry("completion", "Generate shell tab auto-completion script (bash, zsh, fish)"),
                    new CommandEntry("backup", "Full backup, AES-256-GCM encryption, inspection, and disaster recovery")
            ))
    );

    private static final Map<String, List<String>> SUBCOMMAND_EXAMPLES = Map.ofEntries(
            Map.entry("system", List.of(
                    "nx system info                     # View hardware, kernel, CPU, and RAM",
                    "nx system audit                    # Run deep operational health audit",
                    "nx system proc                     # Inspect top CPU & memory consuming processes",
                    "nx system disk                     # Overview of filesystems and disk telemetry"
            )),
            Map.entry("daemon", List.of(
                    "nx daemon status                   # Inspect live daemon process & PID",
                    "nx daemon start                    # Start and enable background agent service",
                    "nx daemon stop                     # Gracefully stop running background service",
                    "nx daemon logs -f                  # Live follow background daemon output"
            )),
            Map.entry("logs", List.of(
                    "nx logs                            # Inspect recent centralized application logs",
                    "nx logs -n 50                      # View last 50 log lines",
                    "nx logs -f                         # Stream logs in real-time",
                    "nx logs --audit                    # Inspect security and command audit trail",
                    "nx logs --flush                    # Clear/truncate active log files"
            )),
            Map.entry("timer", List.of(
                    "nx timer list                      # View scheduled timers across all scopes",
                    "nx timer create backup-db -s 'daily' -d 'Daily DB backup'",
                    "nx timer status NX001              # Inspect next run time and journal logs",
                    "nx timer run NX001                 # Trigger immediate one-off execution",
                    "nx timer delete NX001 --purge-script"
            )),
            Map.entry("reminder", List.of(
                    "nx reminder list                   # View scheduled reminders & upcoming milestones",
                    "nx reminder next                   # Inspect immediate next reminder & countdown",
                    "nx reminder template               # Show copy-pasteable template with all parameters",
                    "nx reminder add --title \"Mom's Birthday\" --date 2026-11-25 --type BIRTHDAY",
                    "nx reminder add --title \"AWS Hosting\" --date 2026-12-05 --recurrence \"1mo\" --stages 3d,1d,0m",
                    "nx reminder add --title \"Hydration\" --date 2026-11-24 --time 10:00:00 --recurrence \"every 45m\"",
                    "nx reminder show EVT-001           # Inspect stages, history, and systemd timer",
                    "nx reminder edit EVT-001 --time 10:00:00 --stages 3d,1d,0m",
                    "nx reminder delete EVT-001         # Remove reminder and delete systemd unit",
                    "nx reminder export reminders.json  # Backup all reminders to JSON",
                    "nx reminder import reminders.json  # Restore/merge reminders from backup"
            )),
            Map.entry("template", List.of(
                    "nx reminder template               # View comprehensive template with all parameters",
                    "nx reminder template > my-rem.sh   # Save template commands to file"
            )),
            Map.entry("add", List.of(
                    "nx reminder add --example          # View full copy-pasteable command template",
                    "sudo nx reminder add --title \"Alice's Birthday\" --date 2026-05-14 --type BIRTHDAY --birth-year 1995",
                    "sudo nx reminder add --title \"Rent\" --date 2026-12-05 --time 09:00:00 --type BILL --recurrence \"1mo\" --stages 3d,1d,0m",
                    "sudo nx reminder add --title \"Hydration\" --date 2026-11-24 --time 10:00:00 --recurrence \"every 45m\" --stages 0m",
                    "sudo nx reminder add --title \"Shift Sync\" --date 2026-11-24 --time 18:00:00 --recurrence \"16h\" --stages 30m,0m"
            )),
            Map.entry("telegram", List.of(
                    "nx telegram send \"Backup finished!\"   # Broadcast text message",
                    "nx telegram file /var/log/syslog       # Upload document / log file",
                    "nx telegram test                      # Validate bot connectivity and token"
            )),
            Map.entry("tailscale", List.of(
                    "nx tailscale status                # View VPN state, tailnet IPs & peers",
                    "nx tailscale up                    # Bring Tailscale mesh network up",
                    "nx tailscale down                  # Disconnect Tailscale VPN"
            )),
            Map.entry("zfs", List.of(
                    "nx zfs pools                      # Health and capacity across all ZFS pools",
                    "nx zfs status rpool               # Inspect vdev tree, scrub & error count",
                    "nx zfs datasets                   # Hierarchy of datasets and mountpoints",
                    "nx zfs snapshots                  # List recent snapshots & space referenced"
            )),
            Map.entry("tmux", List.of(
                    "nx tmux list                      # Inspect active multiplexer sessions",
                    "nx tmux new dev-work              # Create and attach to a new tmux session",
                    "nx tmux attach dev-work           # Handover console to existing session",
                    "nx tmux kill dev-work             # Terminate specified tmux session"
            )),
            Map.entry("config", List.of(
                    "nx config path                    # Print active configuration file path",
                    "nx config show                    # View active YAML settings",
                    "nx config edit                    # Open config in default editor",
                    "nx config reload                  # Hot-reload configuration without restart"
            )),
            Map.entry("capabilities", List.of(
                    "nx capabilities                   # Introspect all registered capabilities",
                    "nx capabilities timer.create      # Inspect input/output schema for capability"
            )),
            Map.entry("update", List.of(
                    "nx update --check                 # Query GitHub API for new releases",
                    "nx update                         # Download & in-place upgrade Nexus binary"
            )),
            Map.entry("uninstall", List.of(
                    "nx uninstall                      # Remove binary and systemd services",
                    "nx uninstall --purge              # Completely purge configs, state & logs"
            )),
            Map.entry("completion", List.of(
                    "nx completion bash                 # Generate Bash auto-completion script",
                    "nx completion zsh                  # Generate Zsh auto-completion script",
                    "nx completion fish                 # Generate Fish auto-completion script",
                    "source <(nexus completion bash)    # Activate tab completion in current session",
                    "nexus completion bash > /etc/bash_completion.d/nexus"
            )),
            Map.entry("backup", List.of(
                    "nx backup create                  # Snapshot all configuration, reminders, and timers",
                    "nx backup create -e -p 'mySecret' # Create AES-256-GCM encrypted backup archive",
                    "nx backup inspect backup.zip.enc  # Inspect manifest, stats, and file inventory",
                    "nx backup restore backup.zip --dry-run # Simulate restore without modifying disk",
                    "sudo nx backup restore backup.zip --rearm -y # Restore and re-arm systemd timers"
            ))
    );

    private NexusCliHelpCustomizer() {}

    /**
     * Applies standard enterprise styling recursively across a Picocli CommandLine hierarchy.
     */
    public static void customize(CommandLine commandLine) {
        if (commandLine == null) {
            return;
        }

        int width = resolveTerminalWidth();
        applyCustomStyling(commandLine, width, true);

        for (CommandLine sub : commandLine.getSubcommands().values()) {
            applySubcommandStyling(sub, width);
        }
    }

    private static void applyCustomStyling(CommandLine cmd, int width, boolean isRoot) {
        cmd.setUsageHelpWidth(width);
        cmd.setColorScheme(buildColorScheme());

        if (isRoot) {
            configureRootSections(cmd);
        } else {
            configureSubcommandSections(cmd);
        }
    }

    private static void applySubcommandStyling(CommandLine cmd, int width) {
        applyCustomStyling(cmd, width, false);
        for (CommandLine sub : cmd.getSubcommands().values()) {
            applySubcommandStyling(sub, width);
        }
    }

    private static void configureRootSections(CommandLine cmd) {
        Map<String, CommandLine.IHelpSectionRenderer> map = cmd.getHelpSectionMap();
        CommandSpec spec = cmd.getCommandSpec();

        map.put(UsageMessageSpec.SECTION_KEY_HEADER, help ->
                help.colorScheme().ansi().text(String.format(
                        "@|bold,yellow ⚡ Nexus Host Management Agent|@  @|bold,cyan [v0.0.11-SNAPSHOT]|@%n" +
                        "Cross-platform host telemetry, service orchestration, and automated operations.%n%n"
                )).toString()
        );

        map.put(UsageMessageSpec.SECTION_KEY_SYNOPSIS, help ->
                help.colorScheme().ansi().text(String.format(
                        "@|bold,yellow USAGE|@%n" +
                        "  nexus [command] [flags]%n" +
                        "  nx [command]%n%n"
                )).toString()
        );

        map.put(UsageMessageSpec.SECTION_KEY_COMMAND_LIST, help -> {
            StringBuilder sb = new StringBuilder();
            for (CommandGroup group : ROOT_COMMAND_GROUPS) {
                sb.append(String.format("@|bold,yellow %s|@%n", group.title));
                for (CommandEntry e : group.commands) {
                    sb.append(String.format("  @|bold,cyan %-14s|@  %s%n", e.name, e.description));
                }
                sb.append(String.format("%n"));
            }
            return help.colorScheme().ansi().text(sb.toString()).toString();
        });

        map.put(UsageMessageSpec.SECTION_KEY_OPTION_LIST_HEADING, help ->
                help.colorScheme().ansi().text(String.format("@|bold,yellow GLOBAL FLAGS|@%n")).toString()
        );

        configureOptionListRenderer(cmd, spec);

        map.put("examples", help ->
                help.colorScheme().ansi().text(String.format(
                        "@|bold,yellow EXAMPLES|@%n" +
                        "  $ @|bold,white nx system info|@                     @|faint # Inspect host hardware, CPU, and RAM|@%n" +
                        "  $ @|bold,white nx timer list|@                      @|faint # View scheduled timers across all scopes|@%n" +
                        "  $ @|bold,white nx daemon status|@                   @|faint # Check background supervisor daemon status|@%n" +
                        "  $ @|bold,white nx logs -f|@                         @|faint # Stream live rotating application logs|@%n%n"
                )).toString()
        );

        map.put(UsageMessageSpec.SECTION_KEY_FOOTER, help ->
                help.colorScheme().ansi().text(String.format(
                        "@|bold,yellow LEARN MORE|@%n" +
                        "  Run '@|bold,cyan nexus <command> --help|@' to see subcommands, flags, and all aliases.%n" +
                        "  Documentation: https://github.com/Shrikshel/nexus%n"
                )).toString()
        );

        List<String> keys = new ArrayList<>();
        keys.add(UsageMessageSpec.SECTION_KEY_HEADER);
        keys.add(UsageMessageSpec.SECTION_KEY_SYNOPSIS);
        keys.add(UsageMessageSpec.SECTION_KEY_COMMAND_LIST);
        keys.add(UsageMessageSpec.SECTION_KEY_OPTION_LIST_HEADING);
        keys.add(UsageMessageSpec.SECTION_KEY_OPTION_LIST);
        keys.add("examples");
        keys.add(UsageMessageSpec.SECTION_KEY_FOOTER);

        cmd.setHelpSectionKeys(keys);
    }

    private static void configureSubcommandSections(CommandLine cmd) {
        CommandSpec spec = cmd.getCommandSpec();
        Map<String, CommandLine.IHelpSectionRenderer> map = cmd.getHelpSectionMap();

        map.put(UsageMessageSpec.SECTION_KEY_HEADER, help -> {
            String name = spec.name();
            String desc = spec.usageMessage().description().length > 0
                    ? String.join(" ", spec.usageMessage().description())
                    : "";
            return help.colorScheme().ansi().text(String.format(
                    "@|bold,yellow ⚡ nexus %s|@%n%s%n%n", name, desc
            )).toString();
        });

        map.put(UsageMessageSpec.SECTION_KEY_SYNOPSIS, help -> {
            String qualifiedName = spec.qualifiedName(" ");
            boolean hasSubcommands = !spec.subcommands().isEmpty();
            boolean hasOptions = !spec.options().isEmpty();
            StringBuilder sb = new StringBuilder("@|bold,yellow USAGE|@\n");
            sb.append(String.format("  %s", qualifiedName));
            if (!spec.positionalParameters().isEmpty()) {
                for (PositionalParamSpec p : spec.positionalParameters()) {
                    String label = p.paramLabel() != null && !p.paramLabel().isBlank() ? p.paramLabel() : "<" + p.index() + ">";
                    sb.append(" ").append(label);
                }
            }
            if (hasSubcommands) {
                sb.append(" [command]");
            }
            if (hasOptions) {
                sb.append(" [flags]");
            }
            sb.append("\n");

            // Also render alias shortcut synopsis (e.g. nx timer [command])
            String shortName = qualifiedName.replaceFirst("^nexus ", "nx ");
            if (!shortName.equals(qualifiedName)) {
                sb.append(String.format("  %s", shortName));
                if (!spec.positionalParameters().isEmpty()) {
                    for (PositionalParamSpec p : spec.positionalParameters()) {
                        String label = p.paramLabel() != null && !p.paramLabel().isBlank() ? p.paramLabel() : "<" + p.index() + ">";
                        sb.append(" ").append(label);
                    }
                }
                if (hasSubcommands) {
                    sb.append(" [command]");
                }
                if (hasOptions) {
                    sb.append(" [flags]");
                }
                sb.append("\n");
            }
            sb.append("\n");
            return help.colorScheme().ansi().text(sb.toString()).toString();
        });

        // Prominent ALIASES section
        map.put("aliases", help -> {
            String[] aliases = spec.aliases();
            if (aliases != null && aliases.length > 0) {
                return help.colorScheme().ansi().text(String.format(
                        "@|bold,yellow ALIASES|@%n  @|bold,blue %s|@%n%n", String.join(", ", aliases)
                )).toString();
            }
            return "";
        });

        // Clean 2-column commands table showing primary names only
        if (!spec.subcommands().isEmpty()) {
            map.put(UsageMessageSpec.SECTION_KEY_COMMAND_LIST_HEADING, help ->
                    help.colorScheme().ansi().text(String.format("@|bold,yellow COMMANDS|@%n")).toString()
            );

            map.put(UsageMessageSpec.SECTION_KEY_COMMAND_LIST, help -> {
                StringBuilder sb = new StringBuilder();
                Set<String> seen = new HashSet<>();
                for (CommandLine sub : spec.subcommands().values()) {
                    String subName = sub.getCommandName();
                    if (seen.add(subName)) {
                        String[] descArr = sub.getCommandSpec().usageMessage().description();
                        String subDesc = descArr.length > 0 ? String.join(" ", descArr) : "";
                        sb.append(String.format("  @|bold,cyan %-14s|@  %s%n", subName, subDesc));
                    }
                }
                sb.append(String.format("%n"));
                return help.colorScheme().ansi().text(sb.toString()).toString();
            });
        }

        if (!spec.positionalParameters().isEmpty()) {
            map.put(UsageMessageSpec.SECTION_KEY_PARAMETER_LIST_HEADING, help ->
                    help.colorScheme().ansi().text(String.format("@|bold,yellow ARGUMENTS|@%n")).toString()
            );

            map.put(UsageMessageSpec.SECTION_KEY_PARAMETER_LIST, help -> {
                StringBuilder sb = new StringBuilder();
                for (PositionalParamSpec p : spec.positionalParameters()) {
                    if (p.hidden()) continue;
                    String label = p.paramLabel() != null && !p.paramLabel().isBlank() ? p.paramLabel() : "<arg>";
                    String desc = p.description().length > 0 ? String.join(" ", p.description()) : "";
                    sb.append(String.format("  @|bold,magenta %-22s|@  %s%n", label, desc));
                }
                sb.append(String.format("%n"));
                return help.colorScheme().ansi().text(sb.toString()).toString();
            });
        }

        if (!spec.options().isEmpty()) {
            map.put(UsageMessageSpec.SECTION_KEY_OPTION_LIST_HEADING, help ->
                    help.colorScheme().ansi().text(String.format("@|bold,yellow FLAGS|@%n")).toString()
            );
            configureOptionListRenderer(cmd, spec);
        }

        // Targeted subcommand examples
        String baseName = spec.name();
        if (SUBCOMMAND_EXAMPLES.containsKey(baseName)) {
            map.put("examples", help -> {
                List<String> examples = SUBCOMMAND_EXAMPLES.get(baseName);
                StringBuilder sb = new StringBuilder("@|bold,yellow EXAMPLES|@\n");
                for (String ex : examples) {
                    int commentIdx = ex.indexOf("#");
                    if (commentIdx > 0) {
                        String cmdPart = ex.substring(0, commentIdx).trim();
                        String commentPart = ex.substring(commentIdx);
                        sb.append(String.format("  $ @|bold,white %s|@  @|faint %s|@%n", cmdPart, commentPart));
                    } else {
                        sb.append(String.format("  $ @|bold,white %s|@%n", ex));
                    }
                }
                sb.append(String.format("%n"));
                return help.colorScheme().ansi().text(sb.toString()).toString();
            });
        }

        List<String> keys = new ArrayList<>();
        keys.add(UsageMessageSpec.SECTION_KEY_HEADER);
        keys.add(UsageMessageSpec.SECTION_KEY_SYNOPSIS);
        keys.add("aliases");
        if (!spec.subcommands().isEmpty()) {
            keys.add(UsageMessageSpec.SECTION_KEY_COMMAND_LIST_HEADING);
            keys.add(UsageMessageSpec.SECTION_KEY_COMMAND_LIST);
        }
        if (!spec.positionalParameters().isEmpty()) {
            keys.add(UsageMessageSpec.SECTION_KEY_PARAMETER_LIST_HEADING);
            keys.add(UsageMessageSpec.SECTION_KEY_PARAMETER_LIST);
        }
        if (!spec.options().isEmpty()) {
            keys.add(UsageMessageSpec.SECTION_KEY_OPTION_LIST_HEADING);
            keys.add(UsageMessageSpec.SECTION_KEY_OPTION_LIST);
        }
        if (SUBCOMMAND_EXAMPLES.containsKey(baseName)) {
            keys.add("examples");
        }
        keys.add(UsageMessageSpec.SECTION_KEY_FOOTER);

        cmd.setHelpSectionKeys(keys);
    }

    private static void configureOptionListRenderer(CommandLine cmd, CommandSpec spec) {
        cmd.getHelpSectionMap().put(UsageMessageSpec.SECTION_KEY_OPTION_LIST, help -> {
            List<OptionSpec> options = spec.options();
            if (options.isEmpty()) return "";

            int maxCol = 22;
            for (OptionSpec opt : options) {
                if (opt.hidden()) continue;
                String names = String.join(", ", opt.names());
                if (opt.paramLabel() != null && !opt.paramLabel().isBlank()
                        && !opt.type().equals(boolean.class) && !opt.type().equals(Boolean.class)) {
                    names += " " + opt.paramLabel();
                }
                if (names.length() <= 32) {
                    maxCol = Math.max(maxCol, names.length());
                }
            }
            int optColWidth = maxCol + 2;

            StringBuilder sb = new StringBuilder();
            for (OptionSpec opt : options) {
                if (opt.hidden()) continue;
                String names = String.join(", ", opt.names());
                if (opt.paramLabel() != null && !opt.paramLabel().isBlank()
                        && !opt.type().equals(boolean.class) && !opt.type().equals(Boolean.class)) {
                    names += " " + opt.paramLabel();
                }
                String desc = opt.description().length > 0 ? String.join(" ", opt.description()) : "";
                if (names.length() > 32) {
                    sb.append(String.format("  @|bold,green %s|@%n  %" + optColWidth + "s%s%n", names, "", desc));
                } else {
                    sb.append(String.format("  @|bold,green %-" + optColWidth + "s|@%s%n", names, desc));
                }
            }
            sb.append(String.format("%n"));
            return help.colorScheme().ansi().text(sb.toString()).toString();
        });
    }

    private static ColorScheme buildColorScheme() {
        return new ColorScheme.Builder()
                .commands(Help.Ansi.Style.bold, Help.Ansi.Style.fg_cyan)
                .options(Help.Ansi.Style.bold, Help.Ansi.Style.fg_green)
                .parameters(Help.Ansi.Style.fg_magenta)
                .optionParams(Help.Ansi.Style.italic, Help.Ansi.Style.fg_magenta)
                .errors(Help.Ansi.Style.bold, Help.Ansi.Style.fg_red)
                .stackTraces(Help.Ansi.Style.faint)
                .build();
    }

    public static int resolveTerminalWidth() {
        String envCols = System.getenv("COLUMNS");
        if (envCols != null) {
            try {
                int cols = Integer.parseInt(envCols.trim());
                if (cols >= 80) {
                    return Math.min(cols, MAX_HELP_WIDTH);
                }
            } catch (Exception ignored) {}
        }

        try {
            Process p = new ProcessBuilder("tput", "cols").start();
            byte[] bytes = p.getInputStream().readAllBytes();
            if (p.waitFor() == 0) {
                String out = new String(bytes, StandardCharsets.UTF_8).trim();
                int cols = Integer.parseInt(out);
                if (cols >= 80) {
                    return Math.max(MIN_HELP_WIDTH, Math.min(cols, MAX_HELP_WIDTH));
                }
            }
        } catch (Exception ignored) {}

        return DEFAULT_HELP_WIDTH;
    }
}
