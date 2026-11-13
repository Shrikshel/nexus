package com.nexus.capabilities.storage;

import com.nexus.capabilities.storage.dto.DiskListReport;
import com.nexus.capabilities.storage.dto.SmartReport;
import com.nexus.core.capability.CapabilityResult;
import com.nexus.core.capability.ExecutionContext;
import com.nexus.core.capability.InvocationSource;
import com.nexus.core.notification.NotificationDispatcher;
import com.nexus.core.security.Subject;
import com.nexus.platform.GenericStorageCollector;
import com.nexus.platform.SmartctlParser;
import com.nexus.platform.StorageCollector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StorageCapabilitiesTest {

    @Test
    @DisplayName("DiskListCapability returns mounted filesystem partitions")
    void testDiskListCapability() {
        GenericStorageCollector collector = new GenericStorageCollector(new SmartctlParser(), new com.nexus.core.security.privilege.PrivilegeRunner());
        DiskListCapability capability = new DiskListCapability(collector);

        ExecutionContext context = ExecutionContext.create(InvocationSource.CLI, Subject.localCli());
        CapabilityResult<DiskListReport> result = capability.execute(context, null);

        assertThat(result.isSuccess()).isTrue();
        DiskListReport report = result.payload();
        assertThat(report.partitions()).isNotEmpty();
        assertThat(report.aggregateTotalBytes()).isGreaterThan(0);
    }

    @Test
    @DisplayName("DiskSmartCapability publishes critical alert when drive is failing")
    void testDiskSmartAlertOnDegradation() {
        StorageCollector mockCollector = mock(StorageCollector.class);
        NotificationDispatcher mockDispatcher = mock(NotificationDispatcher.class);

        SmartReport failingDrive = new SmartReport(
                "/dev/sda", "Failing Seagate", "SN123", "v1.0", "ATA",
                false, 45, 12000, 150, List.of(), "FAILING", Instant.now()
        );
        when(mockCollector.collectSmartReports(any())).thenReturn(List.of(failingDrive));

        DiskSmartCapability capability = new DiskSmartCapability(mockCollector, mockDispatcher);
        ExecutionContext context = ExecutionContext.create(InvocationSource.CLI, Subject.localCli());

        CapabilityResult<List<SmartReport>> result = capability.execute(context, "/dev/sda");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.payload()).hasSize(1);
        assertThat(result.payload().get(0).healthStatus()).isEqualTo("FAILING");

        // Verify alert was published to NotificationDispatcher
        verify(mockDispatcher, times(1)).dispatch(argThat(event ->
                event.eventType().equals("DISK_SMART_FAILURE") && event.message().contains("FAILED")
        ));
    }
}
