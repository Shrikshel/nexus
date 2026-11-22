package com.nexus.capabilities.system.audit;

import com.nexus.capabilities.storage.dto.DiskListReport;
import com.nexus.capabilities.storage.dto.DiskPartitionReport;
import com.nexus.capabilities.storage.dto.SmartReport;
import com.nexus.capabilities.system.audit.dto.AuditSeverity;
import com.nexus.capabilities.system.audit.dto.SystemAuditReport;
import com.nexus.core.capability.CapabilityResult;
import com.nexus.core.capability.ExecutionContext;
import com.nexus.core.security.privilege.PrivilegeRunner;
import com.nexus.platform.StorageCollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class SystemAuditCapabilityTest {

    private StorageCollector storageCollector;
    private PrivilegeRunner privilegeRunner;
    private SystemAuditCapability capability;

    @BeforeEach
    void setUp() {
        storageCollector = Mockito.mock(StorageCollector.class);
        privilegeRunner = Mockito.mock(PrivilegeRunner.class);
        capability = new SystemAuditCapability(storageCollector, privilegeRunner);
    }

    @Test
    @DisplayName("Metadata conforms to system capability standards")
    void testMetadata() {
        assertThat(capability.getId()).isEqualTo("sys.audit");
        assertThat(capability.getDisplayName()).contains("System Health & Security Audit");
        assertThat(capability.getCategory().name()).isEqualTo("SYSTEM");
        assertThat(capability.getInputType()).isEqualTo(Void.class);
        assertThat(capability.getOutputType()).isEqualTo(SystemAuditReport.class);
    }

    @Test
    @DisplayName("Audit report executes and aggregates check items correctly")
    void testAuditExecution() {
        DiskPartitionReport partition = new DiskPartitionReport(
                "/", "root", "ext4", 100_000_000_000L, 60_000_000_000L, 40_000_000_000L, 40.0
        );
        DiskListReport diskReport = new DiskListReport(List.of(partition), 100_000_000_000L, 60_000_000_000L, 40.0, Instant.now());
        when(storageCollector.collectDiskList()).thenReturn(diskReport);
        when(storageCollector.collectSmartReports(any())).thenReturn(List.of(
                new SmartReport("/dev/sda", "Samsung SSD 980", "S123", "v1.0", "NVME", true, 35, 1000L, 0L, List.of(), "HEALTHY", Instant.now())
        ));
        when(privilegeRunner.executeWhitelisted(anyString(), anyList(), anyLong()))
                .thenReturn(new PrivilegeRunner.ExecutionOutput(0, "0 loaded units listed", false));

        ExecutionContext context = Mockito.mock(ExecutionContext.class);
        CapabilityResult<SystemAuditReport> result = capability.execute(context, null);

        assertThat(result.isSuccess()).isTrue();
        SystemAuditReport report = result.payload();
        assertThat(report).isNotNull();
        assertThat(report.overallStatus()).isIn("HEALTHY", "WARNING", "CRITICAL");
        assertThat(report.checks()).isNotEmpty();
        assertThat(report.passedCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Audit flags critical severity when disk capacity exceeds 95%")
    void testCriticalDiskThreshold() {
        DiskPartitionReport partition = new DiskPartitionReport(
                "/", "root", "ext4", 100_000_000_000L, 3_000_000_000L, 97_000_000_000L, 97.0
        );
        DiskListReport diskReport = new DiskListReport(List.of(partition), 100_000_000_000L, 3_000_000_000L, 97.0, Instant.now());
        when(storageCollector.collectDiskList()).thenReturn(diskReport);
        when(storageCollector.collectSmartReports(any())).thenReturn(List.of());
        when(privilegeRunner.executeWhitelisted(anyString(), anyList(), anyLong()))
                .thenReturn(new PrivilegeRunner.ExecutionOutput(0, "0 loaded units listed", false));

        ExecutionContext context = Mockito.mock(ExecutionContext.class);
        CapabilityResult<SystemAuditReport> result = capability.execute(context, null);

        assertThat(result.isSuccess()).isTrue();
        SystemAuditReport report = result.payload();
        assertThat(report.overallStatus()).isEqualTo("CRITICAL");
        assertThat(report.criticalCount()).isGreaterThanOrEqualTo(1);
        assertThat(report.checks().stream().anyMatch(c -> c.severity() == AuditSeverity.CRITICAL && c.category().equals("Storage"))).isTrue();
    }
}
