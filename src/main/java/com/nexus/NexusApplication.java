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
@ImportRuntimeHints(com.nexus.config.LogbackRuntimeHints.class)
public class NexusApplication implements CommandLineRunner, ExitCodeGenerator {

    private final IFactory factory;
    private final NexusCliRootCommand rootCommand;
    private int exitCode;

    public NexusApplication(IFactory factory, NexusCliRootCommand rootCommand) {
        this.factory = factory;
        this.rootCommand = rootCommand;
    }

    public static void main(String[] args) {
        com.nexus.platform.HostTimezoneResolver.initHostTimezone();
        java.io.File resolvedConfigFile = com.nexus.config.NexusConfigResolver.resolveConfigFile(args);
        com.nexus.config.NexusConfigResolver.applySpringConfigProperties(resolvedConfigFile);

        SpringApplication app = new SpringApplication(NexusApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setBannerMode(org.springframework.boot.Banner.Mode.OFF);
        int code = SpringApplication.exit(app.run(args));
        if (code != 0) {
            System.exit(code);
        }
    }

    @Override
    public void run(String... args) {
        CommandLine commandLine = new CommandLine(rootCommand, factory);
        commandLine.setCaseInsensitiveEnumValuesAllowed(true);
        this.exitCode = commandLine.execute(args);
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
