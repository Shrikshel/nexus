package com.nexus.core.capability;

import com.nexus.core.security.Permission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityRegistryTest {

    private static class DummyCapability implements Capability<Void, String> {
        private final String id;
        private final CapabilityCategory category;

        public DummyCapability(String id, CapabilityCategory category) {
            this.id = id;
            this.category = category;
        }

        @Override public String getId() { return id; }
        @Override public String getDisplayName() { return "Dummy " + id; }
        @Override public String getDescription() { return "Description " + id; }
        @Override public CapabilityCategory getCategory() { return category; }
        @Override public Set<Permission> getRequiredPermissions() { return Set.of(); }
        @Override public Class<Void> getInputType() { return Void.class; }
        @Override public Class<String> getOutputType() { return String.class; }
        @Override
        public CapabilityResult<String> execute(ExecutionContext context, Void input) {
            return CapabilityResult.success(id, "OK", Instant.now());
        }
    }

    @Test
    @DisplayName("Registry discovers and stores capabilities case-insensitively")
    void shouldRegisterAndRetrieveCapabilities() {
        Capability<?, ?> cap1 = new DummyCapability("test.alpha", CapabilityCategory.SYSTEM);
        Capability<?, ?> cap2 = new DummyCapability("test.beta", CapabilityCategory.STORAGE);

        CapabilityRegistry registry = new CapabilityRegistry(List.of(cap1, cap2));

        assertThat(registry.hasCapability("test.alpha")).isTrue();
        assertThat(registry.hasCapability("TEST.ALPHA")).isTrue();
        assertThat(registry.hasCapability("non.existent")).isFalse();

        Optional<Capability<Void, String>> retrieved = registry.getCapability("TEST.alpha");
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getId()).isEqualTo("test.alpha");

        List<CapabilityMetadata> storageCaps = registry.getByCategory(CapabilityCategory.STORAGE);
        assertThat(storageCaps).hasSize(1);
        assertThat(storageCaps.get(0).id()).isEqualTo("test.beta");
    }
}
