package com.nexus.capabilities.resource;

import com.nexus.capabilities.resource.dto.MemoryReport;
import com.nexus.core.capability.*;
import com.nexus.core.security.Permission;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.time.Instant;
import java.util.Set;

/**
 * Capability providing memory and swap utilization metrics.
 */
@Component
public class MemoryCapability implements Capability<Void, MemoryReport> {

    public static final String ID = "sys.memory";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "Host Memory Utilization";
    }

    @Override
    public String getDescription() {
        return "Returns physical RAM total, used, free, and swap space statistics";
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
    public Class<MemoryReport> getOutputType() {
        return MemoryReport.class;
    }

    @Override
    public CapabilityResult<MemoryReport> execute(ExecutionContext context, Void input) {
        Instant start = Instant.now();
        try {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();

            long totalMem = 0;
            long freeMem = 0;
            long totalSwap = 0;
            long freeSwap = 0;

            if (osBean instanceof com.sun.management.OperatingSystemMXBean sunBean) {
                totalMem = sunBean.getTotalMemorySize();
                freeMem = sunBean.getFreeMemorySize();
                totalSwap = sunBean.getTotalSwapSpaceSize();
                freeSwap = sunBean.getFreeSwapSpaceSize();
            } else {
                totalMem = Runtime.getRuntime().totalMemory();
                freeMem = Runtime.getRuntime().freeMemory();
            }

            long usedMem = totalMem - freeMem;
            double pct = totalMem > 0 ? (double) usedMem * 100.0 / (double) totalMem : 0.0;

            MemoryReport report = new MemoryReport(
                    totalMem,
                    freeMem,
                    usedMem,
                    pct,
                    totalSwap,
                    freeSwap,
                    Instant.now()
            );

            return CapabilityResult.success(ID, report, start);
        } catch (Exception e) {
            return CapabilityResult.failure(ID, ErrorCode.INTERNAL_ERROR, "Failed to collect memory metrics: " + e.getMessage(), start);
        }
    }
}
