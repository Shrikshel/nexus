package com.nexus.capabilities.storage.zfs;

import com.nexus.capabilities.storage.zfs.dto.ZfsDatasetDetailReport;
import com.nexus.capabilities.storage.zfs.dto.ZfsDatasetDetailRequest;
import com.nexus.core.capability.*;
import com.nexus.core.security.Permission;
import com.nexus.platform.ZfsLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Capability to inspect comprehensive configuration and runtime properties of a particular ZFS dataset.
 */
@Component
public class ZfsDatasetDetailCapability implements Capability<ZfsDatasetDetailRequest, ZfsDatasetDetailReport> {

    public static final String ID = "zfs.dataset.get";
    private static final Logger log = LoggerFactory.getLogger(ZfsDatasetDetailCapability.class);
    private static final Pattern SAFE_DATASET_NAME = Pattern.compile("^[a-zA-Z0-9._/-]+$");

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "Get ZFS Dataset Details";
    }

    @Override
    public String getDescription() {
        return "Inspects detailed configuration, compression, quotas, and properties for a specific ZFS dataset";
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
    public Class<ZfsDatasetDetailRequest> getInputType() {
        return ZfsDatasetDetailRequest.class;
    }

    @Override
    public Class<ZfsDatasetDetailReport> getOutputType() {
        return ZfsDatasetDetailReport.class;
    }

    @Override
    public CapabilityResult<ZfsDatasetDetailReport> execute(ExecutionContext context, ZfsDatasetDetailRequest input) {
        Instant start = Instant.now();

        if (input == null || input.datasetName() == null || input.datasetName().isBlank()) {
            return CapabilityResult.failure(ID, ErrorCode.INVALID_INPUT, "Dataset name must be specified", start);
        }

        String datasetName = input.datasetName().trim();
        if (!SAFE_DATASET_NAME.matcher(datasetName).matches()) {
            return CapabilityResult.failure(ID, ErrorCode.INVALID_INPUT, "Invalid dataset name format", start);
        }

        if (!ZfsLocator.isZfsInstalled()) {
            return CapabilityResult.success(ID, ZfsDatasetDetailReport.notInstalled(datasetName), start);
        }

        String binary = ZfsLocator.findZfsBinary();
        List<String> cmd = new ArrayList<>();
        cmd.add(binary);
        cmd.add("get");
        cmd.add("-H");
        cmd.add("-o");
        cmd.add("property,value");

        if (input.properties() != null && !input.properties().isEmpty()) {
            cmd.add(String.join(",", input.properties()));
        } else {
            cmd.add("all");
        }
        cmd.add(datasetName);

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
                return CapabilityResult.failure(ID, ErrorCode.TIMEOUT, "zfs get timed out", start);
            }

            String outStr = stdout.toString().trim();
            String errStr = stderr.toString().trim();

            if (process.exitValue() != 0 || outStr.isBlank()) {
                String msg = errStr.isBlank() ? "Dataset '" + datasetName + "' not found" : errStr;
                return CapabilityResult.success(ID, ZfsDatasetDetailReport.notFound(datasetName, msg), start);
            }

            Map<String, String> props = parseDatasetProperties(outStr);
            String type = props.getOrDefault("type", "filesystem");

            ZfsDatasetDetailReport report = new ZfsDatasetDetailReport(
                    true,
                    datasetName,
                    type,
                    props,
                    "Loaded " + props.size() + " properties for '" + datasetName + "'",
                    Instant.now()
            );

            return CapabilityResult.success(ID, report, start);
        } catch (Exception e) {
            log.error("Failed to query zfs get: {}", e.getMessage());
            return CapabilityResult.failure(ID, ErrorCode.INTERNAL_ERROR, "Failed to get dataset properties: " + e.getMessage(), start);
        }
    }

    public Map<String, String> parseDatasetProperties(String output) {
        Map<String, String> map = new LinkedHashMap<>();
        if (output == null || output.isBlank()) {
            return map;
        }

        for (String line : output.split("\n")) {
            line = line.trim();
            if (line.isBlank() || line.startsWith("PROPERTY")) continue;

            String[] parts = line.split("\t");
            if (parts.length >= 2) {
                String prop = parts[0].trim();
                String val = parts[1].trim();
                map.put(prop, val);
            }
        }
        return map;
    }
}
