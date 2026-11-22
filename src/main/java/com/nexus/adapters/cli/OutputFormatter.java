package com.nexus.adapters.cli;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nexus.capabilities.network.dto.NetworkInterfaceInfo;
import com.nexus.capabilities.network.dto.NetworkReport;
import com.nexus.capabilities.network.tailscale.dto.TailscaleOperationResult;
import com.nexus.capabilities.network.tailscale.dto.TailscaleStatusReport;
import com.nexus.capabilities.network.telegram.dto.TelegramRecipientResult;
import com.nexus.capabilities.network.telegram.dto.TelegramSendResult;
import com.nexus.capabilities.process.dto.ProcessInfo;
import com.nexus.capabilities.process.dto.ProcessListReport;
import com.nexus.capabilities.resource.dto.CpuReport;
import com.nexus.capabilities.resource.dto.MemoryReport;
import com.nexus.capabilities.scheduler.timer.dto.*;
import com.nexus.capabilities.service.dto.ServiceListReport;
import com.nexus.capabilities.service.dto.ServiceUnitInfo;
import com.nexus.capabilities.storage.dto.DiskListReport;
import com.nexus.capabilities.storage.dto.DiskPartitionReport;
import com.nexus.capabilities.storage.dto.SmartReport;
import com.nexus.capabilities.storage.zfs.dto.*;
import com.nexus.capabilities.system.audit.dto.AuditCheckItem;
import com.nexus.capabilities.system.audit.dto.AuditSeverity;
import com.nexus.capabilities.system.audit.dto.SystemAuditReport;
import com.nexus.capabilities.system.dto.PowerOperationResult;
import com.nexus.capabilities.system.dto.SystemInfoReport;
import com.nexus.capabilities.system.dto.SystemUptimeReport;
import com.nexus.capabilities.system.package_mgr.dto.PackageOperationResult;
import com.nexus.capabilities.terminal.tmux.dto.*;
import com.nexus.core.capability.CapabilityMetadata;
import com.nexus.core.capability.CapabilityResult;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.util.List;

/**
 * Renders capability results into human-friendly ANSI or structured JSON using a PrintWriter.
 */
@Component
public class OutputFormatter {

    private final ObjectMapper objectMapper;

