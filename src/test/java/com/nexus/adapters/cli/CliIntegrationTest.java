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
    @DisplayName("nexus system info outputs formatted system information")
    void testInfoCommand() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("system", "info");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("=== Nexus Host Information ===");
        assertThat(output).contains("Hostname");
        assertThat(output).contains("OS / Platform");
    }

    @Test
    @DisplayName("nexus system info --json outputs structured JSON report")
    void testInfoCommandJson() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("system", "info", "--json");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("\"capabilityId\" : \"sys.info\"");
        assertThat(output).contains("\"status\" : \"SUCCESS\"");
        assertThat(output).contains("\"hostname\"");
    }

    @Test
    @DisplayName("nexus system uptime outputs formatted uptime duration")
    void testUptimeCommand() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("system", "uptime");

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
    @DisplayName("nexus system disk displays filesystem mounts table")
    void testDiskListCommand() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("system", "disk");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("=== Filesystem Storage ===");
        assertThat(output).contains("FILESYSTEM");
        assertThat(output).contains("Aggregate:");
    }

    @Test
    @DisplayName("nexus system disk --json displays structured JSON")
    void testDiskListCommandJson() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("system", "disk", "--json");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("\"capabilityId\" : \"disk.list\"");
        assertThat(output).contains("\"partitions\"");
    }

    @Test
    @DisplayName("nexus system cpu displays CPU cores and load")
    void testCpuCommand() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("system", "cpu");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("=== CPU Utilization & Load ===");
        assertThat(output).contains("Logical Cores");
    }

    @Test
    @DisplayName("nexus system memory displays RAM utilization")
    void testMemoryCommand() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("system", "memory");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("=== Host Memory Utilization ===");
        assertThat(output).contains("Total RAM");
    }

    @Test
    @DisplayName("nexus system process displays process table")
    void testProcessListCommand() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("system", "process");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("=== Running Processes ===");
        assertThat(output).contains("PID");
    }

    @Test
    @DisplayName("nexus system network displays interfaces")
    void testNetworkCommand() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("system", "network");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("=== Network Interfaces ===");
    }

    @Test
    @DisplayName("nexus system service displays service list")
    void testServicesCommand() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("system", "service");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("=== System Services ===");
    }

    @Test
    @DisplayName("nexus daemon status displays daemon status")
    void testStatusCommand() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("daemon", "status");

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
    @DisplayName("nexus system info displays host timezone and localized timestamp")
    void testHostTimezoneDisplayed() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("system", "info");

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

    @Test
    @DisplayName("nexus system displays available subcommands")
    void testSystemHelpCommand() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("system", "--help");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("Usage: nexus system");
        assertThat(output).contains("update");
        assertThat(output).contains("upgrade");
        assertThat(output).contains("info");
        assertThat(output).contains("audit");
        assertThat(output).contains("disk");
    }

    @Test
    @DisplayName("nexus daemon displays available subcommands")
    void testDaemonHelpCommand() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("daemon", "--help");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("Usage: nexus daemon");
        assertThat(output).contains("start");
        assertThat(output).contains("stop");
        assertThat(output).contains("status");
    }

    @Test
    @DisplayName("nexus system audit runs self audit successfully")
    void testSystemAuditCommand() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("system", "audit");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("=== Nexus System Health & Security Audit ===");
        assertThat(output).contains("Summary:");
    }

    @Test
    @DisplayName("nexus system audit --json outputs structured audit report")
    void testSystemAuditCommandJson() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("system", "audit", "--json");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("\"capabilityId\" : \"sys.audit\"");
        assertThat(output).contains("\"overallStatus\"");
    }

    @Test
    @DisplayName("nexus system update --help displays options")
    void testSystemUpdateHelp() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("system", "update", "--help");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("Usage: nexus system update");
    }

    @Test
    @DisplayName("nexus system upgrade --help displays options")
    void testSystemUpgradeHelp() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("system", "upgrade", "--help");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("Usage: nexus system upgrade");
        assertThat(output).contains("--dry-run");
        assertThat(output).contains("--yes");
    }
}
