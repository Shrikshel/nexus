package com.nexus.adapters.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = com.nexus.NexusApplication.class)
class CliIntegrationTest {

    @Autowired
    private IFactory factory;

    @Autowired
    private NexusCliRootCommand rootCommand;

    private CommandLine createCommandLine(StringWriter out, StringWriter err) {
        rootCommand.reset();
        CommandLine cmd = new CommandLine(rootCommand, factory);
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));
        return cmd;
    }

    @Test
    @DisplayName("nexus info outputs formatted system information")
    void testInfoCommand() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("info");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("=== Nexus Host Information ===");
        assertThat(output).contains("Hostname");
        assertThat(output).contains("OS / Platform");
    }

    @Test
    @DisplayName("nexus info --json outputs structured JSON report")
    void testInfoCommandJson() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("info", "--json");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("\"capabilityId\" : \"sys.info\"");
        assertThat(output).contains("\"status\" : \"SUCCESS\"");
        assertThat(output).contains("\"hostname\"");
    }

    @Test
    @DisplayName("nexus uptime outputs formatted uptime duration")
    void testUptimeCommand() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("uptime");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("=== Nexus Host Uptime ===");
        assertThat(output).contains("Host Uptime");
    }

    @Test
    @DisplayName("nexus capabilities list lists registered capabilities")
    void testCapabilitiesListCommand() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("capabilities", "list");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("=== Registered Nexus Capabilities ===");
        assertThat(output).contains("sys.info");
        assertThat(output).contains("sys.uptime");
        assertThat(output).contains("cap.list");
    }

    @Test
    @DisplayName("nexus capabilities describe sys.info displays schema")
    void testCapabilitiesDescribeCommand() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("capabilities", "describe", "sys.info");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("=== Capability Details ===");
        assertThat(output).contains("sys.info");
        assertThat(output).contains("Host System Information");
    }

    @Test
    @DisplayName("nexus disk list displays filesystem mounts table")
    void testDiskListCommand() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("disk", "list");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("=== Filesystem Storage ===");
        assertThat(output).contains("FILESYSTEM");
        assertThat(output).contains("Aggregate:");
    }

    @Test
    @DisplayName("nexus disk list --json displays structured JSON")
    void testDiskListCommandJson() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("disk", "list", "--json");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("\"capabilityId\" : \"disk.list\"");
        assertThat(output).contains("\"partitions\"");
    }

    @Test
    @DisplayName("nexus cpu displays CPU cores and load")
    void testCpuCommand() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("cpu");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("=== CPU Utilization & Load ===");
        assertThat(output).contains("Logical Cores");
    }

    @Test
    @DisplayName("nexus memory displays RAM utilization")
    void testMemoryCommand() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("memory");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("=== Host Memory Utilization ===");
        assertThat(output).contains("Total RAM");
    }

    @Test
    @DisplayName("nexus process list displays process table")
    void testProcessListCommand() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("process", "list");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("=== Running Processes ===");
        assertThat(output).contains("PID");
    }

    @Test
    @DisplayName("nexus network displays interfaces")
    void testNetworkCommand() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("network");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("=== Network Interfaces ===");
    }

    @Test
    @DisplayName("nexus services displays service list")
    void testServicesCommand() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("services");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("=== System Services ===");
    }

    @Test
    @DisplayName("nexus status displays daemon status")
    void testStatusCommand() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("status");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("=== Nexus Daemon Status ===");
    }

    @Test
    @DisplayName("nexus config without subcommand runs safely without NPE")
    void testConfigCommandWithoutSubcommand() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("config");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("=== Nexus Configuration Validation ===");
        assertThat(output).contains("Configuration is valid.");
    }

    @Test
    @DisplayName("nexus info displays host timezone and localized timestamp")
    void testHostTimezoneDisplayed() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("info");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("Timezone");
        assertThat(output).contains("Report Timestamp");
    }

    @Test
    @DisplayName("nexus config validate outputs valid status")
    void testConfigValidateCommand() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("config", "validate");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("=== Nexus Configuration Validation ===");
        assertThat(output).contains("Configuration is valid.");
    }

    @Test
    @DisplayName("nexus config edit --print displays nano command and config path")
    void testConfigEditPrintCommand() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("config", "edit", "--print");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("=== Nexus Configuration File ===");
        assertThat(output).contains("To edit this configuration file, run:");
        assertThat(output).contains("nano");
    }

    @Test
    @DisplayName("nexus config path prints absolute file path")
    void testConfigPathCommand() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("config", "path");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString().trim();
        assertThat(output).matches(".*\\.(yml|yaml)$");
    }

    @Test
    @DisplayName("nexus config show displays file contents or helpful guidance")
    void testConfigShowCommand() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("config", "show");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("# Configuration File:");
    }

    @Test
    @DisplayName("nexus config reload validates file when daemon is offline")
    void testConfigReloadOffline() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("config", "reload");

        // Returns 0 or 1 depending on whether config file exists on test machine, but must not crash
        assertThat(exitCode).isIn(0, 1);
        String combined = out.toString() + err.toString();
        assertThat(combined).isNotEmpty();
    }

    @Test
    @DisplayName("nexus timer defaults to listing scheduled timers")
    void testTimerDefaultCommand() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("timer");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("=== Scheduled Timers & Automation Tasks");
    }

    @Test
    @DisplayName("nexus timer list lists scheduled timers")
    void testTimerListCommand() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("timer", "list");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("=== Scheduled Timers & Automation Tasks");
    }

    @Test
    @DisplayName("nexus timer --json outputs structured JSON report")
    void testTimerListCommandJson() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("timer", "--json");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("\"capabilityId\" : \"timer.list\"");
        assertThat(output).contains("\"status\" : \"SUCCESS\"");
    }

    @Test
    @DisplayName("nexus telegram displays usage information")
    void testTelegramHelpCommand() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("telegram", "--help");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("Usage: nexus telegram");
        assertThat(output).contains("send");
    }

    @Test
    @DisplayName("nexus telegram send displays usage options")
    void testTelegramSendHelpCommand() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("telegram", "send", "--help");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("Usage: nexus telegram send");
        assertThat(output).contains("--recipient");
        assertThat(output).contains("--caption");
    }
}
