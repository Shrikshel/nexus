package com.nexus.capabilities.storage.zfs;

import com.nexus.capabilities.storage.zfs.dto.ZfsSnapshotInfo;
import com.nexus.capabilities.storage.zfs.dto.ZfsSnapshotListReport;
import com.nexus.core.capability.*;
import com.nexus.core.security.Permission;
import com.nexus.platform.ZfsLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Capability to query and list ZFS snapshots across datasets or for a specific dataset.
 */
@Component
public class ZfsSnapshotListCapability implements Capability<String, ZfsSnapshotListReport> {

    public static final String ID = "zfs.snapshot.list";
    private static final Logger log = LoggerFactory.getLogger(ZfsSnapshotListCapability.class);
    private static final Pattern SAFE_DATASET_NAME = Pattern.compile("^[a-zA-Z0-9._/-]+$");

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "List ZFS Snapshots";
    }

    @Override
    public String getDescription() {
        return "Lists ZFS snapshots across all datasets or for a specific dataset hierarchy";
    }

    @Override
    public CapabilityCategory getCategory() {
        return CapabilityCategory.STORAGE;
    }

    @Override
    public Set<Permission> getRequiredPermissions() {
        return Set.of(Permission.READ_STORAGE);
    }

    @Override
    public Class<String> getInputType() {
        return String.class;
    }

    @Override
    public Class<ZfsSnapshotListReport> getOutputType() {
        return ZfsSnapshotListReport.class;
    }

    @Override
    public CapabilityResult<ZfsSnapshotListReport> execute(ExecutionContext context, String targetDataset) {
        Instant start = Instant.now();

        if (!ZfsLocator.isZfsInstalled()) {
            return CapabilityResult.success(ID, ZfsSnapshotListReport.notInstalled(), start);
        }

        String binary = ZfsLocator.findZfsBinary();
        List<String> cmd = new ArrayList<>();
        cmd.add(binary);
        cmd.add("list");
        cmd.add("-t");
        cmd.add("snapshot");
        cmd.add("-H");
        cmd.add("-p");
        cmd.add("-o");
        cmd.add("name,used,refer,creation");

        if (targetDataset != null && !targetDataset.isBlank()) {
            String cleanTarget = targetDataset.trim();
            if (!SAFE_DATASET_NAME.matcher(cleanTarget).matches()) {
                return CapabilityResult.failure(ID, ErrorCode.INVALID_INPUT, "Invalid dataset name for snapshot query", start);
            }
            cmd.add("-r");
            cmd.add(cleanTarget);
        }

        try {
            Process process = new ProcessBuilder(cmd).start();

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            try (BufferedReader outReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = outReader.readLine()) != null) {
                    stdout.append(line).append("\n");
                }
                while ((line = errReader.readLine()) != null) {
                    stderr.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return CapabilityResult.failure(ID, ErrorCode.TIMEOUT, "zfs list snapshots timed out", start);
            }

            String outStr = stdout.toString().trim();
            String errStr = stderr.toString().trim();

            if (outStr.contains("no datasets available") || errStr.contains("no datasets available")) {
                return CapabilityResult.success(ID, ZfsSnapshotListReport.empty("No ZFS snapshots available on this host"), start);
            }

            if (process.exitValue() != 0 && outStr.isBlank()) {
                return CapabilityResult.success(ID, ZfsSnapshotListReport.empty(errStr.isBlank() ? "zfs returned exit code " + process.exitValue() : errStr), start);
            }

            List<ZfsSnapshotInfo> snapshots = parseSnapshotListOutput(outStr);
            ZfsSnapshotListReport report = new ZfsSnapshotListReport(
                    true,
                    snapshots.size(),
                    snapshots,
                    snapshots.isEmpty() ? "No ZFS snapshots found" : "Total snapshots: " + snapshots.size(),
                    Instant.now()
            );

            return CapabilityResult.success(ID, report, start);
        } catch (Exception e) {
            log.error("Failed to list zfs snapshots: {}", e.getMessage());
            return CapabilityResult.failure(ID, ErrorCode.INTERNAL_ERROR, "Failed to list zfs snapshots: " + e.getMessage(), start);
        }
    }

    public List<ZfsSnapshotInfo> parseSnapshotListOutput(String output) {
        List<ZfsSnapshotInfo> list = new ArrayList<>();
        if (output == null || output.isBlank() || output.contains("no datasets available")) {
            return list;
        }

        for (String line : output.split("\n")) {
            line = line.trim();
            if (line.isBlank() || line.startsWith("NAME")) continue;

            String[] parts = line.split("\t");
            if (parts.length >= 4) {
                String fullName = parts[0];
                long used = parseSafeLong(parts[1]);
                long refer = parseSafeLong(parts[2]);
                String creation = parts[3];

                String dataset = fullName.contains("@") ? fullName.substring(0, fullName.indexOf('@')) : fullName;
                String tag = fullName.contains("@") ? fullName.substring(fullName.indexOf('@') + 1) : "";

                list.add(new ZfsSnapshotInfo(fullName, dataset, tag, used, refer, creation));
            }
        }
        return list;
    }

    private long parseSafeLong(String str) {
        try {
            return Long.parseLong(str.trim());
        } catch (Exception e) {
            return 0L;
        }
    }
}
