package com.nexus.capabilities.system;

import com.nexus.capabilities.system.dto.SystemUptimeReport;
import com.nexus.core.capability.*;
import com.nexus.core.security.Permission;
import com.nexus.platform.SystemCollector;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Capability providing host and agent uptime telemetry.
 */
@Component
public class SystemUptimeCapability implements Capability<Void, SystemUptimeReport> {

    public static final String ID = "sys.uptime";

    private final SystemCollector systemCollector;

    public SystemUptimeCapability(SystemCollector systemCollector) {
        this.systemCollector = Objects.requireNonNull(systemCollector, "SystemCollector must not be null");
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "Host and Agent Uptime";
    }

    @Override
    public String getDescription() {
        return "Returns current host system uptime, agent process uptime, and boot timestamps";
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
    public Class<SystemUptimeReport> getOutputType() {
        return SystemUptimeReport.class;
    }

    @Override
    public CapabilityResult<SystemUptimeReport> execute(ExecutionContext context, Void input) {
        Instant start = Instant.now();
        try {
            SystemUptimeReport report = systemCollector.collectSystemUptime();
            return CapabilityResult.success(ID, report, start);
        } catch (Exception e) {
            return CapabilityResult.failure(ID, ErrorCode.INTERNAL_ERROR, "Failed to collect uptime: " + e.getMessage(), start);
        }
    }
}
