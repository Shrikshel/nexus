package com.nexus.capabilities.resource;

import com.nexus.capabilities.resource.dto.CpuReport;
import com.nexus.core.capability.*;
import com.nexus.core.security.Permission;
import org.springframework.stereotype.Component;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Set;

/**
 * Capability providing CPU load averages, cores, and processor utilization.
 */
@Component
public class CpuCapability implements Capability<Void, CpuReport> {

    public static final String ID = "sys.cpu";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "CPU Utilization and Load";
    }

    @Override
    public String getDescription() {
        return "Returns CPU processor count, load averages (1m, 5m, 15m), and utilization percentage";
    }

    @Override
    public CapabilityCategory getCategory() {
        return CapabilityCategory.RESOURCE;
    }

    @Override
    public Set<Permission> getRequiredPermissions() {
        return Set.of(Permission.READ_RESOURCES);
    }

    @Override
    public Class<Void> getInputType() {
        return Void.class;
    }

    @Override
    public Class<CpuReport> getOutputType() {
        return CpuReport.class;
    }

    @Override
    public CapabilityResult<CpuReport> execute(ExecutionContext context, Void input) {
        Instant start = Instant.now();
        try {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            int cores = osBean.getAvailableProcessors();

            double load1m = osBean.getSystemLoadAverage();
            double load5m = -1.0;
            double load15m = -1.0;

            // On Linux, /proc/loadavg contains all three: 1m, 5m, 15m
            File procLoad = new File("/proc/loadavg");
            if (procLoad.exists()) {
                try {
                    String content = Files.readString(procLoad.toPath()).trim();
                    String[] parts = content.split("\\s+");
                    if (parts.length >= 3) {
                        load1m = Double.parseDouble(parts[0]);
                        load5m = Double.parseDouble(parts[1]);
                        load15m = Double.parseDouble(parts[2]);
                    }
                } catch (Exception ignored) {
                }
            }

            double cpuPct = 0.0;
            if (osBean instanceof com.sun.management.OperatingSystemMXBean sunBean) {
                double load = sunBean.getCpuLoad();
                if (load >= 0) {
                    cpuPct = load * 100.0;
                }
            }

            CpuReport report = new CpuReport(cores, load1m, load5m, load15m, cpuPct, Instant.now());
            return CapabilityResult.success(ID, report, start);
        } catch (Exception e) {
            return CapabilityResult.failure(ID, ErrorCode.INTERNAL_ERROR, "Failed to collect CPU metrics: " + e.getMessage(), start);
        }
    }
}
