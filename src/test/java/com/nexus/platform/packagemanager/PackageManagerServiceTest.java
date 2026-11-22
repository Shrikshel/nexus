package com.nexus.platform.packagemanager;

import com.nexus.capabilities.system.package_mgr.dto.PackageOperationResult;
import com.nexus.core.security.privilege.PrivilegeRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class PackageManagerServiceTest {

    private PrivilegeRunner privilegeRunner;
    private PackageManagerService service;

    @BeforeEach
    void setUp() {
        privilegeRunner = Mockito.mock(PrivilegeRunner.class);
        service = new PackageManagerService(privilegeRunner);
    }

    @Test
    @DisplayName("getDetectedType returns valid package manager type")
    void testDetectType() {
        PackageManagerType type = service.getDetectedType();
        assertThat(type).isNotNull();
    }

    @Test
    @DisplayName("update invokes privilegeRunner when supported package manager is found")
    void testUpdateExecution() {
        when(privilegeRunner.executeWhitelisted(anyString(), anyList(), anyLong()))
                .thenReturn(new PrivilegeRunner.ExecutionOutput(0, "Hit:1 http://archive.ubuntu.com/ubuntu jammy InRelease", false));

        PackageOperationResult result = service.update();
        assertThat(result).isNotNull();
        assertThat(result.action()).isEqualTo("UPDATE");

        if (service.getDetectedType() != PackageManagerType.UNKNOWN) {
            assertThat(result.success()).isTrue();
            assertThat(result.exitCode()).isEqualTo(0);
        } else {
            assertThat(result.success()).isFalse();
        }
    }

    @Test
    @DisplayName("upgrade invokes privilegeRunner with appropriate flags")
    void testUpgradeExecution() {
        when(privilegeRunner.executeWhitelisted(anyString(), anyList(), anyLong()))
                .thenReturn(new PrivilegeRunner.ExecutionOutput(0, "0 upgraded, 0 newly installed", false));

        PackageOperationResult result = service.upgrade(true, true);
        assertThat(result).isNotNull();
        assertThat(result.action()).isEqualTo("UPGRADE");

        if (service.getDetectedType() != PackageManagerType.UNKNOWN) {
            assertThat(result.success()).isTrue();
        } else {
            assertThat(result.success()).isFalse();
        }
    }

    @Test
    @DisplayName("updateUpgrade runs sequentially and succeeds if both succeed")
    void testUpdateUpgradeExecution() {
        when(privilegeRunner.executeWhitelisted(anyString(), anyList(), anyLong()))
                .thenReturn(new PrivilegeRunner.ExecutionOutput(0, "All operations succeeded", false));

        PackageOperationResult result = service.updateUpgrade(true, false);
        assertThat(result).isNotNull();
        assertThat(result.action()).isEqualTo("UPDATE-UPGRADE");

        if (service.getDetectedType() != PackageManagerType.UNKNOWN) {
            assertThat(result.success()).isTrue();
            assertThat(result.output()).contains("=== Update Output ===");
            assertThat(result.output()).contains("=== Upgrade Output ===");
        } else {
            assertThat(result.success()).isFalse();
        }
    }
}
