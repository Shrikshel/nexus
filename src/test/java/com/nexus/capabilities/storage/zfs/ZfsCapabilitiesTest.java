package com.nexus.capabilities.storage.zfs;

import com.nexus.capabilities.storage.zfs.dto.*;
import com.nexus.core.capability.CapabilityResult;
import com.nexus.core.capability.ExecutionContext;
import com.nexus.core.capability.InvocationSource;
import com.nexus.core.security.Permission;
import com.nexus.core.security.Role;
import com.nexus.core.security.Subject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ZfsCapabilitiesTest {

    private ExecutionContext context;

    @BeforeEach
    void setup() {
        Subject subject = Subject.of("op-1", "operator", Role.OPERATOR);
        context = ExecutionContext.create(InvocationSource.CLI, subject, Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("ZfsPoolListCapability parses tab-delimited zpool list output")
    void testParsePoolList() {
        ZfsPoolListCapability capability = new ZfsPoolListCapability();
        assertThat(capability.getId()).isEqualTo("zfs.pool.list");
        assertThat(capability.getRequiredPermissions()).contains(Permission.READ_STORAGE);

        String sampleOutput = """
        tank\t2000398934016\t500099733504\t1500299200512\t2%\t25%\t1.00x\tONLINE\t-
        backup\t4000797868032\t1000199467008\t3000598401024\t5%\t25%\t1.20x\tDEGRADED\t/mnt/alt
        """;

        List<ZfsPoolInfo> pools = capability.parsePoolListOutput(sampleOutput);
        assertThat(pools).hasSize(2);

        ZfsPoolInfo p1 = pools.get(0);
        assertThat(p1.name()).isEqualTo("tank");
        assertThat(p1.sizeBytes()).isEqualTo(2000398934016L);
        assertThat(p1.allocatedBytes()).isEqualTo(500099733504L);
        assertThat(p1.freeBytes()).isEqualTo(1500299200512L);
        assertThat(p1.fragmentationPct()).isEqualTo(2);
        assertThat(p1.capacityPct()).isEqualTo(25);
        assertThat(p1.dedupRatio()).isEqualTo(1.00);
        assertThat(p1.healthStatus()).isEqualTo("ONLINE");
        assertThat(p1.altroot()).isEqualTo("-");

        ZfsPoolInfo p2 = pools.get(1);
        assertThat(p2.name()).isEqualTo("backup");
        assertThat(p2.healthStatus()).isEqualTo("DEGRADED");
        assertThat(p2.altroot()).isEqualTo("/mnt/alt");
    }

    @Test
    @DisplayName("ZfsPoolStatusCapability parses zpool status fields")
    void testParsePoolStatus() {
        ZfsPoolStatusCapability capability = new ZfsPoolStatusCapability();
        assertThat(capability.getId()).isEqualTo("zfs.pool.status");
        assertThat(capability.getRequiredPermissions()).contains(Permission.READ_STORAGE);

        String sampleOutput = """
          pool: tank
         state: ONLINE
          scan: scrub repaired 0B in 01:23:45 with 0 errors on Sun Nov 10 02:45:00 2026
        config:

        \tNAME        STATE     READ WRITE CKSUM
        \ttank        ONLINE       0     0     0
        \t  mirror-0  ONLINE       0     0     0
        \t    sda     ONLINE       0     0     0
        \t    sdb     ONLINE       0     0     0

        errors: No known data errors
        """;

        ZfsPoolStatusReport report = capability.parsePoolStatusOutput(sampleOutput);
        assertThat(report.poolName()).isEqualTo("tank");
        assertThat(report.state()).isEqualTo("ONLINE");
        assertThat(report.scanInfo()).contains("scrub repaired 0B");
        assertThat(report.errors()).isEqualTo("No known data errors");
        assertThat(report.configTree()).contains("mirror-0");
    }

    @Test
    @DisplayName("ZfsDatasetListCapability parses zfs list datasets output")
    void testParseDatasetList() {
        ZfsDatasetListCapability capability = new ZfsDatasetListCapability();
        assertThat(capability.getId()).isEqualTo("zfs.dataset.list");
        assertThat(capability.getRequiredPermissions()).contains(Permission.READ_STORAGE);

        String sampleOutput = """
        tank\tfilesystem\t500099733504\t1500299200512\t98304\t/tank
        tank/data\tfilesystem\t250049866752\t1500299200512\t250049866752\t/tank/data
        tank/vm-disk1\tvolume\t107374182400\t1500299200512\t107374182400\t-
        """;

        List<ZfsDatasetInfo> datasets = capability.parseDatasetListOutput(sampleOutput);
        assertThat(datasets).hasSize(3);

        ZfsDatasetInfo d1 = datasets.get(0);
        assertThat(d1.name()).isEqualTo("tank");
        assertThat(d1.type()).isEqualTo("filesystem");
        assertThat(d1.mountpoint()).isEqualTo("/tank");

        ZfsDatasetInfo d3 = datasets.get(2);
        assertThat(d3.name()).isEqualTo("tank/vm-disk1");
        assertThat(d3.type()).isEqualTo("volume");
        assertThat(d3.mountpoint()).isEqualTo("-");
    }

    @Test
    @DisplayName("ZfsSnapshotListCapability parses zfs list snapshots output")
    void testParseSnapshotList() {
        ZfsSnapshotListCapability capability = new ZfsSnapshotListCapability();
        assertThat(capability.getId()).isEqualTo("zfs.snapshot.list");
        assertThat(capability.getRequiredPermissions()).contains(Permission.READ_STORAGE);

        String sampleOutput = """
        tank/data@snap-daily-1\t104857600\t250049866752\tSun Nov 10 00:00 2026
        tank/data@snap-daily-2\t52428800\t250049866752\tMon Nov 11 00:00 2026
        """;

        List<ZfsSnapshotInfo> list = capability.parseSnapshotListOutput(sampleOutput);
        assertThat(list).hasSize(2);

        ZfsSnapshotInfo s1 = list.get(0);
        assertThat(s1.name()).isEqualTo("tank/data@snap-daily-1");
        assertThat(s1.dataset()).isEqualTo("tank/data");
        assertThat(s1.snapshotTag()).isEqualTo("snap-daily-1");
        assertThat(s1.usedBytes()).isEqualTo(104857600L);
        assertThat(s1.referencedBytes()).isEqualTo(250049866752L);
        assertThat(s1.creationTime()).contains("Nov 10");
    }

    @Test
    @DisplayName("ZfsDatasetDetailCapability parses zfs get property values")
    void testParseDatasetProperties() {
        ZfsDatasetDetailCapability capability = new ZfsDatasetDetailCapability();
        assertThat(capability.getId()).isEqualTo("zfs.dataset.get");
        assertThat(capability.getRequiredPermissions()).contains(Permission.READ_STORAGE);

        String sampleOutput = """
        type\tfilesystem
        creation\tSun Oct 20 12:00 2026
        used\t120G
        available\t1.4T
        referenced\t120G
        compressratio\t1.35x
        mounted\tyes
        mountpoint\t/tank/data
        compression\tzstd
        """;

        Map<String, String> props = capability.parseDatasetProperties(sampleOutput);
        assertThat(props).containsEntry("type", "filesystem");
        assertThat(props).containsEntry("compression", "zstd");
        assertThat(props).containsEntry("mountpoint", "/tank/data");
        assertThat(props).containsEntry("used", "120G");
        assertThat(props).containsEntry("mounted", "yes");
    }
}
