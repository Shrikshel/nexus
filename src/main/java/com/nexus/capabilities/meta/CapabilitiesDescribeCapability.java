package com.nexus.capabilities.meta;

import com.nexus.core.capability.*;
import com.nexus.core.security.Permission;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * Capability providing detailed schema and specification for a specific capability ID.
 */
@Component
public class CapabilitiesDescribeCapability implements Capability<String, CapabilityMetadata> {

    public static final String ID = "cap.describe";

    private final ObjectProvider<CapabilityRegistry> registryProvider;

    public CapabilitiesDescribeCapability(ObjectProvider<CapabilityRegistry> registryProvider) {
        this.registryProvider = registryProvider;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "Describe Capability";
    }

    @Override
    public String getDescription() {
        return "Returns metadata, schema, and permission requirements for a given capability";
    }

    @Override
    public CapabilityCategory getCategory() {
        return CapabilityCategory.META;
    }

    @Override
    public Set<Permission> getRequiredPermissions() {
        return Set.of();
    }

    @Override
    public Class<String> getInputType() {
        return String.class;
    }

    @Override
    public Class<CapabilityMetadata> getOutputType() {
        return CapabilityMetadata.class;
    }

    @Override
    public CapabilityResult<CapabilityMetadata> execute(ExecutionContext context, String capabilityId) {
        Instant start = Instant.now();
        if (capabilityId == null || capabilityId.isBlank()) {
            return CapabilityResult.failure(ID, ErrorCode.INVALID_INPUT, "Capability ID must not be empty", start);
        }

        Optional<Capability<Object, Object>> cap = registryProvider.getObject().getCapability(capabilityId);
        if (cap.isEmpty()) {
            return CapabilityResult.failure(ID, ErrorCode.CAPABILITY_NOT_FOUND, "Capability not found: " + capabilityId, start);
        }

        CapabilityMetadata meta = CapabilityMetadata.from(cap.get());
        return CapabilityResult.success(ID, meta, start);
    }
}
