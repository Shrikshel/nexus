package com.nexus.capabilities.storage;

import com.nexus.capabilities.storage.dto.SmartReport;
import com.nexus.core.capability.*;
import com.nexus.core.notification.HostEvent;
import com.nexus.core.notification.NotificationDispatcher;
import com.nexus.core.security.Permission;
import com.nexus.platform.StorageCollector;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Capability providing physical drive SMART telemetry and proactive failure detection.
 * Requires elevated permissions or helper execution.
 */
@Component
public class DiskSmartCapability implements Capability<String, List<SmartReport>> {

    public static final String ID = "disk.smart";

    private final StorageCollector storageCollector;
    private final NotificationDispatcher notificationDispatcher;

    public DiskSmartCapability(
            StorageCollector storageCollector,
            NotificationDispatcher notificationDispatcher) {
        this.storageCollector = Objects.requireNonNull(storageCollector, "StorageCollector must not be null");
        this.notificationDispatcher = Objects.requireNonNull(notificationDispatcher, "NotificationDispatcher must not be null");
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "Drive SMART Health Telemetry";
    }

    @Override
    public String getDescription() {
        return "Reads drive health, temperature, reallocated sectors, and power-on hours via SMART";
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
    public boolean isPrivileged() {
        return true;
    }

    @Override
    public Class<String> getInputType() {
        return String.class;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<List<SmartReport>> getOutputType() {
        return (Class<List<SmartReport>>) (Class<?>) List.class;
    }

    @Override
    public CapabilityResult<List<SmartReport>> execute(ExecutionContext context, String targetDevice) {
        Instant start = Instant.now();
        try {
            List<SmartReport> reports = storageCollector.collectSmartReports(targetDevice);

            // Check for drive health degradation and publish proactive alerts
            for (SmartReport report : reports) {
                if ("FAILING".equalsIgnoreCase(report.healthStatus())) {
                    notificationDispatcher.dispatch(HostEvent.critical(
                            "DISK_SMART_FAILURE",
                            "Drive Health Critical: " + report.devicePath(),
                            String.format("Drive %s (%s) has FAILED SMART health assessment!",
                                    report.devicePath(), report.modelName()),
                            Map.of("device", report.devicePath(), "model", report.modelName())
                    ));
                } else if ("WARNING".equalsIgnoreCase(report.healthStatus())) {
                    notificationDispatcher.dispatch(HostEvent.warning(
                            "DISK_SMART_WARNING",
                            "Drive Degradation Warning: " + report.devicePath(),
                            String.format("Drive %s (%s) has %d reallocated sectors. Temperature: %d°C",
                                    report.devicePath(), report.modelName(), report.reallocatedSectors(), report.temperatureCelsius()),
                            Map.of("device", report.devicePath(), "reallocatedSectors", report.reallocatedSectors())
                    ));
                }
            }

            return CapabilityResult.success(ID, reports, start);
        } catch (Exception e) {
            return CapabilityResult.failure(ID, ErrorCode.INTERNAL_ERROR, "Failed to collect SMART data: " + e.getMessage(), start);
        }
    }
}
