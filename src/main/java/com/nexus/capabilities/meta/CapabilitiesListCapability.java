package com.nexus.capabilities.meta;

import com.nexus.core.capability.*;
import com.nexus.core.security.Permission;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Self-reflection capability that lists all registered capabilities in the agent.
 */
@Component
public class CapabilitiesListCapability implements Capability<Void, List<CapabilityMetadata>> {

    public static final String ID = "cap.list";

    private final ObjectProvider<CapabilityRegistry> registryProvider;

    public CapabilitiesListCapability(ObjectProvider<CapabilityRegistry> registryProvider) {
        this.registryProvider = registryProvider;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "List Registered Capabilities";
    }

    @Override
    public String getDescription() {
        return "Discovers and lists all statically compiled capabilities available in this Nexus binary";
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
    public Class<Void> getInputType() {
        return Void.class;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<List<CapabilityMetadata>> getOutputType() {
        return (Class<List<CapabilityMetadata>>) (Class<?>) List.class;
    }

    @Override
    public CapabilityResult<List<CapabilityMetadata>> execute(ExecutionContext context, Void input) {
        Instant start = Instant.now();
        List<CapabilityMetadata> list = registryProvider.getObject().getAllMetadata();
        return CapabilityResult.success(ID, list, start);
    }
}
