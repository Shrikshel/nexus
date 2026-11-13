package com.nexus.adapters.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.platform.NexusUpdateService;
import com.nexus.platform.PlatformDetector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = com.nexus.NexusApplication.class)
class UpdateAndUninstallTest {

    @Autowired
    private IFactory factory;

    @Autowired
    private NexusCliRootCommand rootCommand;

    @Autowired
    private NexusUpdateService updateService;

    @Autowired
    private PlatformDetector platformDetector;

    private CommandLine createCommandLine(StringWriter out, StringWriter err) {
        rootCommand.reset();
        CommandLine cmd = new CommandLine(rootCommand, factory);
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));
        return cmd;
    }

    @Test
    @DisplayName("NexusUpdateService correctly resolves asset names for platform")
    void testResolveBinaryAssetName() {
        String assetName = updateService.resolveBinaryAssetName();
        assertThat(assetName).isNotBlank();
        assertThat(assetName).startsWith("nexus-");
        if (platformDetector.isLinux()) {
            assertThat(assetName).contains("linux");
        } else if (platformDetector.isMac()) {
            assertThat(assetName).contains("darwin");
        } else if (platformDetector.isWindows()) {
            assertThat(assetName).endsWith(".exe");
        }
    }

    @Test
    @DisplayName("NexusUpdateService version comparison handles release and snapshot versions")
    void testVersionComparison() {
        // Snapshot is considered older than a formal release
        assertThat(updateService.isNewerVersion("1.0.0-SNAPSHOT", "v1.0.0")).isTrue();
        assertThat(updateService.isNewerVersion("1.0.0", "v1.0.1")).isTrue();
        assertThat(updateService.isNewerVersion("1.0.0", "1.0.0")).isFalse();
        assertThat(updateService.isNewerVersion("v1.0.0", "1.0.0")).isFalse();
    }

    @Test
    @DisplayName("NexusUpdateService computes valid SHA256 checksums")
    void testComputeSha256() throws Exception {
        Path temp = Files.createTempFile("sha-test", ".txt");
        try {
            Files.writeString(temp, "Nexus host management agent test", StandardCharsets.UTF_8);
            String hash = updateService.computeSha256(temp);
            assertThat(hash).hasSize(64); // SHA256 hex is 64 characters
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Test
    @DisplayName("nexus uninstall --dry-run displays actions without removing files")
    void testUninstallDryRun() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("uninstall", "--dry-run");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("=== Nexus Uninstaller [DRY-RUN] ===");
        assertThat(output).contains("Executable to remove");
        assertThat(output).contains("[DRY-RUN] No files were removed.");
    }

    @Test
    @DisplayName("nexus uninstall --dry-run --json outputs structured JSON")
    void testUninstallDryRunJson() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("uninstall", "--dry-run", "--json");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("\"dryRun\" : true");
        assertThat(output).contains("\"binaryPath\"");
    }

    @Test
    @DisplayName("nexus update --help displays options")
    void testUpdateHelp() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("update", "--help");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("Check for and install the latest available Nexus binary release");
        assertThat(output).contains("--check");
        assertThat(output).contains("--force");
        assertThat(output).contains("--dry-run");
    }

    @Test
    @DisplayName("nexus uninstall --help displays options")
    void testUninstallHelp() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommandLine(out, err);

        int exitCode = cmd.execute("uninstall", "--help");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("Uninstall Nexus binary");
        assertThat(output).contains("--yes");
        assertThat(output).contains("--purge");
    }
}
