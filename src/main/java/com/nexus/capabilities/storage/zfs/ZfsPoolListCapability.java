package com.nexus.capabilities.storage.zfs;

import com.nexus.capabilities.storage.zfs.dto.ZfsPoolInfo;
import com.nexus.capabilities.storage.zfs.dto.ZfsPoolListReport;
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
 * Capability to query and list all ZFS storage pools configured on the host.
 */
@Component
public class ZfsPoolListCapability implements Capability<Void, ZfsPoolListReport> {

    public static final String ID = "zfs.pool.list";
    private static final Logger log = LoggerFactory.getLogger(ZfsPoolListCapability.class);

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "List ZFS Storage Pools";
    }

    @Override
    public String getDescription() {
        return "Lists configured ZFS storage pools, capacity utilization, health status, and fragmentation";
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
    public Class<ZfsPoolListReport> getOutputType() {
        return ZfsPoolListReport.class;
    }

    @Override
    public CapabilityResult<ZfsPoolListReport> execute(ExecutionContext context, Void input) {
        Instant start = Instant.now();

        if (!ZfsLocator.isZfsInstalled()) {
            return CapabilityResult.success(ID, ZfsPoolListReport.notInstalled(), start);
        }

        String binary = ZfsLocator.findZpoolBinary();
        try {
            Process process = new ProcessBuilder(
                    binary,
                    "list",
                    "-H",
                    "-p",
                    "-o",
                    "name,size,alloc,free,frag,cap,dedup,health,altroot"
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
                return CapabilityResult.failure(ID, ErrorCode.TIMEOUT, "zpool list timed out", start);
            }

            String outStr = stdout.toString().trim();
            String errStr = stderr.toString().trim();

            if (outStr.contains("no pools available") || errStr.contains("no pools available")) {
                return CapabilityResult.success(ID, ZfsPoolListReport.empty("No ZFS pools available on this host"), start);
            }

            if (process.exitValue() != 0 && outStr.isBlank()) {
                return CapabilityResult.success(ID, ZfsPoolListReport.empty(errStr.isBlank() ? "zpool returned exit code " + process.exitValue() : errStr), start);
            }

            List<ZfsPoolInfo> pools = parsePoolListOutput(outStr);
            ZfsPoolListReport report = new ZfsPoolListReport(
                    true,
                    pools.size(),
                    pools,
                    pools.isEmpty() ? "No ZFS pools available" : "Active ZFS pools: " + pools.size(),
                    Instant.now()
            );

            return CapabilityResult.success(ID, report, start);
        } catch (Exception e) {
            log.error("Failed to query zpool list: {}", e.getMessage());
            return CapabilityResult.failure(ID, ErrorCode.INTERNAL_ERROR, "Failed to query zpool: " + e.getMessage(), start);
        }
    }

    public List<ZfsPoolInfo> parsePoolListOutput(String output) {
        List<ZfsPoolInfo> pools = new ArrayList<>();
        if (output == null || output.isBlank() || output.contains("no pools available")) {
            return pools;
        }

        for (String line : output.split("\n")) {
            line = line.trim();
            if (line.isBlank() || line.startsWith("NAME")) continue;

            String[] parts = line.split("\t");
            if (parts.length >= 8) {
                String name = parts[0];
                long size = parseSafeLong(parts[1]);
                long alloc = parseSafeLong(parts[2]);
                long free = parseSafeLong(parts[3]);
                int frag = parseSafePercent(parts[4]);
                int cap = parseSafePercent(parts[5]);
                double dedup = parseSafeDouble(parts[6]);
                String health = parts[7];
                String altroot = parts.length > 8 ? parts[8] : "-";

                pools.add(new ZfsPoolInfo(name, size, alloc, free, frag, cap, dedup, health, altroot));
            }
        }
        return pools;
    }

    private long parseSafeLong(String str) {
        try {
            return Long.parseLong(str.trim());
        } catch (Exception e) {
            return 0L;
        }
    }

    private int parseSafePercent(String str) {
        try {
            String clean = str.replace("%", "").trim();
            if ("-".equals(clean)) return 0;
            return Integer.parseInt(clean);
        } catch (Exception e) {
            return 0;
        }
    }

    private double parseSafeDouble(String str) {
        try {
            String clean = str.replace("x", "").trim();
            return Double.parseDouble(clean);
        } catch (Exception e) {
            return 1.0;
        }
    }
}
