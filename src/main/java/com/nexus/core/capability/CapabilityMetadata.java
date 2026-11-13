package com.nexus.core.capability;

import com.nexus.core.security.Permission;

import java.util.Set;

/**
 * Self-describing capability metadata container for discovery and documentation.
 */
public record CapabilityMetadata(
    String id,
    String displayName,
    String description,
    CapabilityCategory category,
    Set<Permission> requiredPermissions,
    boolean privileged,
    String inputType,
    String outputType
) {
    public static CapabilityMetadata from(Capability<?, ?> capability) {
        return new CapabilityMetadata(
                capability.getId(),
                capability.getDisplayName(),
                capability.getDescription(),
                capability.getCategory(),
                capability.getRequiredPermissions(),
                capability.isPrivileged(),
                capability.getInputType().getSimpleName(),
                capability.getOutputType().getSimpleName()
        );
    }
}
