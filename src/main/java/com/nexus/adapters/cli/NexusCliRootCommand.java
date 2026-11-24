package com.nexus.adapters.cli;

import com.nexus.adapters.cli.commands.*;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.File;
import java.util.concurrent.Callable;

/**
 * Root Picocli command defining global CLI options and registering subcommands.
 */
@Component
@Command(
    name = "nexus",
    aliases = {"nx", "n", "nxs", "nexux"},
    description = "Nexus: Cross-Platform Host Management Agent",
    version = "Nexus 0.0.15-SNAPSHOT",
    mixinStandardHelpOptions = true,
    subcommands = {
        SystemCommand.class,
        DaemonCommand.class,
        LogsCommand.class,
        com.nexus.adapters.cli.commands.TimerCommand.class,
        com.nexus.adapters.cli.commands.TelegramCommand.class,
        TailscaleCommand.class,
        com.nexus.adapters.cli.commands.ZfsCommand.class,
        com.nexus.adapters.cli.commands.TmuxCommand.class,
        ConfigCommand.class,
        CapabilitiesCommand.class,
        UpdateCommand.class,
        UninstallCommand.class
    }
)
public class NexusCliRootCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Option(names = {"-j", "--json"}, description = "Format output as machine-readable JSON", scope = picocli.CommandLine.ScopeType.INHERIT)
    private boolean json;

    @Option(names = {"-v", "--verbose"}, description = "Enable verbose debug output", scope = picocli.CommandLine.ScopeType.INHERIT)
    private boolean verbose;

    @Option(names = {"-c", "--config"}, paramLabel = "<path>", description = "Path to custom configuration file (YAML)", scope = picocli.CommandLine.ScopeType.INHERIT)
    private File configFile;

    public boolean isJson() {
        return json;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public File getConfigFile() {
        if (configFile == null) {
            String resolved = com.nexus.config.NexusConfigResolver.getResolvedConfigPath();
            if (resolved != null) {
                return com.nexus.config.NexusConfigResolver.normalize(new File(resolved));
            }
        }
        return com.nexus.config.NexusConfigResolver.normalize(configFile);
    }

    public void reset() {
        this.json = false;
        this.verbose = false;
        this.configFile = null;
    }

    @Override
    public Integer call() {
        if (spec != null) {
            CommandLine cmd = spec.commandLine();
            NexusCliHelpCustomizer.customize(cmd);
            cmd.usage(cmd.getOut());
        }
        return 0;
    }
}
