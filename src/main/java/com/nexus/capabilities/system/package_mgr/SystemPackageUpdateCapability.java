package com.nexus.capabilities.system.package_mgr;

import com.nexus.capabilities.system.package_mgr.dto.PackageOperationRequest;
import com.nexus.capabilities.system.package_mgr.dto.PackageOperationResult;
import com.nexus.core.capability.*;
import com.nexus.core.security.Permission;
import com.nexus.platform.packagemanager.PackageManagerService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Capability to refresh and update host system package repositories.
 */
@Component
public class SystemPackageUpdateCapability implements Capability<PackageOperationRequest, PackageOperationResult> {

    public static final String ID = "sys.pkg.update";

    private final PackageManagerService packageManagerService;

    public SystemPackageUpdateCapability(PackageManagerService packageManagerService) {
        this.packageManagerService = Objects.requireNonNull(packageManagerService, "PackageManagerService must not be null");
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "Update System Packages";
    }

    @Override
    public String getDescription() {
        return "Refreshes system package repositories using the host's native package manager";
    }

    @Override
    public CapabilityCategory getCategory() {
        return CapabilityCategory.SYSTEM;
    }

    @Override
    public Set<Permission> getRequiredPermissions() {
        return Set.of(Permission.WRITE_PACKAGES);
    }

    @Override
    public Class<PackageOperationRequest> getInputType() {
        return PackageOperationRequest.class;
    }

    @Override
    public Class<PackageOperationResult> getOutputType() {
        return PackageOperationResult.class;
    }

    @Override
    public CapabilityResult<PackageOperationResult> execute(ExecutionContext context, PackageOperationRequest input) {
        Instant start = Instant.now();
        try {
            PackageOperationResult result = packageManagerService.update();
            if (result.success()) {
                return CapabilityResult.success(ID, result, start);
            } else {
                return CapabilityResult.failure(ID, ErrorCode.INTERNAL_ERROR, result.message(), start);
            }
        } catch (Exception e) {
            return CapabilityResult.failure(ID, ErrorCode.INTERNAL_ERROR, "Package update failed: " + e.getMessage(), start);
        }
    }
}
