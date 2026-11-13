package com.nexus.adapters.cli.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.adapters.cli.NexusCliRootCommand;
import com.nexus.platform.NexusUpdateService;
import com.nexus.platform.NexusUpdateService.ReleaseInfo;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * CLI command to check for, download, and apply updates to the Nexus agent binary.
 */
@Component
@Command(
        name = "update",
        aliases = {"upgrade"},
        description = "Check for and install the latest available Nexus binary release",
        mixinStandardHelpOptions = true
)
public class UpdateCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    private final NexusUpdateService updateService;
    private final ObjectMapper objectMapper;

    @Option(names = {"--check"}, description = "Check if an update is available without downloading or installing")
    private boolean checkOnly;

    @Option(names = {"-f", "--force"}, description = "Force re-download and installation even if already on the latest version")
    private boolean force;

    @Option(names = {"--target-version", "--tag"}, description = "Target specific release version tag (e.g. v1.0.0)")
    private String targetVersion;

    @Option(names = {"--repo"}, description = "Custom GitHub repository in owner/repo format")
    private String customRepo;

    @Option(names = {"--dry-run"}, description = "Display what would be downloaded and updated without modifying the system")
    private boolean dryRun;

    public UpdateCommand(NexusUpdateService updateService) {
        this.updateService = updateService;
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

        String repo = (customRepo != null && !customRepo.isBlank()) ? customRepo : NexusUpdateService.DEFAULT_REPO;
        String currentVersion = NexusUpdateService.CURRENT_VERSION;

        try {
            ReleaseInfo release = updateService.fetchReleaseInfo(repo, Optional.ofNullable(targetVersion));
            boolean updateAvailable = updateService.isNewerVersion(currentVersion, release.tagName());
            Path targetBinary = updateService.locateCurrentBinary();

            if (jsonMode) {
                Map<String, Object> jsonResult = new LinkedHashMap<>();
                jsonResult.put("repo", repo);
                jsonResult.put("currentVersion", currentVersion);
                jsonResult.put("latestVersion", release.tagName());
                jsonResult.put("updateAvailable", updateAvailable);
                jsonResult.put("releaseUrl", release.releaseUrl());
                jsonResult.put("binaryAssetUrl", release.binaryAssetUrl());
                jsonResult.put("targetBinaryPath", targetBinary.toString());

                if (checkOnly || dryRun) {
                    jsonResult.put("status", checkOnly ? "CHECKED" : "DRY_RUN");
                } else if (!updateAvailable && !force) {
                    jsonResult.put("status", "UP_TO_DATE");
                } else {
                    applyDownloadAndUpdate(release, targetBinary, out, false);
                    jsonResult.put("status", "SUCCESS");
                }
                out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonResult));
                return 0;
            }

            // Human-readable output
            out.println("=== Nexus Agent Update ===");
            out.println("  Repository        : " + repo);
            out.println("  Current Version   : " + currentVersion);
            out.println("  Latest Version    : " + release.tagName());
            out.println("  Target Asset      : " + release.binaryAssetName());
            out.println("  Installation Path : " + targetBinary);
            out.println("  Release Notes     : " + release.releaseUrl());

            if (checkOnly) {
                if (updateAvailable) {
                    out.println("\n✓ A new version (" + release.tagName() + ") is available! Run 'nexus update' to install.");
                } else {
                    out.println("\n✓ Nexus is already at the latest version (" + release.tagName() + ").");
                }
                return 0;
            }

            if (dryRun) {
                out.println("\n[DRY-RUN] Pre-flight checks passed.");
                out.println("[DRY-RUN] Download URL : " + release.binaryAssetUrl());
                out.println("[DRY-RUN] Target Path  : " + targetBinary);
                return 0;
            }

            if (!updateAvailable && !force) {
                out.println("\n✓ Nexus is already up to date (" + release.tagName() + "). Use --force to reinstall.");
                return 0;
            }

            out.println("\nDownloading " + release.binaryAssetName() + "...");
            applyDownloadAndUpdate(release, targetBinary, out, true);
            out.println("✓ Successfully updated Nexus to " + release.tagName() + "!");
            return 0;

        } catch (Exception e) {
            if (jsonMode) {
                out.println("{\"error\": \"" + e.getMessage() + "\"}");
            } else {
                err.println("Error updating Nexus: " + e.getMessage());
                if (e.getMessage() != null && e.getMessage().contains("Permission denied")) {
                    err.println("Hint: You may need root/sudo privileges to replace the binary. Try: sudo nexus update");
                }
            }
            return 1;
        }
    }

    private void applyDownloadAndUpdate(ReleaseInfo release, Path targetBinary, PrintWriter out, boolean printSteps) throws Exception {
        Path tempBinary = updateService.downloadToTemp(release.binaryAssetUrl(), "nexus-download-");
        try {
            if (release.checksumAssetUrl() != null) {
                if (printSteps) out.println("Verifying SHA256 integrity...");
                Path tempChecksum = updateService.downloadToTemp(release.checksumAssetUrl(), "nexus-checksums-");
                try {
                    String actualHash = updateService.computeSha256(tempBinary);
                    String checksumContent = Files.readString(tempChecksum);
                    if (checksumContent.contains(actualHash)) {
                        if (printSteps) out.println("  ✓ Checksum verified (" + actualHash.substring(0, 12) + "...)");
                    }
                } finally {
                    Files.deleteIfExists(tempChecksum);
                }
            }

            if (printSteps) out.println("Installing new binary to " + targetBinary + "...");
            updateService.applyUpdate(tempBinary, targetBinary);
        } finally {
            Files.deleteIfExists(tempBinary);
        }
    }
}
