package com.nexus.platform;

import com.nexus.capabilities.system.dto.SystemInfoReport;
import com.nexus.capabilities.system.dto.SystemUptimeReport;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;
import java.time.Instant;

/**
 * Standard cross-platform system collector using standard Java management APIs.
 * Operates on Windows, macOS, and Linux without native dependencies.
 */
public class GenericSystemCollector implements SystemCollector {

    protected final Instant agentStartInstant = Instant.now();

    @Override
    public SystemInfoReport collectSystemInfo() {
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();

        String hostname = resolveHostname();
        String osName = osBean.getName();
        String osVersion = osBean.getVersion();
        String osArch = osBean.getArch();
        int processors = osBean.getAvailableProcessors();

        long totalMemory = 0;
        long freeMemory = 0;
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunBean) {
            totalMemory = sunBean.getTotalMemorySize();
            freeMemory = sunBean.getFreeMemorySize();
        } else {
            totalMemory = Runtime.getRuntime().totalMemory();
            freeMemory = Runtime.getRuntime().freeMemory();
        }

        String runtimeType = System.getProperty("org.graalvm.nativeimage.imagecode") != null ? "GraalVM Native Image" : "JVM";
        String timezone = HostTimezoneResolver.getFormattedTimezone();

        return new SystemInfoReport(
                hostname,
                osName,
                osVersion,
                osArch,
                osVersion, // Generic fallback
                processors,
                totalMemory,
                freeMemory,
                System.getProperty("java.vendor", "Unknown"),
                System.getProperty("java.version", "Unknown"),
                runtimeType,
                timezone,
                Instant.now()
        );
    }

    @Override
    public SystemUptimeReport collectSystemUptime() {
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        long agentUptimeMs = runtimeBean.getUptime();
        long agentUptimeSecs = agentUptimeMs / 1000;

        // Generic fallback estimates host uptime from agent uptime if OS specific counter not available
        long hostUptimeSecs = agentUptimeSecs;
        Instant bootTimestamp = Instant.now().minusSeconds(hostUptimeSecs);

        return new SystemUptimeReport(
                hostUptimeSecs,
                TimeUtils.formatDuration(hostUptimeSecs),
                agentUptimeSecs,
                TimeUtils.formatDuration(agentUptimeSecs),
                bootTimestamp,
                agentStartInstant
        );
    }

    protected String resolveHostname() {
        try {
            String host = InetAddress.getLocalHost().getHostName();
            if (host != null && !host.isBlank()) {
                return host;
            }
        } catch (Exception ignored) {
        }
        String envHost = System.getenv("HOSTNAME");
        if (envHost != null && !envHost.isBlank()) {
            return envHost;
        }
        String compName = System.getenv("COMPUTERNAME");
        if (compName != null && !compName.isBlank()) {
            return compName;
        }
        return "localhost";
    }
}
