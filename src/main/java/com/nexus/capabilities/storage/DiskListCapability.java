package com.nexus.capabilities.storage;

import com.nexus.capabilities.storage.dto.DiskListReport;
import com.nexus.core.capability.*;
import com.nexus.core.security.Permission;
import com.nexus.platform.StorageCollector;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Capability providing filesystem and mount partition disk space usage.
 */
@Component
public class DiskListCapability implements Capability<Void, DiskListReport> {

    public static final String ID = "disk.list";

    private final StorageCollector storageCollector;

    public DiskListCapability(StorageCollector storageCollector) {
        this.storageCollector = Objects.requireNonNull(storageCollector, "StorageCollector must not be null");
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "Filesystem Disk Utilization";
    }

    @Override
    public String getDescription() {
        return "Lists mounted filesystems, mount points, total bytes, usable bytes, and capacity percentage";
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
    public Class<DiskListReport> getOutputType() {
        return DiskListReport.class;
    }

    @Override
    public CapabilityResult<DiskListReport> execute(ExecutionContext context, Void input) {
        Instant start = Instant.now();
        try {
            DiskListReport report = storageCollector.collectDiskList();
            return CapabilityResult.success(ID, report, start);
        } catch (Exception e) {
            return CapabilityResult.failure(ID, ErrorCode.INTERNAL_ERROR, "Failed to collect disk list: " + e.getMessage(), start);
        }
    }
}
