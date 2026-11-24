package com.nexus;

import com.nexus.adapters.cli.NexusCliRootCommand;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;

/**
 * Entry point for the Nexus Host Management Agent.
 * Runs in headless mode (no embedded web server).
 */
@SpringBootApplication
@ImportRuntimeHints({com.nexus.config.LogbackRuntimeHints.class, com.nexus.config.SchedulerRuntimeHints.class})
public class NexusApplication implements CommandLineRunner, ExitCodeGenerator {

    private final IFactory factory;
    private final NexusCliRootCommand rootCommand;
    private int exitCode;

    public NexusApplication(IFactory factory, NexusCliRootCommand rootCommand) {
        this.factory = factory;
        this.rootCommand = rootCommand;
    }

    public static void main(String[] args) {
        com.nexus.platform.logging.NexusLogManager.initEarly();
        com.nexus.platform.HostTimezoneResolver.initHostTimezone();
        java.io.File resolvedConfigFile = com.nexus.config.NexusConfigResolver.resolveConfigFile(args);
        com.nexus.config.NexusConfigResolver.applySpringConfigProperties(resolvedConfigFile);

        boolean verbose = com.nexus.config.NexusConfigResolver.isVerboseRequested(args, resolvedConfigFile);
        if (verbose) {
            System.setProperty("nexus.verbose", "true");
            System.setProperty("nexus.log.level", "DEBUG");
            System.setProperty("nexus.console.level", "DEBUG");
            System.setProperty("logging.level.com.nexus", "DEBUG");
        } else {
            String cfgLogLevel = com.nexus.config.NexusConfigResolver.resolveConfiguredLogLevel(resolvedConfigFile);
            if (cfgLogLevel != null) {
                System.setProperty("nexus.log.level", cfgLogLevel);
                System.setProperty("logging.level.com.nexus", cfgLogLevel);
            }
        }

        SpringApplication app = new SpringApplication(NexusApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setBannerMode(org.springframework.boot.Banner.Mode.OFF);
        app.setLogStartupInfo(verbose);
        int code = SpringApplication.exit(app.run(args));
        if (code != 0) {
            System.exit(code);
        }
    }

    @Override
    public void run(String... args) {
        CommandLine commandLine = new CommandLine(rootCommand, factory);
        commandLine.setCaseInsensitiveEnumValuesAllowed(true);
        com.nexus.adapters.cli.NexusCliHelpCustomizer.customize(commandLine);
        this.exitCode = commandLine.execute(args);
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
