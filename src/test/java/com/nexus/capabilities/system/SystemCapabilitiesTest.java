package com.nexus.capabilities.system;

import com.nexus.capabilities.system.dto.SystemInfoReport;
import com.nexus.capabilities.system.dto.SystemUptimeReport;
import com.nexus.core.capability.CapabilityResult;
import com.nexus.core.capability.ExecutionContext;
import com.nexus.core.capability.InvocationSource;
import com.nexus.core.security.Subject;
import com.nexus.platform.GenericSystemCollector;
import com.nexus.platform.LinuxSystemCollector;
import com.nexus.platform.PlatformDetector;
import com.nexus.platform.SystemCollector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SystemCapabilitiesTest {

    @Test
    @DisplayName("SystemInfoCapability collects real platform telemetry")
    void shouldCollectSystemInfo() {
        PlatformDetector detector = new PlatformDetector();
        SystemCollector collector = detector.isLinux() ? new LinuxSystemCollector() : new GenericSystemCollector();
        SystemInfoCapability capability = new SystemInfoCapability(collector);

        ExecutionContext context = ExecutionContext.create(InvocationSource.CLI, Subject.localCli());
        CapabilityResult<SystemInfoReport> result = capability.execute(context, null);

        assertThat(result.isSuccess()).isTrue();
        SystemInfoReport report = result.payload();
        assertThat(report).isNotNull();
        assertThat(report.hostname()).isNotBlank();
        assertThat(report.osName()).isNotBlank();
        assertThat(report.availableProcessors()).isGreaterThan(0);
        assertThat(report.totalPhysicalMemoryBytes()).isGreaterThan(0);
    }

    @Test
    @DisplayName("SystemUptimeCapability collects uptime and formatted duration")
    void shouldCollectSystemUptime() {
        PlatformDetector detector = new PlatformDetector();
        SystemCollector collector = detector.isLinux() ? new LinuxSystemCollector() : new GenericSystemCollector();
        SystemUptimeCapability capability = new SystemUptimeCapability(collector);

        ExecutionContext context = ExecutionContext.create(InvocationSource.CLI, Subject.localCli());
        CapabilityResult<SystemUptimeReport> result = capability.execute(context, null);

        assertThat(result.isSuccess()).isTrue();
        SystemUptimeReport report = result.payload();
        assertThat(report).isNotNull();
        assertThat(report.hostUptimeSeconds()).isGreaterThanOrEqualTo(0);
        assertThat(report.hostUptimeFormatted()).isNotBlank();
        assertThat(report.bootTimestamp()).isNotNull();
    }
}
