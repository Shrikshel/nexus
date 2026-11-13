package com.nexus.adapters.telegram;

import com.nexus.capabilities.network.tailscale.dto.TailscaleOperationResult;
import com.nexus.capabilities.network.tailscale.dto.TailscaleStatusReport;
import com.nexus.capabilities.storage.dto.DiskListReport;
import com.nexus.capabilities.storage.dto.DiskPartitionReport;
import com.nexus.capabilities.storage.dto.SmartReport;
import com.nexus.capabilities.storage.zfs.dto.*;
import com.nexus.capabilities.system.dto.PowerOperationResult;
import com.nexus.capabilities.system.dto.SystemInfoReport;
import com.nexus.capabilities.system.dto.SystemUptimeReport;
import com.nexus.core.capability.CapabilityResult;
import com.nexus.core.notification.HostEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramFormatterTest {

    private TelegramFormatter formatter;

    @BeforeEach
    void setup() {
        formatter = new TelegramFormatter();
    }

    @Test
    @DisplayName("Formats SystemInfoReport with HTML tags and escaped entities")
    void testFormatSystemInfo() {
        SystemInfoReport report = new SystemInfoReport(
                "srv-prod-01", "Linux", "6.5.0", "amd64", "6.5.0-generic",
                8, 34359738368L, 17179869184L, "OpenJDK", "17.0.2", "JVM", "Asia/Kolkata (IST, UTC+05:30)", Instant.now()
        );
        CapabilityResult<SystemInfoReport> result = CapabilityResult.success("sys.info", report, Instant.now());

        String html = formatter.formatResult(result);

        assertThat(html).contains("🖥️ <b>Nexus Host Information</b>");
        assertThat(html).contains("<code>srv-prod-01</code>");
        assertThat(html).contains("<b>CPU Cores:</b> 8");
        assertThat(html).contains("GB free");
    }

    @Test
    @DisplayName("Formats SystemUptimeReport into HTML")
    void testFormatSystemUptime() {
        SystemUptimeReport report = new SystemUptimeReport(
                86400, "1d 0s", 3600, "1h 0s", Instant.now().minusSeconds(86400), Instant.now().minusSeconds(3600)
        );
        CapabilityResult<SystemUptimeReport> result = CapabilityResult.success("sys.uptime", report, Instant.now());

        String html = formatter.formatResult(result);

        assertThat(html).contains("⏱️ <b>Nexus Host Uptime</b>");
        assertThat(html).contains("<code>1d 0s</code>");
    }

    @Test
    @DisplayName("Formats Drive SMART Health with TBW, Power-On Time, and Unsafe Shutdowns")
    void testFormatSmartReport() {
        SmartReport drive = new SmartReport(
                "/dev/nvme0", "Samsung 980 PRO", "S123", "v1.0", "NVMe",
                true, 38, 4200, 0, 15000000000000L, 3, 120,
                List.of(), List.of(), "HEALTHY", Instant.now()
        );
        CapabilityResult<List<SmartReport>> result = CapabilityResult.success("disk.smart", List.of(drive), Instant.now());

        String html = formatter.formatResult(result);

        assertThat(html).contains("🩺 <b>Drive SMART Health</b>");
        assertThat(html).contains("<b>/dev/nvme0</b>");
        assertThat(html).contains("Power-On: <code>4,200 hrs");
        assertThat(html).contains("Writes (TBW): <code>13.64 TBW</code>");
        assertThat(html).contains("Unsafe Shutdowns: <code>3 (Cycles: 120)</code>");
        assertThat(html).contains("Warnings/Errors: None");
    }

    @Test
    @DisplayName("Formats Physical Storage with clean mount points")
    void testFormatDiskList() {
        DiskPartitionReport root = new DiskPartitionReport("/", "/dev/nvme0n1p2", "EXT4",
                500000000000L, 300000000000L, 200000000000L, 40.0);
        DiskListReport listReport = new DiskListReport(List.of(root), 500000000000L, 300000000000L, 40.0, Instant.now());
        CapabilityResult<DiskListReport> result = CapabilityResult.success("disk.list", listReport, Instant.now());

        String html = formatter.formatResult(result);

        assertThat(html).contains("💾 <b>Physical Storage</b>");
        assertThat(html).contains("• <b>/</b> (EXT4)");
        assertThat(html).contains("Aggregate Storage");
    }

    @Test
    @DisplayName("Formats HostEvent with appropriate severity icons")
    void testFormatHostEvent() {
        HostEvent event = HostEvent.critical("DISK_DEGRADED", "Drive Failure Impending",
                "Disk /dev/sda has 450 reallocated sectors", Map.of("disk", "/dev/sda"));

        String html = formatter.formatEvent(event);

        assertThat(html).contains("🚨 <b>[CRITICAL] Drive Failure Impending</b>");
        assertThat(html).contains("Disk /dev/sda has 450 reallocated sectors");
    }

    @Test
    @DisplayName("Formats TailscaleStatusReport into Telegram HTML")
    void testFormatTailscaleStatus() {
        TailscaleStatusReport report = new TailscaleStatusReport(
                true, true, "Running",
                List.of("100.101.102.103"), "srv-nexus", "srv-nexus.example.ts.net", "example.ts.net",
                12, 5, "None", "Tailscale connected and operational", Instant.now()
        );
        CapabilityResult<TailscaleStatusReport> result = CapabilityResult.success("net.tailscale.status", report, Instant.now());

        String html = formatter.formatResult(result);

        assertThat(html).contains("🌐 <b>Tailscale VPN Status</b>");
        assertThat(html).contains("🟢 Running");
        assertThat(html).contains("<code>100.101.102.103</code>");
        assertThat(html).contains("5 online / 12 total");
    }

    @Test
    @DisplayName("Formats TailscaleOperationResult into Telegram HTML")
    void testFormatTailscaleOperation() {
        TailscaleOperationResult report = new TailscaleOperationResult(
                true, "UP", "Tailscale connected successfully", 0, Instant.now()
        );
        CapabilityResult<TailscaleOperationResult> result = CapabilityResult.success("net.tailscale.up", report, Instant.now());

        String html = formatter.formatResult(result);

        assertThat(html).contains("✅ <b>Tailscale UP</b>");
        assertThat(html).contains("Success");
        assertThat(html).contains("Tailscale connected successfully");
    }

    @Test
    @DisplayName("Formats PowerOperationResult into Telegram HTML")
    void testFormatPowerOperation() {
        PowerOperationResult report = new PowerOperationResult(
                true, "REBOOT", "Host reboot initiated successfully", 10, Instant.now()
        );
        CapabilityResult<PowerOperationResult> result = CapabilityResult.success("sys.reboot", report, Instant.now());

        String html = formatter.formatResult(result);

        assertThat(html).contains("⚡ <b>System Host Reboot</b>");
        assertThat(html).contains("Initiated");
        assertThat(html).contains("<b>Delay:</b> 10 seconds");
    }

    @Test
    @DisplayName("Formats ZfsPoolListReport into Telegram HTML")
    void testFormatZfsPoolList() {
        ZfsPoolInfo pool = new ZfsPoolInfo("tank", 2000000000000L, 500000000000L, 1500000000000L, 2, 25, 1.0, "ONLINE", "-");
        ZfsPoolListReport report = new ZfsPoolListReport(true, 1, List.of(pool), "Active pools", Instant.now());
        CapabilityResult<ZfsPoolListReport> result = CapabilityResult.success("zfs.pool.list", report, Instant.now());

        String html = formatter.formatResult(result);

        assertThat(html).contains("🗄️ <b>ZFS Storage Pools</b>");
        assertThat(html).contains("<b>tank</b> [ONLINE]");
        assertThat(html).contains("25%");
    }

    @Test
    @DisplayName("Formats ZfsPoolStatusReport into Telegram HTML")
    void testFormatZfsPoolStatus() {
        ZfsPoolStatusReport report = new ZfsPoolStatusReport(
                true, "tank", "ONLINE", "None", "scrub completed with 0 errors", "mirror-0 ONLINE", "No known data errors", "OK", Instant.now()
        );
        CapabilityResult<ZfsPoolStatusReport> result = CapabilityResult.success("zfs.pool.status", report, Instant.now());

        String html = formatter.formatResult(result);

        assertThat(html).contains("🗄️ <b>ZFS Pool Status: tank</b>");
        assertThat(html).contains("ONLINE");
        assertThat(html).contains("scrub completed");
    }

    @Test
    @DisplayName("Formats ZfsDatasetListReport into Telegram HTML")
    void testFormatZfsDatasetList() {
        ZfsDatasetInfo d1 = new ZfsDatasetInfo("tank/data", "filesystem", 100000000000L, 1400000000000L, 100000000000L, "/tank/data");
        ZfsDatasetListReport report = new ZfsDatasetListReport(true, 1, List.of(d1), "OK", Instant.now());
        CapabilityResult<ZfsDatasetListReport> result = CapabilityResult.success("zfs.dataset.list", report, Instant.now());

        String html = formatter.formatResult(result);

        assertThat(html).contains("📁 <b>ZFS Datasets</b>");
        assertThat(html).contains("<b>tank/data</b>");
        assertThat(html).contains("<code>/tank/data</code>");
    }

    @Test
    @DisplayName("Formats ZfsDatasetDetailReport into Telegram HTML")
    void testFormatZfsDatasetDetail() {
        ZfsDatasetDetailReport report = new ZfsDatasetDetailReport(
                true, "tank/data", "filesystem", Map.of("compression", "zstd", "mountpoint", "/tank/data"), "OK", Instant.now()
        );
        CapabilityResult<ZfsDatasetDetailReport> result = CapabilityResult.success("zfs.dataset.get", report, Instant.now());

        String html = formatter.formatResult(result);

        assertThat(html).contains("📊 <b>ZFS Dataset: tank/data</b>");
        assertThat(html).contains("<i>compression:</i> <code>zstd</code>");
    }
}
