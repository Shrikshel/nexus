package com.nexus.adapters.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class NexusCliHelpCustomizerTest {

    @Autowired
    private CommandLine.IFactory factory;

    @Autowired
    private NexusCliRootCommand rootCommand;

    @Test
    @DisplayName("Root help output renders branded header, 4 command categories, global flags, and examples")
    void testRootHelpRendering() {
        CommandLine cmd = new CommandLine(rootCommand, factory);
        NexusCliHelpCustomizer.customize(cmd);

        StringWriter sw = new StringWriter();
        cmd.usage(new PrintWriter(sw));
        String output = sw.toString();

        assertThat(output).contains("⚡ Nexus Host Management Agent");
        assertThat(output).contains("USAGE");
        assertThat(output).contains("CORE & SYSTEM");
        assertThat(output).contains("AUTOMATION & SERVICES");
        assertThat(output).contains("STORAGE & WORKSPACES");
        assertThat(output).contains("AGENT CONFIGURATION");
        assertThat(output).contains("GLOBAL FLAGS");
        assertThat(output).contains("EXAMPLES");
        assertThat(output).contains("LEARN MORE");

        // Verify primary commands are listed
        assertThat(output).contains("system");
        assertThat(output).contains("daemon");
        assertThat(output).contains("logs");
        assertThat(output).contains("timer");
        assertThat(output).contains("telegram");
        assertThat(output).contains("tailscale");
        assertThat(output).contains("zfs");
        assertThat(output).contains("tmux");
        assertThat(output).contains("config");
        assertThat(output).contains("capabilities");
        assertThat(output).contains("update");
        assertThat(output).contains("uninstall");
    }

    @Test
    @DisplayName("Subcommand help renders prominent ALIASES, commands table, flags, and targeted examples")
    void testSubcommandHelpRenderingWithAliases() {
        CommandLine cmd = new CommandLine(rootCommand, factory);
        NexusCliHelpCustomizer.customize(cmd);

        CommandLine timerCmd = cmd.getSubcommands().get("timer");
        assertThat(timerCmd).isNotNull();

        StringWriter sw = new StringWriter();
        timerCmd.usage(new PrintWriter(sw));
        String output = sw.toString();

        assertThat(output).contains("⚡ nexus timer");
        assertThat(output).contains("USAGE");
        assertThat(output).contains("ALIASES");
        assertThat(output).contains("timers, cron, schedule");
        assertThat(output).contains("COMMANDS");
        assertThat(output).contains("list");
        assertThat(output).contains("create");
        assertThat(output).contains("status");
        assertThat(output).contains("FLAGS");
        assertThat(output).contains("EXAMPLES");
        assertThat(output).contains("nx timer create backup-db");
    }

    @Test
    @DisplayName("Subcommand without aliases omits the ALIASES section cleanly")
    void testSubcommandWithoutAliasesOmitsAliasesSection() {
        CommandLine cmd = new CommandLine(rootCommand, factory);
        NexusCliHelpCustomizer.customize(cmd);

        CommandLine zfsCmd = cmd.getSubcommands().get("zfs");
        assertThat(zfsCmd).isNotNull();

        StringWriter sw = new StringWriter();
        zfsCmd.usage(new PrintWriter(sw));
        String output = sw.toString();

        assertThat(output).contains("⚡ nexus zfs");
        assertThat(output).doesNotContain("ALIASES");
        assertThat(output).contains("COMMANDS");
    }

    @Test
    @DisplayName("Terminal width resolver returns a bounded width between 95 and 140")
    void testResolveTerminalWidth() {
        int width = NexusCliHelpCustomizer.resolveTerminalWidth();
        assertThat(width).isBetween(95, 140);
    }
}
