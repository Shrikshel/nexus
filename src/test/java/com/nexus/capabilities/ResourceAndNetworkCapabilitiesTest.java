package com.nexus.capabilities;

import com.nexus.capabilities.network.NetworkInfoCapability;
import com.nexus.capabilities.network.dto.NetworkReport;
import com.nexus.capabilities.process.ProcessListCapability;
import com.nexus.capabilities.process.dto.ProcessListReport;
import com.nexus.capabilities.resource.CpuCapability;
import com.nexus.capabilities.resource.MemoryCapability;
import com.nexus.capabilities.resource.dto.CpuReport;
import com.nexus.capabilities.resource.dto.MemoryReport;
import com.nexus.capabilities.service.ServiceListCapability;
import com.nexus.capabilities.service.dto.ServiceListReport;
import com.nexus.core.capability.CapabilityResult;
import com.nexus.core.capability.ExecutionContext;
import com.nexus.core.capability.InvocationSource;
import com.nexus.core.security.Subject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceAndNetworkCapabilitiesTest {

    private final ExecutionContext context = ExecutionContext.create(InvocationSource.CLI, Subject.localCli());

    @Test
    @DisplayName("CpuCapability returns logical cores and load metrics")
    void testCpuCapability() {
        CpuCapability capability = new CpuCapability();
        CapabilityResult<CpuReport> result = capability.execute(context, null);

        assertThat(result.isSuccess()).isTrue();
        CpuReport report = result.payload();
        assertThat(report.logicalCores()).isGreaterThan(0);
        assertThat(report.timestamp()).isNotNull();
    }

    @Test
    @DisplayName("MemoryCapability returns RAM and swap utilization")
    void testMemoryCapability() {
        MemoryCapability capability = new MemoryCapability();
        CapabilityResult<MemoryReport> result = capability.execute(context, null);

        assertThat(result.isSuccess()).isTrue();
        MemoryReport report = result.payload();
        assertThat(report.totalPhysicalMemoryBytes()).isGreaterThan(0);
        assertThat(report.usedPhysicalMemoryBytes()).isGreaterThan(0);
    }

    @Test
    @DisplayName("ProcessListCapability returns active processes including current JVM")
    void testProcessListCapability() {
        ProcessListCapability capability = new ProcessListCapability();
        CapabilityResult<ProcessListReport> result = capability.execute(context, null);

        assertThat(result.isSuccess()).isTrue();
        ProcessListReport report = result.payload();
        assertThat(report.totalProcessCount()).isGreaterThan(0);
        assertThat(report.processes()).isNotEmpty();
    }

    @Test
    @DisplayName("NetworkInfoCapability returns network interfaces")
    void testNetworkInfoCapability() {
        NetworkInfoCapability capability = new NetworkInfoCapability();
        CapabilityResult<NetworkReport> result = capability.execute(context, null);

        assertThat(result.isSuccess()).isTrue();
        NetworkReport report = result.payload();
        assertThat(report.totalInterfaceCount()).isGreaterThan(0);
        assertThat(report.interfaces()).isNotEmpty();
    }

    @Test
    @DisplayName("ServiceListCapability executes without error")
    void testServiceListCapability() {
        ServiceListCapability capability = new ServiceListCapability();
        CapabilityResult<ServiceListReport> result = capability.execute(context, null);

        assertThat(result.isSuccess()).isTrue();
        ServiceListReport report = result.payload();
        assertThat(report.timestamp()).isNotNull();
    }
}
