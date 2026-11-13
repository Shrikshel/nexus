package com.nexus.adapters.telegram;

import com.nexus.capabilities.network.dto.NetworkReport;
import com.nexus.capabilities.network.tailscale.dto.TailscaleOperationResult;
import com.nexus.capabilities.network.tailscale.dto.TailscaleStatusReport;
import com.nexus.capabilities.process.dto.ProcessInfo;
import com.nexus.capabilities.process.dto.ProcessListReport;
import com.nexus.capabilities.resource.dto.CpuReport;
import com.nexus.capabilities.resource.dto.MemoryReport;
import com.nexus.capabilities.service.dto.ServiceListReport;
import com.nexus.capabilities.service.dto.ServiceUnitInfo;
import com.nexus.capabilities.storage.dto.DiskListReport;
import com.nexus.capabilities.storage.dto.DiskPartitionReport;
import com.nexus.capabilities.storage.dto.SmartReport;
import com.nexus.capabilities.storage.zfs.dto.*;
import com.nexus.capabilities.system.dto.PowerOperationResult;
import com.nexus.capabilities.system.dto.SystemInfoReport;
import com.nexus.capabilities.system.dto.SystemUptimeReport;
import com.nexus.core.capability.CapabilityMetadata;
import com.nexus.core.capability.CapabilityResult;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Formats capability execution results into concise, Telegram-optimized HTML messages.
 */
@Component
public class TelegramFormatter {

    public String formatResult(CapabilityResult<?> result) {
        if (!result.isSuccess()) {
            String code = result.error() != null && result.error().code() != null ? result.error().code().getCode() : "ERROR";
            String msg = result.error() != null ? result.error().message() : "Unknown error";
            return String.format("❌ <b>Execution Failed</b> [%s]\n\n<b>Error:</b> %s",
                    code, escapeHtml(msg));
        }

        Object payload = result.payload();
        if (payload == null) {
            return "✅ <i>Operation completed successfully (no content).</i>";
        }

        if (payload instanceof SystemInfoReport r) {
            return formatSystemInfo(r);
        } else if (payload instanceof SystemUptimeReport r) {
            return formatSystemUptime(r);
        } else if (payload instanceof CpuReport r) {
            return formatCpu(r);
        } else if (payload instanceof MemoryReport r) {
            return formatMemory(r);
        } else if (payload instanceof DiskListReport r) {
            return formatDiskList(r);
        } else if (payload instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof SmartReport) {
            @SuppressWarnings("unchecked")
            List<SmartReport> smartList = (List<SmartReport>) list;
            return formatSmartList(smartList);
        } else if (payload instanceof ProcessListReport r) {
            return formatProcessList(r);
        } else if (payload instanceof NetworkReport r) {
            return formatNetwork(r);
        } else if (payload instanceof ServiceListReport r) {
            return formatServices(r);
        } else if (payload instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof CapabilityMetadata) {
            @SuppressWarnings("unchecked")
            List<CapabilityMetadata> metaList = (List<CapabilityMetadata>) list;
            return formatCapabilitiesList(metaList);
        } else if (payload instanceof CapabilityMetadata m) {
            return formatCapabilityDetail(m);
        } else if (payload instanceof TailscaleStatusReport r) {
            return formatTailscaleStatus(r);
        } else if (payload instanceof TailscaleOperationResult r) {
            return formatTailscaleOperation(r);
        } else if (payload instanceof PowerOperationResult r) {
            return formatPowerOperation(r);
        } else if (payload instanceof ZfsPoolListReport r) {
            return formatZfsPoolList(r);
        } else if (payload instanceof ZfsPoolStatusReport r) {
            return formatZfsPoolStatus(r);
        } else if (payload instanceof ZfsDatasetListReport r) {
            return formatZfsDatasetList(r);
        } else if (payload instanceof ZfsSnapshotListReport r) {
            return formatZfsSnapshotList(r);
        } else if (payload instanceof ZfsDatasetDetailReport r) {
            return formatZfsDatasetDetail(r);
        }

        return "<code>" + escapeHtml(payload.toString()) + "</code>";
    }

