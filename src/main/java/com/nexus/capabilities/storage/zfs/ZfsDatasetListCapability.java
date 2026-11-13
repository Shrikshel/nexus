package com.nexus.capabilities.storage.zfs;

import com.nexus.capabilities.storage.zfs.dto.ZfsDatasetInfo;
import com.nexus.capabilities.storage.zfs.dto.ZfsDatasetListReport;
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

/**
 * Capability to query and list ZFS filesystems and volume datasets.
 */
@Component
public class ZfsDatasetListCapability implements Capability<Void, ZfsDatasetListReport> {

    public static final String ID = "zfs.dataset.list";
    private static final Logger log = LoggerFactory.getLogger(ZfsDatasetListCapability.class);

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "List ZFS Datasets";
    }

    @Override
    public String getDescription() {
        return "Lists ZFS filesystem and volume datasets, used/available bytes, and mountpoints";
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
    public Class<Void> getInputType() {
        return Void.class;
    }

    @Override
    public Class<ZfsDatasetListReport> getOutputType() {
        return ZfsDatasetListReport.class;
    }

    @Override
    public CapabilityResult<ZfsDatasetListReport> execute(ExecutionContext context, Void input) {
        Instant start = Instant.now();

        if (!ZfsLocator.isZfsInstalled()) {
            return CapabilityResult.success(ID, ZfsDatasetListReport.notInstalled(), start);
        }

        String binary = ZfsLocator.findZfsBinary();
        try {
            Process process = new ProcessBuilder(
                    binary,
                    "list",
                    "-H",
                    "-p",
                    "-o",
                    "name,type,used,avail,refer,mountpoint"
            ).start();

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
                return CapabilityResult.failure(ID, ErrorCode.TIMEOUT, "zfs list timed out", start);
            }

            String outStr = stdout.toString().trim();
            String errStr = stderr.toString().trim();

            if (outStr.contains("no datasets available") || errStr.contains("no datasets available")) {
                return CapabilityResult.success(ID, ZfsDatasetListReport.empty("No ZFS datasets available on this host"), start);
            }

            if (process.exitValue() != 0 && outStr.isBlank()) {
                return CapabilityResult.success(ID, ZfsDatasetListReport.empty(errStr.isBlank() ? "zfs returned exit code " + process.exitValue() : errStr), start);
            }

            List<ZfsDatasetInfo> datasets = parseDatasetListOutput(outStr);
            ZfsDatasetListReport report = new ZfsDatasetListReport(
                    true,
                    datasets.size(),
                    datasets,
                    datasets.isEmpty() ? "No ZFS datasets found" : "Total datasets: " + datasets.size(),
                    Instant.now()
            );

            return CapabilityResult.success(ID, report, start);
        } catch (Exception e) {
            log.error("Failed to list zfs datasets: {}", e.getMessage());
            return CapabilityResult.failure(ID, ErrorCode.INTERNAL_ERROR, "Failed to list zfs datasets: " + e.getMessage(), start);
        }
    }

    public List<ZfsDatasetInfo> parseDatasetListOutput(String output) {
        List<ZfsDatasetInfo> datasets = new ArrayList<>();
        if (output == null || output.isBlank() || output.contains("no datasets available")) {
            return datasets;
        }

        for (String line : output.split("\n")) {
            line = line.trim();
            if (line.isBlank() || line.startsWith("NAME")) continue;

            String[] parts = line.split("\t");
            if (parts.length >= 6) {
                String name = parts[0];
                String type = parts[1];
                long used = parseSafeLong(parts[2]);
                long avail = parseSafeLong(parts[3]);
                long refer = parseSafeLong(parts[4]);
                String mountpoint = parts[5];

                datasets.add(new ZfsDatasetInfo(name, type, used, avail, refer, mountpoint));
            }
        }
        return datasets;
    }

    private long parseSafeLong(String str) {
        try {
            return Long.parseLong(str.trim());
        } catch (Exception e) {
            return 0L;
        }
    }
}