    public OutputFormatter() {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .setTimeZone(java.util.TimeZone.getTimeZone(com.nexus.platform.HostTimezoneResolver.getHostZoneId()))
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public <T> void render(CapabilityResult<T> result, boolean json, PrintWriter out, PrintWriter err) {
        if (json) {
            try {
                out.println(objectMapper.writeValueAsString(result));
                out.flush();
            } catch (Exception e) {
                err.println("{\"error\": \"JSON serialization failed: " + e.getMessage() + "\"}");
                err.flush();
            }
            return;
        }

        if (!result.isSuccess()) {
            err.println("\u001B[31m✖ Execution Failed\u001B[0m");
            if (result.error() != null) {
                err.println("  Code:    " + (result.error().code() != null ? result.error().code().getCode() : "UNKNOWN"));
                err.println("  Message: " + result.error().message());
                if (result.error().details() != null) {
                    err.println("  Details: " + result.error().details());
                }
                if (result.error().remediation() != null) {
                    err.println("  Action:  " + result.error().remediation());
                }
            }
            err.flush();
            return;
        }

        T payload = result.payload();
        if (payload instanceof SystemInfoReport report) {
            renderSystemInfo(report, out);
        } else if (payload instanceof SystemUptimeReport report) {
            renderSystemUptime(report, out);
        } else if (payload instanceof CpuReport report) {
            renderCpu(report, out);
        } else if (payload instanceof MemoryReport report) {
            renderMemory(report, out);
        } else if (payload instanceof DiskListReport report) {
            renderDiskList(report, out);
        } else if (payload instanceof ProcessListReport report) {
            renderProcessList(report, out);
        } else if (payload instanceof NetworkReport report) {
            renderNetwork(report, out);
        } else if (payload instanceof ServiceListReport report) {
            renderServices(report, out);
        } else if (payload instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof SmartReport) {
            @SuppressWarnings("unchecked")
            List<SmartReport> smartList = (List<SmartReport>) list;
            renderSmartList(smartList, out);
        } else if (payload instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof CapabilityMetadata) {
            @SuppressWarnings("unchecked")
            List<CapabilityMetadata> metaList = (List<CapabilityMetadata>) list;
            renderCapabilitiesList(metaList, out);
        } else if (payload instanceof CapabilityMetadata meta) {
            renderCapabilityDetail(meta, out);
        } else if (payload instanceof TailscaleStatusReport report) {
            renderTailscaleStatus(report, out);
        } else if (payload instanceof TailscaleOperationResult report) {
            renderTailscaleOperation(report, out);
        } else if (payload instanceof PowerOperationResult report) {
            renderPowerOperation(report, out);
        } else if (payload instanceof TmuxSessionListReport report) {
            renderTmuxSessionList(report, out);
        } else if (payload instanceof TmuxOperationResult report) {
            renderTmuxOperation(report, out);
        } else if (payload instanceof ZfsPoolListReport report) {
            renderZfsPoolList(report, out);
        } else if (payload instanceof ZfsPoolStatusReport report) {
            renderZfsPoolStatus(report, out);
        } else if (payload instanceof ZfsDatasetListReport report) {
            renderZfsDatasetList(report, out);
        } else if (payload instanceof ZfsSnapshotListReport report) {
            renderZfsSnapshotList(report, out);
        } else if (payload instanceof ZfsDatasetDetailReport report) {
            renderZfsDatasetDetail(report, out);
        } else if (payload instanceof TimerListReport report) {
            renderTimerList(report, out);
        } else if (payload instanceof TimerCreateResult report) {
            renderTimerCreate(report, out);
        } else if (payload instanceof TimerStatusResult report) {
            renderTimerStatus(report, out);
        } else if (payload instanceof TimerUpdateResult report) {
            renderTimerUpdate(report, out);
        } else if (payload instanceof TimerRunResult report) {
            renderTimerRun(report, out);
        } else if (payload instanceof TimerDeleteResult report) {
            renderTimerDelete(report, out);
        } else if (payload instanceof TelegramSendResult report) {
            renderTelegramSend(report, out);
        } else if (payload instanceof PackageOperationResult report) {
            renderPackageOperation(report, out);
        } else if (payload instanceof SystemAuditReport report) {
            renderSystemAudit(report, out);
        } else {
            try {
                out.println(objectMapper.writeValueAsString(payload));
            } catch (Exception e) {
                out.println(payload);
            }
        }
        out.flush();
    }

    private void renderSystemInfo(SystemInfoReport r, PrintWriter out) {
        out.println("\u001B[1;36m=== Nexus Host Information ===\u001B[0m");
        out.printf("  %-20s: \u001B[1m%s\u001B[0m%n", "Hostname", r.hostname());
        out.printf("  %-20s: %s (%s, %s)%n", "OS / Platform", r.osName(), r.osVersion(), r.osArch());
        out.printf("  %-20s: %s%n", "Kernel Version", r.kernelVersion());
        out.printf("  %-20s: %d logical cores%n", "CPU Processors", r.availableProcessors());
        out.printf("  %-20s: %s / %s (%.1f%% free)%n", "Memory (Free/Total)",
                formatBytes(r.freePhysicalMemoryBytes()),
                formatBytes(r.totalPhysicalMemoryBytes()),
                r.totalPhysicalMemoryBytes() > 0 ? (r.freePhysicalMemoryBytes() * 100.0 / r.totalPhysicalMemoryBytes()) : 0.0);
        out.printf("  %-20s: %s (%s)%n", "Runtime", r.runtimeType(), r.javaVersion());
        out.printf("  %-20s: %s%n", "Timezone", r.timezone());
        out.printf("  %-20s: %s%n", "Report Timestamp", com.nexus.platform.TimeUtils.formatTimestamp(r.timestamp()));
    }

    private void renderSystemUptime(SystemUptimeReport r, PrintWriter out) {
        out.println("\u001B[1;36m=== Nexus Host Uptime ===\u001B[0m");
        out.printf("  %-20s: \u001B[32m%s\u001B[0m (%d seconds)%n", "Host Uptime", r.hostUptimeFormatted(), r.hostUptimeSeconds());
        out.printf("  %-20s: %s%n", "Boot Timestamp", com.nexus.platform.TimeUtils.formatTimestamp(r.bootTimestamp()));
        out.printf("  %-20s: \u001B[34m%s\u001B[0m (%d seconds)%n", "Agent Uptime", r.agentUptimeFormatted(), r.agentUptimeSeconds());
        out.printf("  %-20s: %s%n", "Agent Started At", com.nexus.platform.TimeUtils.formatTimestamp(r.agentStartTimestamp()));
    }

    private void renderCpu(CpuReport r, PrintWriter out) {
        out.println("\u001B[1;36m=== CPU Utilization & Load ===\u001B[0m");
        out.printf("  %-20s: %d logical cores%n", "Logical Cores", r.logicalCores());
        out.printf("  %-20s: %.2f (1m), %.2f (5m), %.2f (15m)%n", "System Load Avg",
                r.systemLoadAverage1m(), r.systemLoadAverage5m(), r.systemLoadAverage15m());
        out.printf("  %-20s: \u001B[1m%.1f%%\u001B[0m%n", "CPU Usage", r.cpuUsagePercentage());
    }

    private void renderMemory(MemoryReport r, PrintWriter out) {
        out.println("\u001B[1;36m=== Host Memory Utilization ===\u001B[0m");
        out.printf("  %-20s: %s%n", "Total RAM", formatBytes(r.totalPhysicalMemoryBytes()));
        out.printf("  %-20s: %s%n", "Used RAM", formatBytes(r.usedPhysicalMemoryBytes()));
        out.printf("  %-20s: %s%n", "Free RAM", formatBytes(r.freePhysicalMemoryBytes()));
        out.printf("  %-20s: \u001B[1m%.1f%%\u001B[0m%n", "RAM Usage", r.memoryUsagePercentage());
        if (r.totalSwapBytes() > 0) {
            long usedSwap = r.totalSwapBytes() - r.freeSwapBytes();
            double swapPct = (double) usedSwap * 100.0 / (double) r.totalSwapBytes();
            out.printf("  %-20s: %s / %s (%.1f%%)%n", "Swap (Used/Total)",
                    formatBytes(usedSwap), formatBytes(r.totalSwapBytes()), swapPct);
        }
    }

    private void renderDiskList(DiskListReport r, PrintWriter out) {
        out.println("\u001B[1;36m=== Filesystem Storage ===\u001B[0m");
        out.printf("  \u001B[1m%-24s %-10s %10s %10s %10s %8s\u001B[0m%n", "FILESYSTEM", "TYPE", "TOTAL", "USED", "FREE", "USE%");
        out.println("  --------------------------------------------------------------------------------");
        for (DiskPartitionReport p : r.partitions()) {
            out.printf("  %-24s %-10s %10s %10s %10s %7.1f%%%n",
                    truncate(p.mountPoint(), 24),
                    truncate(p.filesystemType(), 10),
                    formatBytes(p.totalBytes()),
                    formatBytes(p.usedBytes()),
                    formatBytes(p.usableBytes()),
                    p.usagePercentage());
        }
        out.println("  --------------------------------------------------------------------------------");
        out.printf("  \u001B[1mAggregate:\u001B[0m Total: %s | Free: %s | Used: %.1f%%%n",
                formatBytes(r.aggregateTotalBytes()),
                formatBytes(r.aggregateUsableBytes()),
                r.aggregateUsagePercentage());
    }

    private void renderProcessList(ProcessListReport r, PrintWriter out) {
        out.println("\u001B[1;36m=== Running Processes ===\u001B[0m");
        out.printf("  \u001B[1m%-8s %-16s %-12s %s\u001B[0m%n", "PID", "USER", "CPU TIME", "COMMAND");
        out.println("  --------------------------------------------------------------------------");
        int count = 0;
        for (ProcessInfo p : r.processes()) {
            out.printf("  %-8d %-16s %-12s %s%n",
                    p.pid(),
                    truncate(p.user(), 16),
                    p.cpuDuration(),
                    truncate(p.command(), 50));
            if (++count >= 30) {
                out.printf("  ... and %d more processes%n", r.totalProcessCount() - count);
                break;
            }
        }
        out.printf("%n  Total Active Processes: \u001B[1m%d\u001B[0m%n", r.totalProcessCount());
    }

    private void renderNetwork(NetworkReport r, PrintWriter out) {
        out.println("\u001B[1;36m=== Network Interfaces ===\u001B[0m");
        for (NetworkInterfaceInfo n : r.interfaces()) {
            String status = n.up() ? "\u001B[32mUP\u001B[0m" : "\u001B[31mDOWN\u001B[0m";
            out.printf("  \u001B[1m%-12s\u001B[0m [%s] MAC: %s | MTU: %d%n", n.name(), status, n.macAddress(), n.mtu());
            for (String ip : n.ipAddresses()) {
                out.printf("    • IP: %s%n", ip);
            }
        }
        out.printf("%n  Total Interfaces: \u001B[1m%d\u001B[0m%n", r.totalInterfaceCount());
    }

    private void renderServices(ServiceListReport r, PrintWriter out) {
        out.println("\u001B[1;36m=== System Services ===\u001B[0m");
        if (r.services().isEmpty()) {
            out.println("  No services returned or service manager unavailable.");
            return;
        }

        out.printf("  \u001B[1m%-30s %-10s %-10s %s\u001B[0m%n", "UNIT", "LOAD", "ACTIVE", "DESCRIPTION");
        out.println("  --------------------------------------------------------------------------");
        int count = 0;
        for (ServiceUnitInfo s : r.services()) {
            out.printf("  %-30s %-10s %-10s %s%n",
                    truncate(s.name(), 30),
                    s.loadState(),
                    s.activeState(),
                    truncate(s.description(), 40));
            if (++count >= 30) {
                out.printf("  ... and %d more services%n", r.totalCount() - count);
                break;
            }
        }
        out.printf("%n  Total Services: \u001B[1m%d\u001B[0m%n", r.totalCount());
    }

    private void renderSmartList(List<SmartReport> list, PrintWriter out) {
        out.println("\u001B[1;36m=== Drive SMART Health Telemetry ===\u001B[0m");
        if (list.isEmpty()) {
            out.println("  No SMART-capable drives detected or access restricted.");
            return;
        }

        out.printf("  \u001B[1m%-12s %-20s %-10s %5s %18s %12s %16s %s\u001B[0m%n",
                "DEVICE", "MODEL", "STATUS", "TEMP", "POWER-ON TIME", "TBW", "UNSAFE SHUTDOWN", "DIAGNOSTICS");
        out.println("  -------------------------------------------------------------------------------------------------------------------------");
        for (SmartReport s : list) {
            String statusColor = switch (s.healthStatus().toUpperCase()) {
                case "HEALTHY" -> "\u001B[32mHEALTHY\u001B[0m";
                case "WARNING" -> "\u001B[33mWARNING\u001B[0m";
                default -> "\u001B[31mFAILING\u001B[0m";
            };

            String warnings = s.warningsAndErrors().isEmpty() ? "OK" : String.join(", ", s.warningsAndErrors());

            out.printf("  %-12s %-20s %-10s %4d°C %18s %12s %16s %s%n",
                    s.devicePath(),
                    truncate(s.modelName(), 20),
                    statusColor,
                    s.temperatureCelsius(),
                    s.getFormattedPowerOnTime(),
                    s.getFormattedTbw(),
                    s.getFormattedShutdowns(),
                    truncate(warnings, 35));
        }
    }

    private void renderCapabilitiesList(List<CapabilityMetadata> list, PrintWriter out) {
        out.println("\u001B[1;36m=== Registered Nexus Capabilities ===\u001B[0m");
        out.printf("  \u001B[1m%-16s %-12s %-6s %s\u001B[0m%n", "CAPABILITY ID", "CATEGORY", "PRIV", "DESCRIPTION");
        out.println("  --------------------------------------------------------------------------");
        for (CapabilityMetadata m : list) {
            out.printf("  %-16s %-12s %-6s %s%n",
                    m.id(),
                    m.category(),
                    m.privileged() ? "\u001B[33mYES\u001B[0m" : "NO",
                    m.description());
        }
        out.printf("%n  Total Capabilities: \u001B[1m%d\u001B[0m%n", list.size());
    }

    private void renderCapabilityDetail(CapabilityMetadata m, PrintWriter out) {
        out.println("\u001B[1;36m=== Capability Details ===\u001B[0m");
        out.printf("  %-16s: \u001B[1m%s\u001B[0m (%s)%n", "Capability ID", m.id(), m.displayName());
        out.printf("  %-16s: %s%n", "Category", m.category());
        out.printf("  %-16s: %s%n", "Privileged", m.privileged() ? "YES (Requires elevation)" : "NO");
        out.printf("  %-16s: %s%n", "Input Type", m.inputType());
        out.printf("  %-16s: %s%n", "Output Type", m.outputType());
        out.printf("  %-16s: %s%n", "Permissions", m.requiredPermissions().isEmpty() ? "None (Public)" : m.requiredPermissions());
        out.printf("  %-16s: %s%n", "Description", m.description());
    }

    private void renderTailscaleStatus(TailscaleStatusReport r, PrintWriter out) {
        out.println("\u001B[1;36m=== Tailscale VPN Status ===\u001B[0m");
        if (!r.installed()) {
            out.println("  Status: \u001B[31mNot Installed\u001B[0m (Tailscale CLI binary not found)");
            return;
        }
        String statusColor = r.running() ? "\u001B[32mRunning\u001B[0m" : "\u001B[33mStopped\u001B[0m";
        out.printf("  %-16s: %s (State: %s)%n", "Status", statusColor, r.backendState());
        out.printf("  %-16s: \u001B[1m%s\u001B[0m%n", "Hostname", r.hostname());
        out.printf("  %-16s: \u001B[1m%s\u001B[0m%n", "Tailscale IP", r.primaryIp());
        if (r.tailscaleIPs().size() > 1) {
            out.printf("  %-16s: %s%n", "All IPs", String.join(", ", r.tailscaleIPs()));
        }
        out.printf("  %-16s: %s%n", "MagicDNS", r.dnsName());
        out.printf("  %-16s: %d online / %d total%n", "Peers", r.onlinePeers(), r.totalPeers());
        out.printf("  %-16s: %s%n", "Exit Node", r.exitNode());
        if (r.message() != null && !r.message().isBlank() && !"OK".equalsIgnoreCase(r.message())) {
            out.printf("  %-16s: %s%n", "Message", r.message());
        }
    }

    private void renderTailscaleOperation(TailscaleOperationResult r, PrintWriter out) {
        String title = "=== Tailscale " + r.action() + " ===";
        out.println("\u001B[1;36m" + title + "\u001B[0m");
        String statusColor = r.success() ? "\u001B[32mSUCCESS\u001B[0m" : "\u001B[31mFAILED\u001B[0m";
        out.printf("  %-12s: %s (Exit Code: %d)%n", "Status", statusColor, r.exitCode());
        out.printf("  %-12s: %s%n", "Message", r.message());
    }

    private void renderPowerOperation(PowerOperationResult r, PrintWriter out) {
        String title = "REBOOT".equalsIgnoreCase(r.action()) ? "=== System Host Reboot ===" : "=== System Host Poweroff ===";
        out.println("\u001B[1;36m" + title + "\u001B[0m");
        String statusColor = r.success() ? "\u001B[32mINITIATED\u001B[0m" : "\u001B[31mFAILED\u001B[0m";
        out.printf("  %-12s: %s%n", "Status", statusColor);
        if (r.delaySeconds() > 0) {
            out.printf("  %-12s: %d seconds%n", "Delay", r.delaySeconds());
        }
        out.printf("  %-12s: %s%n", "Message", r.message());
    }

    private void renderTmuxSessionList(TmuxSessionListReport r, PrintWriter out) {
        out.println("\u001B[1;36m=== Active Tmux Sessions ===\u001B[0m");
        if (!r.installed()) {
            out.println("  Status: \u001B[31mNot Installed\u001B[0m (tmux binary not found)");
            return;
        }
        if (r.sessions().isEmpty()) {
            out.println("  No active tmux sessions found (" + r.message() + ")");
            return;
        }
        out.printf("  \u001B[1m%-24s %-10s %-12s %s\u001B[0m%n", "SESSION NAME", "WINDOWS", "STATUS", "WORKING DIRECTORY");
        out.println("  ------------------------------------------------------------------------");
        for (TmuxSessionInfo s : r.sessions()) {
            String status = s.attached() ? "\u001B[32mAttached\u001B[0m" : "\u001B[33mDetached\u001B[0m";
            out.printf("  %-24s %-10d %-21s %s%n",
                    truncate(s.name(), 24),
                    s.windows(),
                    status,
                    truncate(s.workingDirectory(), 40));
        }
        out.printf("%n  Total Sessions: \u001B[1m%d\u001B[0m%n", r.totalSessions());
    }

    private void renderTmuxOperation(TmuxOperationResult r, PrintWriter out) {
        out.println("\u001B[1;36m=== Tmux " + r.action() + " Session ===\u001B[0m");
        String status = r.success() ? "\u001B[32mSUCCESS\u001B[0m" : "\u001B[31mFAILED\u001B[0m";
        out.printf("  %-14s: %s%n", "Status", status);
        out.printf("  %-14s: \u001B[1m%s\u001B[0m%n", "Session", r.sessionName());
        out.printf("  %-14s: %s%n", "Message", r.message());
    }

    private void renderZfsPoolList(ZfsPoolListReport r, PrintWriter out) {
        out.println("\u001B[1;36m=== ZFS Storage Pools ===\u001B[0m");
        if (!r.installed()) {
            out.println("  Status: \u001B[31mNot Installed\u001B[0m (ZFS utilities not found)");
            return;
        }
        if (r.pools().isEmpty()) {
            out.println("  " + r.message());
            return;
        }
        out.printf("  \u001B[1m%-16s %-10s %-10s %-10s %-6s %-6s %-8s %s\u001B[0m%n",
                "POOL", "SIZE", "ALLOC", "FREE", "FRAG", "CAP", "DEDUP", "HEALTH");
        out.println("  ----------------------------------------------------------------------------------");
        for (ZfsPoolInfo p : r.pools()) {
            String healthColor = switch (p.healthStatus().toUpperCase()) {
                case "ONLINE" -> "\u001B[32mONLINE\u001B[0m";
                case "DEGRADED" -> "\u001B[33mDEGRADED\u001B[0m";
                default -> "\u001B[31m" + p.healthStatus() + "\u001B[0m";
            };
            out.printf("  %-16s %-10s %-10s %-10s %4d%% %4d%% %6.2fx %s%n",
                    p.name(),
                    formatBytes(p.sizeBytes()),
                    formatBytes(p.allocatedBytes()),
                    formatBytes(p.freeBytes()),
                    p.fragmentationPct(),
                    p.capacityPct(),
                    p.dedupRatio(),
                    healthColor);
        }
        out.printf("%n  Total Pools: \u001B[1m%d\u001B[0m%n", r.totalPools());
    }

    private void renderZfsPoolStatus(ZfsPoolStatusReport r, PrintWriter out) {
        out.println("\u001B[1;36m=== ZFS Pool Status: " + r.poolName() + " ===\u001B[0m");
        if (!r.installed()) {
            out.println("  Status: \u001B[31mNot Installed\u001B[0m");
            return;
        }
        String stateColor = "ONLINE".equalsIgnoreCase(r.state()) ? "\u001B[32mONLINE\u001B[0m" : "\u001B[31m" + r.state() + "\u001B[0m";
        out.printf("  %-12s: %s%n", "State", stateColor);
        if (!"N/A".equals(r.statusDetail())) {
            out.printf("  %-12s: %s%n", "Status", r.statusDetail());
        }
        out.printf("  %-12s: %s%n", "Scan/Scrub", r.scanInfo());
        out.printf("  %-12s: %s%n", "Errors", r.errors());
        if (!r.configTree().isBlank() && !"N/A".equals(r.configTree())) {
            out.println("\n  Configuration / Vdev Layout:");
            for (String line : r.configTree().split("\n")) {
                out.println("    " + line);
            }
        }
    }

    private void renderZfsDatasetList(ZfsDatasetListReport r, PrintWriter out) {
        out.println("\u001B[1;36m=== ZFS Datasets ===\u001B[0m");
        if (!r.installed()) {
            out.println("  Status: \u001B[31mNot Installed\u001B[0m");
            return;
        }
        if (r.datasets().isEmpty()) {
            out.println("  " + r.message());
            return;
        }
        out.printf("  \u001B[1m%-30s %-12s %-10s %-10s %-10s %s\u001B[0m%n",
                "DATASET", "TYPE", "USED", "AVAIL", "REFER", "MOUNTPOINT");
        out.println("  -------------------------------------------------------------------------------------------------");
        for (ZfsDatasetInfo d : r.datasets()) {
            out.printf("  %-30s %-12s %-10s %-10s %-10s %s%n",
                    truncate(d.name(), 30),
                    d.type(),
                    formatBytes(d.usedBytes()),
                    formatBytes(d.availableBytes()),
                    formatBytes(d.referencedBytes()),
                    truncate(d.mountpoint(), 30));
        }
        out.printf("%n  Total Datasets: \u001B[1m%d\u001B[0m%n", r.totalDatasets());
    }

    private void renderZfsSnapshotList(ZfsSnapshotListReport r, PrintWriter out) {
        out.println("\u001B[1;36m=== ZFS Snapshots ===\u001B[0m");
        if (!r.installed()) {
            out.println("  Status: \u001B[31mNot Installed\u001B[0m");
            return;
        }
        if (r.snapshots().isEmpty()) {
            out.println("  " + r.message());
            return;
        }
        out.printf("  \u001B[1m%-36s %-10s %-10s %s\u001B[0m%n",
                "SNAPSHOT NAME", "USED", "REFER", "CREATION");
        out.println("  ------------------------------------------------------------------------------------");
        for (ZfsSnapshotInfo s : r.snapshots()) {
            out.printf("  %-36s %-10s %-10s %s%n",
                    truncate(s.name(), 36),
                    formatBytes(s.usedBytes()),
                    formatBytes(s.referencedBytes()),
                    s.creationTime());
        }
        out.printf("%n  Total Snapshots: \u001B[1m%d\u001B[0m%n", r.totalSnapshots());
    }

    private void renderZfsDatasetDetail(ZfsDatasetDetailReport r, PrintWriter out) {
        out.println("\u001B[1;36m=== ZFS Dataset Properties: " + r.datasetName() + " ===\u001B[0m");
        if (!r.installed()) {
            out.println("  Status: \u001B[31mNot Installed\u001B[0m");
            return;
        }
        if (r.properties().isEmpty()) {
            out.println("  " + r.message());
            return;
        }
        out.printf("  \u001B[1m%-24s %s\u001B[0m%n", "PROPERTY", "VALUE");
        out.println("  ------------------------------------------------------------------------");
        for (java.util.Map.Entry<String, String> entry : r.properties().entrySet()) {
            out.printf("  %-24s %s%n", entry.getKey(), entry.getValue());
        }
        out.printf("%n  Total Properties: \u001B[1m%d\u001B[0m%n", r.properties().size());
    }

    private void renderTimerList(TimerListReport r, PrintWriter out) {
        out.println("\u001B[1;36m=== Scheduled Timers & Automation Tasks (" + r.scope().name().toLowerCase() + " scope) ===\u001B[0m");
        if (r.timers().isEmpty()) {
            out.println("  ℹ No scheduled timers configured.");
            out.println("  💡 Create one using: \u001B[32mnexus timer create <name> --schedule 'daily'\u001B[0m");
            return;
        }

        out.printf("  \u001B[1m%-8s %-22s %-16s %-10s %-22s %-14s\u001B[0m%n",
                "ID", "IDENTIFIER", "SCHEDULE", "STATUS", "NEXT ACTIVATION", "LAST RESULT");
        out.println("  ------------------------------------------------------------------------------------------------");

        for (TimerInfo t : r.timers()) {
            String status = t.active() ? "\u001B[32mActive\u001B[0m" : "\u001B[90mInactive\u001B[0m";
            String lastResult = "-";
            if (t.lastResult() != null && !t.lastResult().isBlank()) {
                if ("0".equals(t.lastResult()) || "success".equalsIgnoreCase(t.lastResult())) {
                    lastResult = "\u001B[32msuccess\u001B[0m";
                } else if (!"-".equals(t.lastResult())) {
                    lastResult = "\u001B[31m" + t.lastResult() + "\u001B[0m";
                }
            }

            out.printf("  \u001B[1;36m%-8s\u001B[0m %-22s %-16s %-19s %-22s %s%n",
                    t.id(),
                    truncate(t.fullIdentifier(), 22),
                    truncate(t.schedule(), 16),
                    status,
                    truncate(t.nextRun() != null ? t.nextRun() : "-", 22),
                    lastResult);
        }

        out.printf("%n  Total Timers: \u001B[1m%d\u001B[0m%n", r.count());
        out.println("  💡 Edit automation script: \u001B[32mnexus timer edit <ID>\u001B[0m | Trigger now: \u001B[32mnexus timer run <ID>\u001B[0m");
    }

    private void renderTimerCreate(TimerCreateResult r, PrintWriter out) {
        if (!r.success()) {
            out.println("\u001B[31m✖ Failed to create timer: " + r.message() + "\u001B[0m");
            return;
        }
        TimerInfo t = r.timer();
        String fullId = t != null ? t.fullIdentifier() : "Timer";
        out.println("\u001B[1;32m✓ Scheduled Timer " + fullId + " successfully created!\u001B[0m");
        if (t != null) {
            out.printf("  • Identifier:      \u001B[1m%s\u001B[0m (%s)%n", t.fullIdentifier(), t.id());
            out.printf("  • Schedule:        %s (%s)%n", t.scheduleDetails(), t.schedule());
            out.printf("  • Scope:           %s%n", t.scope().name().toLowerCase());
            if (t.unitPath() != null) {
                out.printf("  • Timer Unit:      %s%n", t.unitPath());
            }
            if (t.serviceUnitPath() != null) {
                out.printf("  • Service Unit:    %s%n", t.serviceUnitPath());
            }
        }
        out.println();
        out.println("  \u001B[1;33m⚡ Executable Automation Script Stub:\u001B[0m");
        out.printf("    \u001B[1m%s\u001B[0m%n", r.scriptPath());
        out.println();
        out.println("  👉 \u001B[1mAdd your automation code to the script above:\u001B[0m");
        out.printf("     \u001B[36mnexus timer edit %s\u001B[0m%n", t != null ? t.id() : fullId);
        out.printf("     or: ${EDITOR:-nano} %s%n", r.scriptPath());
        if (r.enableCommand() != null) {
            out.println();
            out.printf("  ℹ System activation command: %s%n", r.enableCommand());
        }
    }

    private void renderTimerStatus(TimerStatusResult r, PrintWriter out) {
        if (!r.success() || r.timer() == null) {
            out.println("\u001B[31m✖ " + r.message() + "\u001B[0m");
            return;
        }
        TimerInfo t = r.timer();
        out.println("\u001B[1;36m=== Timer Details: " + t.fullIdentifier() + " (" + t.id() + ") ===\u001B[0m");
        out.printf("  • Name:             %s%n", t.name());
        out.printf("  • Schedule:         %s (%s)%n", t.scheduleDetails(), t.schedule());
        out.printf("  • Status:           %s%n", t.active() ? "\u001B[32mActive\u001B[0m" : "\u001B[90mInactive\u001B[0m");
        out.printf("  • Next Activation:  %s%n", t.nextRun() != null ? t.nextRun() : "-");
        out.printf("  • Last Trigger:     %s%n", t.lastRun() != null ? t.lastRun() : "-");
        out.printf("  • Last Exit Code:   %s%n", t.lastResult() != null ? t.lastResult() : "-");
        out.printf("  • Scope:            %s%n", t.scope().name().toLowerCase());
        out.printf("  • Script Path:      %s%n", t.scriptPath());
        if (t.unitPath() != null) {
            out.printf("  • Timer Unit:       %s%n", t.unitPath());
        }
        if (t.serviceUnitPath() != null) {
            out.printf("  • Service Unit:     %s%n", t.serviceUnitPath());
        }

        if (r.recentLogs() != null && !r.recentLogs().isEmpty()) {
            out.println();
            out.println("  \u001B[1mRecent Journal Execution Logs:\u001B[0m");
            for (String logLine : r.recentLogs()) {
                out.println("    " + logLine);
            }
        }
    }

    private void renderTimerUpdate(TimerUpdateResult r, PrintWriter out) {
        if (!r.success()) {
            out.println("\u001B[31m✖ Failed to update timer: " + r.message() + "\u001B[0m");
            return;
        }
        TimerInfo t = r.timer();
        out.println("\u001B[1;32m✓ Timer " + (t != null ? t.fullIdentifier() : "") + " successfully updated!\u001B[0m");
        if (t != null) {
            out.printf("  • Schedule: %s (%s)%n", t.scheduleDetails(), t.schedule());
            if (t.description() != null && !t.description().isBlank()) {
                out.printf("  • Description: %s%n", t.description());
            }
        }
    }

    private void renderTimerRun(TimerRunResult r, PrintWriter out) {
        if (!r.success()) {
            out.println("\u001B[31m✖ Execution Failed (exit code " + r.exitCode() + "): " + r.message() + "\u001B[0m");
            if (r.output() != null && !r.output().isBlank()) {
                out.println("  Output:\n" + r.output());
            }
            return;
        }
        out.println("\u001B[1;32m✓ Task for timer " + r.nameOrId() + " executed successfully (exit code " + r.exitCode() + " in " + r.executionTimeMs() + "ms)\u001B[0m");
        if (r.output() != null && !r.output().isBlank()) {
            out.println("  Output:\n" + r.output());
        }
    }

    private void renderTimerDelete(TimerDeleteResult r, PrintWriter out) {
        if (!r.success()) {
            out.println("\u001B[31m✖ Failed to delete timer: " + r.message() + "\u001B[0m");
            return;
        }
        out.println("\u001B[1;32m✓ Successfully removed scheduled timer: " + r.fullIdentifier() + "\u001B[0m");
        out.printf("  • Timer Unit Deleted:   %s%n", r.deletedTimerUnit() ? "Yes" : "No");
        out.printf("  • Service Unit Deleted: %s%n", r.deletedServiceUnit() ? "Yes" : "No");
        out.printf("  • Script File Deleted:  %s%n", r.deletedScript() ? "Yes" : "Preserved");
    }

    private void renderTelegramSend(TelegramSendResult r, PrintWriter out) {
        if (r.success()) {
            out.printf("\u001B[1;32m✓ Telegram Notification Dispatched\u001B[0m (%d sent, %d failed)%n",
                    r.sentCount(), r.failedCount());
        } else {
            out.printf("\u001B[1;31m✖ Telegram Notification Dispatch Failed\u001B[0m (%d sent, %d failed)%n",
                    r.sentCount(), r.failedCount());
        }
        if (r.deliveries() != null && !r.deliveries().isEmpty()) {
            for (TelegramRecipientResult d : r.deliveries()) {
                if (d.success()) {
                    String msgInfo = d.messageId() != null ? " (message #" + d.messageId() + ")" : "";
                    out.printf("  \u001B[32m✓\u001B[0m Delivered to %s%s%n", d.recipient(), msgInfo);
                } else {
                    out.printf("  \u001B[31m✖\u001B[0m Failed to %s: %s%n", d.recipient(), d.errorMessage());
                }
            }
        }
        if (r.message() != null && !r.message().isBlank()) {
            out.printf("  Summary: %s%n", r.message());
        }
    }

    private void renderPackageOperation(PackageOperationResult r, PrintWriter out) {
        if (r.success()) {
            out.printf("\u001B[1;32m✓ System Package %s Succeeded\u001B[0m (Manager: %s, Exit code: %d)%n",
                    r.action(), r.packageManager(), r.exitCode());
        } else {
            out.printf("\u001B[1;31m✖ System Package %s Failed\u001B[0m (Manager: %s, Exit code: %d)%n",
                    r.action(), r.packageManager(), r.exitCode());
        }
        if (r.message() != null && !r.message().isBlank()) {
            out.println("  " + r.message());
        }
        if (r.output() != null && !r.output().isBlank()) {
            out.println("\n  Output:\n" + r.output().trim());
        }
    }

    private void renderSystemAudit(SystemAuditReport r, PrintWriter out) {
        String badge;
        if ("HEALTHY".equalsIgnoreCase(r.overallStatus())) {
            badge = "\u001B[1;32m✓ HEALTHY\u001B[0m";
        } else if ("WARNING".equalsIgnoreCase(r.overallStatus())) {
            badge = "\u001B[1;33m⚠ WARNING\u001B[0m";
        } else {
            badge = "\u001B[1;31m✖ CRITICAL\u001B[0m";
        }

        out.printf("\u001B[1;36m=== Nexus System Health & Security Audit ===\u001B[0m [%s]%n", badge);
        out.printf("  Summary: \u001B[32m%d passed\u001B[0m, \u001B[33m%d warning(s)\u001B[0m, \u001B[31m%d critical\u001B[0m%n%n",
                r.passedCount(), r.warnCount(), r.criticalCount());

        if (r.checks() != null) {
            for (AuditCheckItem item : r.checks()) {
                String symbol = switch (item.severity()) {
                    case PASS -> "\u001B[32m✓\u001B[0m";
                    case WARN -> "\u001B[33m⚠\u001B[0m";
                    case CRITICAL -> "\u001B[31m✖\u001B[0m";
                };
                out.printf("  %s \u001B[1m[%s]\u001B[0m %s: %s%n", symbol, item.category(), item.checkName(), item.message());
                if (item.remediation() != null && !item.remediation().isBlank()) {
                    out.printf("     \u001B[33m↳ Action:\u001B[0m %s%n", item.remediation());
                }
            }
        }
    }

    private String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format("%.2f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    private String truncate(String val, int max) {
        if (val == null) return "";
        return val.length() <= max ? val : val.substring(0, max - 3) + "...";
    }
}
