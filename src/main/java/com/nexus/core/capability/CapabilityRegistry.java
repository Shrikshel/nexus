package com.nexus.core.capability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Central registry for all statically registered capabilities.
 * Discoverable via Spring dependency injection at startup.
 */
@Component
public class CapabilityRegistry {

    private static final Logger log = LoggerFactory.getLogger(CapabilityRegistry.class);

    private final Map<String, Capability<?, ?>> capabilities = new ConcurrentHashMap<>();

    public CapabilityRegistry(List<Capability<?, ?>> capabilityList) {
        if (capabilityList != null) {
            for (Capability<?, ?> cap : capabilityList) {
                register(cap);
            }
        }
        log.debug("Initialized CapabilityRegistry with {} capabilities", capabilities.size());
    }

    public void register(Capability<?, ?> capability) {
        Objects.requireNonNull(capability, "Capability must not be null");
        String id = capability.getId().toLowerCase().trim();
        if (capabilities.containsKey(id)) {
            log.warn("Overwriting capability registration for ID: {}", id);
        }
        capabilities.put(id, capability);
        log.debug("Registered capability: [{}] ({})", id, capability.getDisplayName());
    }

    @SuppressWarnings("unchecked")
    public <I, O> Optional<Capability<I, O>> getCapability(String id) {
        if (id == null) {
            return Optional.empty();
        }
        Capability<?, ?> cap = capabilities.get(id.toLowerCase().trim());
        if (cap == null) {
            return Optional.empty();
        }
        return Optional.of((Capability<I, O>) cap);
    }

    public boolean hasCapability(String id) {
        return id != null && capabilities.containsKey(id.toLowerCase().trim());
    }

    public Collection<Capability<?, ?>> getAll() {
        return Collections.unmodifiableCollection(capabilities.values());
    }

    public List<CapabilityMetadata> getAllMetadata() {
        return capabilities.values().stream()
                .map(CapabilityMetadata::from)
                .sorted(Comparator.comparing(CapabilityMetadata::id))
                .collect(Collectors.toList());
    }

    public List<CapabilityMetadata> getByCategory(CapabilityCategory category) {
        return capabilities.values().stream()
                .filter(cap -> cap.getCategory() == category)
                .map(CapabilityMetadata::from)
                .sorted(Comparator.comparing(CapabilityMetadata::id))
                .collect(Collectors.toList());
    }
}
