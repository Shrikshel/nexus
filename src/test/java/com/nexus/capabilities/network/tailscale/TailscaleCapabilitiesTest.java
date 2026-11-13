package com.nexus.capabilities.network.tailscale;

import com.nexus.capabilities.network.tailscale.dto.TailscaleOperationRequest;
import com.nexus.capabilities.network.tailscale.dto.TailscaleOperationResult;
import com.nexus.capabilities.network.tailscale.dto.TailscaleStatusReport;
import com.nexus.core.capability.CapabilityResult;
import com.nexus.core.capability.ExecutionContext;
import com.nexus.core.capability.InvocationSource;
import com.nexus.core.security.Permission;
import com.nexus.core.security.Role;
import com.nexus.core.security.Subject;
import com.nexus.core.security.privilege.PrivilegeRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TailscaleCapabilitiesTest {

    private PrivilegeRunner privilegeRunner;
    private ExecutionContext context;

    @BeforeEach
    void setup() {
        privilegeRunner = mock(PrivilegeRunner.class);
        Subject subject = Subject.of("admin-1", "admin", Role.ADMIN);
        context = ExecutionContext.create(InvocationSource.CLI, subject, Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("TailscaleStatusCapability parses JSON report correctly")
    void testParseStatusJson() throws Exception {
        TailscaleStatusCapability capability = new TailscaleStatusCapability();
        assertThat(capability.getId()).isEqualTo("net.tailscale.status");
        assertThat(capability.getRequiredPermissions()).contains(Permission.READ_NETWORK);
        assertThat(capability.isPrivileged()).isFalse();

        String sampleJson = """
        {
            "BackendState": "Running",
            "TailscaleIPs": ["100.80.90.100", "fd7a:115c:a1e0::1"],
            "Self": {
                "HostName": "nexus-host",
                "DNSName": "nexus-host.tailnet.ts.net.",
                "ExitNode": false
            },
            "MagicDNSSuffix": "tailnet.ts.net",
            "Peer": {
                "peer-1": { "Online": true },
                "peer-2": { "Online": false },
                "peer-3": { "Online": true }
            }
        }
        """;

        TailscaleStatusReport report = capability.parseStatusJson(sampleJson);
        assertThat(report.installed()).isTrue();
        assertThat(report.running()).isTrue();
        assertThat(report.backendState()).isEqualTo("Running");
        assertThat(report.hostname()).isEqualTo("nexus-host");
        assertThat(report.primaryIp()).isEqualTo("100.80.90.100");
        assertThat(report.tailscaleIPs()).containsExactly("100.80.90.100", "fd7a:115c:a1e0::1");
        assertThat(report.dnsName()).isEqualTo("nexus-host.tailnet.ts.net");
        assertThat(report.totalPeers()).isEqualTo(3);
        assertThat(report.onlinePeers()).isEqualTo(2);
        assertThat(report.exitNode()).isEqualTo("None");
    }

    @Test
    @DisplayName("TailscaleUpCapability invokes PrivilegeRunner with expected flags")
    void testTailscaleUpExecution() {
        TailscaleUpCapability capability = new TailscaleUpCapability(privilegeRunner);
        assertThat(capability.getId()).isEqualTo("net.tailscale.up");
        assertThat(capability.getRequiredPermissions()).contains(Permission.WRITE_NETWORK);
        assertThat(capability.isPrivileged()).isTrue();

        when(privilegeRunner.executeWhitelisted(anyString(), anyList(), anyLong()))
                .thenReturn(new PrivilegeRunner.ExecutionOutput(0, "Success", false));

        TailscaleOperationRequest request = new TailscaleOperationRequest(
                true,   // acceptRoutes
                true,   // ssh
                "100.64.0.1", // exitNode
                null,
                "node-alpha", // hostname
                false   // reset
        );

        CapabilityResult<TailscaleOperationResult> result = capability.execute(context, request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.payload().action()).isEqualTo("UP");
        assertThat(result.payload().success()).isTrue();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> argsCaptor = ArgumentCaptor.forClass(List.class);
        verify(privilegeRunner).executeWhitelisted(anyString(), argsCaptor.capture(), eq(30L));

        List<String> capturedArgs = argsCaptor.getValue();
        assertThat(capturedArgs).contains("up", "--accept-routes", "--ssh", "--exit-node=100.64.0.1", "--hostname=node-alpha");
    }

    @Test
    @DisplayName("TailscaleDownCapability invokes PrivilegeRunner for teardown")
    void testTailscaleDownExecution() {
        TailscaleDownCapability capability = new TailscaleDownCapability(privilegeRunner);
        assertThat(capability.getId()).isEqualTo("net.tailscale.down");
        assertThat(capability.getRequiredPermissions()).contains(Permission.WRITE_NETWORK);
        assertThat(capability.isPrivileged()).isTrue();

        when(privilegeRunner.executeWhitelisted(anyString(), anyList(), anyLong()))
                .thenReturn(new PrivilegeRunner.ExecutionOutput(0, "Tailscale disconnected", false));

        CapabilityResult<TailscaleOperationResult> result = capability.execute(context, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.payload().action()).isEqualTo("DOWN");
        assertThat(result.payload().success()).isTrue();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> argsCaptor = ArgumentCaptor.forClass(List.class);
        verify(privilegeRunner).executeWhitelisted(anyString(), argsCaptor.capture(), eq(20L));

        List<String> capturedArgs = argsCaptor.getValue();
        assertThat(capturedArgs).containsExactly("down");
    }
}
