package com.nexus.capabilities.storage.zfs;

import com.nexus.capabilities.storage.zfs.dto.ZfsPoolStatusReport;
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
 * Capability to inspect detailed ZFS pool health, scrub status, and vdev layout.
 */
@Component
public class ZfsPoolStatusCapability implements Capability<String, ZfsPoolStatusReport> {

    public static final String ID = "zfs.pool.status";
    private static final Logger log = LoggerFactory.getLogger(ZfsPoolStatusCapability.class);
    private static final Pattern SAFE_POOL_NAME = Pattern.compile("^[a-zA-Z0-9._-]+$");

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "ZFS Storage Pool Status";
    }

    @Override
    public String getDescription() {
        return "Inspects detailed pool health, scrub diagnostics, vdev tree layout, and error counts";
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
    public Class<ZfsPoolStatusReport> getOutputType() {
        return ZfsPoolStatusReport.class;
    }

    @Override
    public CapabilityResult<ZfsPoolStatusReport> execute(ExecutionContext context, String poolName) {
        Instant start = Instant.now();

        if (!ZfsLocator.isZfsInstalled()) {
            return CapabilityResult.success(ID, ZfsPoolStatusReport.notInstalled(), start);
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(ZfsLocator.findZpoolBinary());
        cmd.add("status");

        if (poolName != null && !poolName.isBlank()) {
            String cleanName = poolName.trim();
            if (!SAFE_POOL_NAME.matcher(cleanName).matches()) {
                return CapabilityResult.failure(ID, ErrorCode.INVALID_INPUT,
                        "Invalid pool name: must contain only alphanumeric characters, '.', '-', or '_'", start);
            }
            cmd.add(cleanName);
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

            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return CapabilityResult.failure(ID, ErrorCode.TIMEOUT, "zpool status timed out", start);
            }

            String outStr = stdout.toString().trim();
            String errStr = stderr.toString().trim();

            if (outStr.contains("no pools available") || errStr.contains("no pools available")) {
                return CapabilityResult.success(ID, ZfsPoolStatusReport.empty("No ZFS pools available on this host"), start);
            }

            if (process.exitValue() != 0 && outStr.isBlank()) {
                return CapabilityResult.success(ID, ZfsPoolStatusReport.empty(errStr.isBlank() ? "zpool status exit code " + process.exitValue() : errStr), start);
            }

            ZfsPoolStatusReport report = parsePoolStatusOutput(outStr);
            return CapabilityResult.success(ID, report, start);
        } catch (Exception e) {
            log.error("Failed to query zpool status: {}", e.getMessage());
            return CapabilityResult.failure(ID, ErrorCode.INTERNAL_ERROR, "Failed to query zpool status: " + e.getMessage(), start);
        }
    }

    public ZfsPoolStatusReport parsePoolStatusOutput(String output) {
        if (output == null || output.isBlank() || output.contains("no pools available")) {
            return ZfsPoolStatusReport.empty("No ZFS pools available on this host");
        }

        String pool = "All";
        String state = "UNKNOWN";
        String status = "N/A";
        String scan = "None";
        StringBuilder config = new StringBuilder();
        String errors = "No known data errors";

        boolean inConfig = false;
        for (String rawLine : output.split("\n")) {
            String line = rawLine.trim();
            if (line.startsWith("pool:")) {
                pool = line.substring("pool:".length()).trim();
                inConfig = false;
            } else if (line.startsWith("state:")) {
                state = line.substring("state:".length()).trim();
                inConfig = false;
            } else if (line.startsWith("status:")) {
                status = line.substring("status:".length()).trim();
                inConfig = false;
            } else if (line.startsWith("scan:")) {
                scan = line.substring("scan:".length()).trim();
                inConfig = false;
            } else if (line.startsWith("errors:")) {
                errors = line.substring("errors:".length()).trim();
                inConfig = false;
            } else if (line.startsWith("config:")) {
                inConfig = true;
            } else if (inConfig) {
                if (line.startsWith("errors:")) {
                    inConfig = false;
                    errors = line.substring("errors:".length()).trim();
                } else {
                    config.append(rawLine).append("\n");
                }
            }
        }

        return new ZfsPoolStatusReport(
                true,
                pool,
                state,
                status,
                scan,
                config.toString().trim(),
                errors,
                "State: " + state,
                Instant.now()
        );
    }
}
