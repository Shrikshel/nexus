package com.nexus.platform.service;

import com.nexus.core.security.privilege.PrivilegeRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DaemonServiceManagerTest {

    private PrivilegeRunner privilegeRunner;
    private LinuxSystemdDaemonService systemdService;
    private DaemonServiceManager manager;

    @BeforeEach
    void setUp() {
        privilegeRunner = Mockito.mock(PrivilegeRunner.class);
        systemdService = new LinuxSystemdDaemonService(privilegeRunner);
        manager = new DaemonServiceManager(List.of(systemdService));
    }

    @Test
    @DisplayName("Active provider is successfully resolved on Linux")
    void testActiveProvider() {
        if (System.getProperty("os.name", "").toLowerCase().contains("linux")) {
            assertThat(manager.isSupported()).isTrue();
            assertThat(manager.getActiveProvider()).isNotNull();
            assertThat(manager.getActiveProvider().getPlatformName()).isEqualTo("systemd");
        }
    }

    @Test
    @DisplayName("resolveBinaryPath returns valid executable or fallback")
    void testResolveBinaryPath() {
        String path = manager.resolveBinaryPath();
        assertThat(path).isNotBlank();
    }

    @Test
    @DisplayName("install, enable, disable, and uninstall service lifecycle in isolated directory")
    void testServiceLifecycle(@TempDir Path tempDir) throws Exception {
        LinuxSystemdDaemonService customService = new LinuxSystemdDaemonService(privilegeRunner) {
            @Override
            public Path resolveUnitPath(boolean systemScope) {
                return tempDir.resolve("nexus.service");
            }
        };

        DaemonServiceManager customManager = new DaemonServiceManager(List.of(customService));

        // 1. Not installed initially
        assertThat(customManager.isServiceInstalled(false)).isFalse();

        // 2. Install (with enable)
        ServiceOperationResult installResult = customManager.installService(false, true);
        assertThat(installResult.success()).isTrue();
        assertThat(installResult.action()).isEqualTo("INSTALL");
        assertThat(customManager.isServiceInstalled(false)).isTrue();
        assertThat(Files.exists(customService.resolveUnitPath(false))).isTrue();

        // Check content
        String content = Files.readString(customService.resolveUnitPath(false));
        assertThat(content).contains("ExecStart=");
        assertThat(content).contains("daemon start --foreground");
        assertThat(content).doesNotContain("ExecStop=");
        assertThat(content).contains("KillSignal=SIGTERM");
        assertThat(content).contains("Restart=always");

        // 3. Status
        ServiceStatusDetails status = customManager.getServiceStatus(false);
        assertThat(status.installed()).isTrue();
        assertThat(status.serviceName()).isEqualTo("nexus.service");

        // 4. Restart
        ServiceOperationResult restartResult = customManager.restartService(false);
        assertThat(restartResult.action()).isEqualTo("RESTART");

        // 5. Uninstall
        ServiceOperationResult uninstallResult = customManager.uninstallService(false);
        assertThat(uninstallResult.success()).isTrue();
        assertThat(customManager.isServiceInstalled(false)).isFalse();
        assertThat(Files.exists(customService.resolveUnitPath(false))).isFalse();

        // 5. System scope unit content includes User=root and Environment=HOME=/root
        ServiceOperationResult systemInstallResult = customManager.installService(true, false);
        assertThat(systemInstallResult.success()).isTrue();
        String systemContent = Files.readString(customService.resolveUnitPath(true));
        assertThat(systemContent).contains("User=root");
        assertThat(systemContent).contains("WorkingDirectory=/root");
        assertThat(systemContent).contains("Environment=HOME=/root");
    }
}