    public String formatEvent(com.nexus.core.notification.HostEvent event) {
        String icon = switch (event.severity()) {
            case INFO -> "ℹ️";
            case WARNING -> "⚠️";
            case CRITICAL -> "🚨";
        };

        StringBuilder sb = new StringBuilder();
        sb.append(icon).append(" <b>[").append(event.severity().name()).append("] ").append(escapeHtml(event.title())).append("</b>\n\n");
        sb.append(escapeHtml(event.message())).append("\n");

        if (event.metadata() != null && !event.metadata().isEmpty()) {
            sb.append("\n<b>Details:</b>\n");
            event.metadata().forEach((k, v) ->
                    sb.append("• <i>").append(escapeHtml(k)).append(":</i> <code>").append(escapeHtml(String.valueOf(v))).append("</code>\n"));
        }
        return sb.toString();
    }

    private String formatSystemInfo(SystemInfoReport r) {
        return "🖥️ <b>Nexus Host Information</b>\n\n" +
                "<b>Hostname:</b> <code>" + escapeHtml(r.hostname()) + "</code>\n" +
                "<b>OS:</b> " + escapeHtml(r.osName()) + " (" + escapeHtml(r.osArch()) + ")\n" +
                "<b>Kernel:</b> " + escapeHtml(r.kernelVersion()) + "\n" +
                "<b>CPU Cores:</b> " + r.availableProcessors() + "\n" +
                "<b>Memory:</b> " + formatBytes(r.freePhysicalMemoryBytes()) + " free / " + formatBytes(r.totalPhysicalMemoryBytes()) + "\n" +
                "<b>Timezone:</b> " + escapeHtml(r.timezone()) + "\n" +
                "<b>Runtime:</b> " + escapeHtml(r.runtimeType());
    }

    private String formatSystemUptime(SystemUptimeReport r) {
        return "⏱️ <b>Nexus Host Uptime</b>\n\n" +
                "<b>Uptime:</b> <code>" + escapeHtml(r.hostUptimeFormatted()) + "</code>\n" +
                "<b>Boot Time:</b> " + com.nexus.platform.TimeUtils.formatTimestamp(r.bootTimestamp()) + "\n" +
                "<b>Agent Uptime:</b> <code>" + escapeHtml(r.agentUptimeFormatted()) + "</code>";
    }

    private String formatDiskList(DiskListReport r) {
        StringBuilder sb = new StringBuilder("💾 <b>Physical Storage</b>\n\n");
        if (r.partitions().isEmpty()) {
            sb.append("<i>No physical filesystems detected.</i>");
            return sb.toString();
        }

        for (DiskPartitionReport p : r.partitions()) {
            sb.append("• <b>").append(escapeHtml(p.mountPoint())).append("</b>");
            if (p.filesystemType() != null && !p.filesystemType().isBlank()) {
                sb.append(" (").append(escapeHtml(p.filesystemType())).append(")");
            }
            sb.append(": ")
              .append(formatBytes(p.usableBytes())).append(" free / ")
              .append(formatBytes(p.totalBytes()))
              .append(String.format(" (%.1f%% used)\n", p.usagePercentage()));
        }
        sb.append(String.format("\n<b>Aggregate Storage:</b> %.1f%% used (%s free / %s total)",
                r.aggregateUsagePercentage(), formatBytes(r.aggregateUsableBytes()), formatBytes(r.aggregateTotalBytes())));
        return sb.toString();
    }

