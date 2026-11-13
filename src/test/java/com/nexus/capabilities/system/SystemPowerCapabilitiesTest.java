package com.nexus.capabilities.system;

import com.nexus.capabilities.system.dto.PowerOperationRequest;
import com.nexus.capabilities.system.dto.PowerOperationResult;
import com.nexus.core.capability.CapabilityResult;
import com.nexus.core.capability.ErrorCode;
import com.nexus.core.capability.ExecutionContext;
import com.nexus.core.capability.InvocationSource;
import com.nexus.core.security.Permission;
import com.nexus.core.security.Role;
import com.nexus.core.security.Subject;
import com.nexus.core.security.privilege.PrivilegeRunner;
import com.nexus.platform.PlatformDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SystemPowerCapabilitiesTest {

    private PrivilegeRunner privilegeRunner;
    private PlatformDetector platformDetector;
    private ExecutionContext context;

    @BeforeEach
    void setup() {
        privilegeRunner = mock(PrivilegeRunner.class);
        platformDetector = mock(PlatformDetector.class);
        Subject subject = Subject.of("admin-1", "admin", Role.ADMIN);
        context = ExecutionContext.create(InvocationSource.CLI, subject, Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("SystemRebootCapability rejects execution without explicit confirmation")
    void testRebootRequiresConfirmation() {
        SystemRebootCapability capability = new SystemRebootCapability(privilegeRunner, platformDetector);
        assertThat(capability.getId()).isEqualTo("sys.reboot");
        assertThat(capability.getRequiredPermissions()).contains(Permission.POWER_MANAGEMENT);
        assertThat(capability.isPrivileged()).isTrue();

        // Null request
        CapabilityResult<PowerOperationResult> result1 = capability.execute(context, null);
        assertThat(result1.isSuccess()).isFalse();
        assertThat(result1.error().code()).isEqualTo(ErrorCode.FORBIDDEN);

        // confirm=false request
        PowerOperationRequest unconfirmed = new PowerOperationRequest(false, 0, "Test");
        CapabilityResult<PowerOperationResult> result2 = capability.execute(context, unconfirmed);
        assertThat(result2.isSuccess()).isFalse();
        assertThat(result2.error().code()).isEqualTo(ErrorCode.FORBIDDEN);

        verify(privilegeRunner, never()).executeWhitelisted(anyString(), anyList(), anyLong());
    }

    @Test
    @DisplayName("SystemRebootCapability executes reboot when confirmed on Linux")
    void testRebootConfirmedLinux() {
        when(platformDetector.isWindows()).thenReturn(false);
        when(platformDetector.isMac()).thenReturn(false);
        when(privilegeRunner.executeWhitelisted(anyString(), anyList(), anyLong()))
                .thenReturn(new PrivilegeRunner.ExecutionOutput(0, "", false));

        SystemRebootCapability capability = new SystemRebootCapability(privilegeRunner, platformDetector);
        PowerOperationRequest request = new PowerOperationRequest(true, 5, "Scheduled kernel upgrade");

        CapabilityResult<PowerOperationResult> result = capability.execute(context, request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.payload().action()).isEqualTo("REBOOT");
        assertThat(result.payload().delaySeconds()).isEqualTo(5);
        assertThat(result.payload().success()).isTrue();

        verify(privilegeRunner).executeWhitelisted(anyString(), anyList(), eq(15L));
    }

    @Test
    @DisplayName("SystemPoweroffCapability rejects execution without explicit confirmation")
    void testPoweroffRequiresConfirmation() {
        SystemPoweroffCapability capability = new SystemPoweroffCapability(privilegeRunner, platformDetector);
        assertThat(capability.getId()).isEqualTo("sys.poweroff");
        assertThat(capability.getRequiredPermissions()).contains(Permission.POWER_MANAGEMENT);
        assertThat(capability.isPrivileged()).isTrue();

        // confirm=false request
        PowerOperationRequest unconfirmed = new PowerOperationRequest(false, 0, "Shutdown now");
        CapabilityResult<PowerOperationResult> result = capability.execute(context, unconfirmed);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.error().code()).isEqualTo(ErrorCode.FORBIDDEN);

        verify(privilegeRunner, never()).executeWhitelisted(anyString(), anyList(), anyLong());
    }

    @Test
    @DisplayName("SystemPoweroffCapability executes shutdown when confirmed on Windows")
    void testPoweroffConfirmedWindows() {
        when(platformDetector.isWindows()).thenReturn(true);
        when(platformDetector.isMac()).thenReturn(false);
        when(privilegeRunner.executeWhitelisted(anyString(), anyList(), anyLong()))
                .thenReturn(new PrivilegeRunner.ExecutionOutput(0, "", false));

        SystemPoweroffCapability capability = new SystemPoweroffCapability(privilegeRunner, platformDetector);
        PowerOperationRequest request = new PowerOperationRequest(true, 10, "Maintenance window");

        CapabilityResult<PowerOperationResult> result = capability.execute(context, request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.payload().action()).isEqualTo("POWEROFF");
        assertThat(result.payload().delaySeconds()).isEqualTo(10);
        assertThat(result.payload().success()).isTrue();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> argsCaptor = ArgumentCaptor.forClass(List.class);
        verify(privilegeRunner).executeWhitelisted(eq("C:\\Windows\\System32\\shutdown.exe"), argsCaptor.capture(), eq(15L));

        List<String> capturedArgs = argsCaptor.getValue();
        assertThat(capturedArgs).contains("/s", "/t", "10", "/c", "Maintenance window");
    }
}
