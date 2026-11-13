package com.nexus.capabilities.system;

import com.nexus.capabilities.system.dto.SystemInfoReport;
import com.nexus.core.capability.*;
import com.nexus.core.security.Permission;
import com.nexus.platform.SystemCollector;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Capability providing overall host system and hardware summary.
 */
@Component
public class SystemInfoCapability implements Capability<Void, SystemInfoReport> {

    public static final String ID = "sys.info";

    private final SystemCollector systemCollector;

    public SystemInfoCapability(SystemCollector systemCollector) {
        this.systemCollector = Objects.requireNonNull(systemCollector, "SystemCollector must not be null");
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "Host System Information";
    }

    @Override
    public String getDescription() {
        return "Provides operating system, architecture, kernel, CPU model, and memory summary";
    }

    @Override
    public CapabilityCategory getCategory() {
        return CapabilityCategory.SYSTEM;
    }

    @Override
    public Set<Permission> getRequiredPermissions() {
        return Set.of(Permission.READ_SYSTEM);
    }

    @Override
    public Class<Void> getInputType() {
        return Void.class;
    }

    @Override
    public Class<SystemInfoReport> getOutputType() {
        return SystemInfoReport.class;
    }

    @Override
    public CapabilityResult<SystemInfoReport> execute(ExecutionContext context, Void input) {
        Instant start = Instant.now();
        try {
            SystemInfoReport report = systemCollector.collectSystemInfo();
            return CapabilityResult.success(ID, report, start);
        } catch (Exception e) {
            return CapabilityResult.failure(ID, ErrorCode.INTERNAL_ERROR, "Failed to collect system info: " + e.getMessage(), start);
        }
    }
}