    private String formatSmartList(List<SmartReport> list) {
        StringBuilder sb = new StringBuilder("🩺 <b>Drive SMART Health</b>\n\n");
        if (list.isEmpty()) {
            sb.append("<i>No SMART drives detected or accessible (elevated privileges may be required).</i>");
            return sb.toString();
        }

        for (SmartReport s : list) {
            String icon = "HEALTHY".equalsIgnoreCase(s.healthStatus()) ? "✅" : ("WARNING".equalsIgnoreCase(s.healthStatus()) ? "⚠️" : "🚨");
            sb.append(icon).append(" <b>").append(escapeHtml(s.devicePath())).append("</b> (").append(escapeHtml(s.modelName())).append(")\n")
              .append("   • Status: <b>").append(s.healthStatus()).append("</b> | Temp: ").append(s.temperatureCelsius()).append("°C\n")
              .append("   • Power-On: <code>").append(s.getFormattedPowerOnTime()).append("</code>\n")
              .append("   • Writes (TBW): <code>").append(s.getFormattedTbw()).append("</code>\n")
              .append("   • Unsafe Shutdowns: <code>").append(s.getFormattedShutdowns()).append("</code>\n");

            if (s.reallocatedSectors() > 0) {
                sb.append("   • Reallocated: <code>").append(s.reallocatedSectors()).append("</code>\n");
            }

            if (!s.warningsAndErrors().isEmpty()) {
                sb.append("   • ⚠️ <b>Warnings/Errors:</b> <i>").append(escapeHtml(String.join(", ", s.warningsAndErrors()))).append("</i>\n");
            } else {
                sb.append("   • Warnings/Errors: None\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String formatCapabilitiesList(List<CapabilityMetadata> list) {
        StringBuilder sb = new StringBuilder("📋 <b>Registered Capabilities (").append(list.size()).append(")</b>\n\n");
        for (CapabilityMetadata m : list) {
            sb.append("• <code>/").append(m.id().replace('.', '_')).append("</code> - ")
              .append(escapeHtml(m.displayName()))
              .append(m.privileged() ? " <i>[Elevated]</i>" : "")
              .append("\n");
        }
        return sb.toString();
    }

    private String formatCapabilityDetail(CapabilityMetadata m) {
        return "🔍 <b>Capability: " + escapeHtml(m.displayName()) + "</b>\n" +
                "<b>ID:</b> <code>" + escapeHtml(m.id()) + "</code>\n" +
                "<b>Category:</b> " + m.category() + "\n" +
                "<b>Privileged:</b> " + (m.privileged() ? "YES" : "NO") + "\n" +
                "<b>Description:</b> " + escapeHtml(m.description());
    }

    private String formatCpu(CpuReport r) {
        return "⚡ <b>CPU Telemetry</b>\n\n" +
                "<b>Logical Cores:</b> " + r.logicalCores() + "\n" +
                "<b>System Load:</b> " + String.format("%.2f (1m), %.2f (5m), %.2f (15m)",
                r.systemLoadAverage1m(), r.systemLoadAverage5m(), r.systemLoadAverage15m()) + "\n" +
                "<b>CPU Usage:</b> <code>" + String.format("%.1f%%", r.cpuUsagePercentage()) + "</code>";
    }

    private String formatMemory(MemoryReport r) {
        StringBuilder sb = new StringBuilder("🧠 <b>Host Memory</b>\n\n");
        sb.append("<b>RAM Usage:</b> <code>").append(String.format("%.1f%%", r.memoryUsagePercentage())).append("</code>\n")
          .append("<b>Free RAM:</b> ").append(formatBytes(r.freePhysicalMemoryBytes())).append(" / ").append(formatBytes(r.totalPhysicalMemoryBytes())).append("\n")
          .append("<b>Used RAM:</b> ").append(formatBytes(r.usedPhysicalMemoryBytes())).append("\n");

        if (r.totalSwapBytes() > 0) {
            long usedSwap = r.totalSwapBytes() - r.freeSwapBytes();
            double swapPct = (double) usedSwap * 100.0 / (double) r.totalSwapBytes();
            sb.append(String.format("<b>Swap Usage:</b> %.1f%% (%s free / %s total)\n",
                    swapPct, formatBytes(r.freeSwapBytes()), formatBytes(r.totalSwapBytes())));
        }
        return sb.toString();
    }

    private String formatProcessList(ProcessListReport r) {
        StringBuilder sb = new StringBuilder("⚙️ <b>Active Processes (Top 15)</b>\n\n");
        int count = 0;
        for (ProcessInfo p : r.processes()) {
            sb.append("• <code>").append(p.pid()).append("</code> [").append(escapeHtml(p.user())).append("] ")
              .append("<code>").append(escapeHtml(truncate(p.command(), 32))).append("</code> (")
              .append(p.cpuDuration()).append(")\n");
            if (++count >= 15) break;
        }
        sb.append("\n<b>Total System Processes:</b> ").append(r.totalProcessCount());
        return sb.toString();
    }

    private String formatNetwork(NetworkReport r) {
        StringBuilder sb = new StringBuilder("🌐 <b>Network Interfaces</b>\n\n");
        for (var n : r.interfaces()) {
            sb.append("• <b>").append(escapeHtml(n.name())).append("</b> [")
              .append(n.up() ? "UP" : "DOWN").append("]\n")
              .append("   MAC: <code>").append(n.macAddress()).append("</code> | MTU: ").append(n.mtu()).append("\n");
            for (String ip : n.ipAddresses()) {
                sb.append("   IP: <code>").append(ip).append("</code>\n");
            }
        }
        return sb.toString();
    }

    private String formatServices(ServiceListReport r) {
        StringBuilder sb = new StringBuilder("🚦 <b>System Services (Active/Loaded)</b>\n\n");
        int count = 0;
        for (ServiceUnitInfo s : r.services()) {
            sb.append("• <code>").append(escapeHtml(truncate(s.name(), 28))).append("</code> [")
              .append(s.activeState()).append("]\n");
            if (++count >= 15) {
                sb.append("<i>... and ").append(r.totalCount() - count).append(" more</i>\n");
                break;
            }
        }
        sb.append("\n<b>Total Services:</b> ").append(r.totalCount());
        return sb.toString();
    }

    private String formatTailscaleStatus(TailscaleStatusReport r) {
        if (!r.installed()) {
            return "🌐 <b>Tailscale VPN</b>\n\n" +
                    "⚠️ <b>Status:</b> Not Installed\n" +
                    "<i>Tailscale CLI was not found on this host system.</i>";
        }

        String icon = r.running() ? "🟢" : "🔴";
        StringBuilder sb = new StringBuilder("🌐 <b>Tailscale VPN Status</b>\n\n");
        sb.append("<b>Status:</b> ").append(icon).append(" ").append(r.running() ? "Running" : "Stopped")
          .append(" (<code>").append(escapeHtml(r.backendState())).append("</code>)\n");

        if (r.hostname() != null && !r.hostname().isBlank() && !"N/A".equals(r.hostname())) {
            sb.append("<b>Hostname:</b> <code>").append(escapeHtml(r.hostname())).append("</code>\n");
        }
        if (r.primaryIp() != null && !"N/A".equals(r.primaryIp())) {
            sb.append("<b>Tailscale IP:</b> <code>").append(r.primaryIp()).append("</code>\n");
        }
        if (r.dnsName() != null && !r.dnsName().isBlank() && !"N/A".equals(r.dnsName())) {
            sb.append("<b>MagicDNS:</b> <code>").append(escapeHtml(r.dnsName())).append("</code>\n");
        }
        sb.append("<b>Peers:</b> ").append(r.onlinePeers()).append(" online / ").append(r.totalPeers()).append(" total\n");

        if (r.exitNode() != null && !r.exitNode().isBlank() && !"None".equalsIgnoreCase(r.exitNode())) {
            sb.append("<b>Exit Node:</b> <code>").append(escapeHtml(r.exitNode())).append("</code>\n");
        }

        if (r.message() != null && !r.message().isBlank() && !"OK".equalsIgnoreCase(r.message())) {
            sb.append("\n<i>").append(escapeHtml(r.message())).append("</i>");
        }

        return sb.toString();
    }

    private String formatTailscaleOperation(TailscaleOperationResult r) {
        String icon = r.success() ? "✅" : "❌";
        return icon + " <b>Tailscale " + r.action() + "</b>\n\n" +
                "<b>Status:</b> " + (r.success() ? "Success" : "Failed (exit code " + r.exitCode() + ")") + "\n" +
                "<b>Message:</b> " + escapeHtml(r.message());
    }

    private String formatPowerOperation(PowerOperationResult r) {
        String icon = r.success() ? "⚡" : "❌";
        String title = "REBOOT".equalsIgnoreCase(r.action()) ? "System Host Reboot" : "System Host Poweroff";
        StringBuilder sb = new StringBuilder();
        sb.append(icon).append(" <b>").append(title).append("</b>\n\n");
        sb.append("<b>Status:</b> ").append(r.success() ? "Initiated" : "Failed").append("\n");
        if (r.delaySeconds() > 0) {
            sb.append("<b>Delay:</b> ").append(r.delaySeconds()).append(" seconds\n");
        }
        sb.append("<b>Message:</b> ").append(escapeHtml(r.message()));
        return sb.toString();
    }

    private String formatZfsPoolList(ZfsPoolListReport r) {
        StringBuilder sb = new StringBuilder("🗄️ <b>ZFS Storage Pools</b>\n\n");
        if (!r.installed()) {
            sb.append("⚠️ <i>ZFS utilities are not installed on this host.</i>");
            return sb.toString();
        }
        if (r.pools().isEmpty()) {
            sb.append("<i>").append(escapeHtml(r.message())).append("</i>");
            return sb.toString();
        }

        for (ZfsPoolInfo p : r.pools()) {
            String icon = "ONLINE".equalsIgnoreCase(p.healthStatus()) ? "🟢" : "⚠️";
            sb.append(icon).append(" <b>").append(escapeHtml(p.name())).append("</b> [").append(escapeHtml(p.healthStatus())).append("]\n")
              .append("   • Capacity: <code>").append(p.capacityPct()).append("%</code> (")
              .append(formatBytes(p.allocatedBytes())).append(" used / ").append(formatBytes(p.sizeBytes())).append(")\n")
              .append("   • Free: ").append(formatBytes(p.freeBytes()))
              .append(" | Frag: ").append(p.fragmentationPct()).append("% | Dedup: ").append(String.format("%.2fx", p.dedupRatio())).append("\n\n");
        }
        sb.append("<b>Total Pools:</b> ").append(r.totalPools());
        return sb.toString();
    }

    private String formatZfsPoolStatus(ZfsPoolStatusReport r) {
        StringBuilder sb = new StringBuilder("🗄️ <b>ZFS Pool Status: ")
                .append(escapeHtml(r.poolName())).append("</b>\n\n");
        if (!r.installed()) {
            sb.append("⚠️ <i>ZFS utilities are not installed on this host.</i>");
            return sb.toString();
        }

        String icon = "ONLINE".equalsIgnoreCase(r.state()) ? "🟢" : "⚠️";
        sb.append("<b>State:</b> ").append(icon).append(" <b>").append(escapeHtml(r.state())).append("</b>\n");
        if (r.statusDetail() != null && !"N/A".equals(r.statusDetail())) {
            sb.append("<b>Status:</b> ").append(escapeHtml(r.statusDetail())).append("\n");
        }
        sb.append("<b>Scan/Scrub:</b> <code>").append(escapeHtml(r.scanInfo())).append("</code>\n")
          .append("<b>Errors:</b> <code>").append(escapeHtml(r.errors())).append("</code>\n");

        if (r.configTree() != null && !r.configTree().isBlank() && !"N/A".equals(r.configTree())) {
            sb.append("\n<b>Layout:</b>\n<pre>").append(escapeHtml(r.configTree())).append("</pre>");
        }
        return sb.toString();
    }

    private String formatZfsDatasetList(ZfsDatasetListReport r) {
        StringBuilder sb = new StringBuilder("📁 <b>ZFS Datasets</b>\n\n");
        if (!r.installed()) {
            sb.append("⚠️ <i>ZFS utilities are not installed on this host.</i>");
            return sb.toString();
        }
        if (r.datasets().isEmpty()) {
            sb.append("<i>").append(escapeHtml(r.message())).append("</i>");
            return sb.toString();
        }

        for (ZfsDatasetInfo d : r.datasets()) {
            sb.append("• <b>").append(escapeHtml(d.name())).append("</b> (").append(escapeHtml(d.type())).append(")\n")
              .append("   Used: ").append(formatBytes(d.usedBytes()))
              .append(" | Avail: ").append(formatBytes(d.availableBytes()))
              .append(" | Mount: <code>").append(escapeHtml(d.mountpoint())).append("</code>\n");
        }
        sb.append("\n<b>Total Datasets:</b> ").append(r.totalDatasets());
        return sb.toString();
    }

    private String formatZfsSnapshotList(ZfsSnapshotListReport r) {
        StringBuilder sb = new StringBuilder("📸 <b>ZFS Snapshots</b>\n\n");
        if (!r.installed()) {
            sb.append("⚠️ <i>ZFS utilities are not installed on this host.</i>");
            return sb.toString();
        }
        if (r.snapshots().isEmpty()) {
            sb.append("<i>").append(escapeHtml(r.message())).append("</i>");
            return sb.toString();
        }

        int count = 0;
        for (ZfsSnapshotInfo s : r.snapshots()) {
            sb.append("• <code>").append(escapeHtml(s.name())).append("</code>\n")
              .append("   Used: ").append(formatBytes(s.usedBytes()))
              .append(" | Refer: ").append(formatBytes(s.referencedBytes())).append("\n");
            if (++count >= 20) {
                sb.append("<i>... and ").append(r.totalSnapshots() - count).append(" more</i>\n");
                break;
            }
        }
        sb.append("\n<b>Total Snapshots:</b> ").append(r.totalSnapshots());
        return sb.toString();
    }

    private String formatZfsDatasetDetail(ZfsDatasetDetailReport r) {
        StringBuilder sb = new StringBuilder("📊 <b>ZFS Dataset: ")
                .append(escapeHtml(r.datasetName())).append("</b>\n\n");
        if (!r.installed()) {
            sb.append("⚠️ <i>ZFS utilities are not installed on this host.</i>");
            return sb.toString();
        }
        if (r.properties().isEmpty()) {
            sb.append("<i>").append(escapeHtml(r.message())).append("</i>");
            return sb.toString();
        }

        sb.append("<b>Type:</b> ").append(escapeHtml(r.type())).append("\n\n");
        sb.append("<b>Key Properties:</b>\n");
        List<String> priorityProps = List.of("mountpoint", "compression", "used", "available", "referenced", "quota", "reservation", "recordsize", "readonly");
        for (String p : priorityProps) {
            if (r.properties().containsKey(p)) {
                sb.append("• <i>").append(escapeHtml(p)).append(":</i> <code>")
                  .append(escapeHtml(r.properties().get(p))).append("</code>\n");
            }
        }
        sb.append("\n<b>Total Properties:</b> ").append(r.properties().size());
        return sb.toString();
    }

    private String truncate(String str, int max) {
        if (str == null) return "";
        return str.length() <= max ? str : str.substring(0, max - 3) + "...";
    }

    private String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format("%.2f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
