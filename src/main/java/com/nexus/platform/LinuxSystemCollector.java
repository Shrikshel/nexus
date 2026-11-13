package com.nexus.platform;

import com.nexus.capabilities.system.dto.SystemInfoReport;
import com.nexus.capabilities.system.dto.SystemUptimeReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Linux-optimized system metrics collector reading from virtual filesystems (/proc, /sys).
 */
public class LinuxSystemCollector extends GenericSystemCollector {

    private static final Logger log = LoggerFactory.getLogger(LinuxSystemCollector.class);

    private static final Path PROC_UPTIME = Path.of("/proc/uptime");
    private static final Path PROC_HOSTNAME = Path.of("/proc/sys/kernel/hostname");
    private static final Path PROC_OSRELEASE = Path.of("/proc/sys/kernel/osrelease");
    private static final Path PROC_MEMINFO = Path.of("/proc/meminfo");

    @Override
    public SystemInfoReport collectSystemInfo() {
        SystemInfoReport generic = super.collectSystemInfo();

        String hostname = readFirstLine(PROC_HOSTNAME, generic.hostname());
        String kernel = readFirstLine(PROC_OSRELEASE, generic.kernelVersion());

        long totalMem = generic.totalPhysicalMemoryBytes();
        long freeMem = generic.freePhysicalMemoryBytes();

        try {
            if (Files.exists(PROC_MEMINFO)) {
                List<String> lines = Files.readAllLines(PROC_MEMINFO);
                for (String line : lines) {
                    if (line.startsWith("MemTotal:")) {
                        totalMem = parseMemInfoKb(line);
                    } else if (line.startsWith("MemAvailable:")) {
                        freeMem = parseMemInfoKb(line);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not parse /proc/meminfo: {}", e.getMessage());
        }

        return new SystemInfoReport(
                hostname,
                generic.osName(),
                generic.osVersion(),
                generic.osArch(),
                kernel,
                generic.availableProcessors(),
                totalMem,
                freeMem,
                generic.javaVendor(),
                generic.javaVersion(),
                generic.runtimeType(),
                generic.timezone(),
                Instant.now()
        );
    }

    @Override
    public SystemUptimeReport collectSystemUptime() {
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        long agentUptimeSecs = runtimeBean.getUptime() / 1000;

        long hostUptimeSecs = agentUptimeSecs;
        try {
            if (Files.exists(PROC_UPTIME)) {
                String content = Files.readString(PROC_UPTIME).trim();
                String[] parts = content.split("\\s+");
                if (parts.length > 0) {
                    double uptimeDouble = Double.parseDouble(parts[0]);
                    hostUptimeSecs = (long) uptimeDouble;
                }
            }
        } catch (Exception e) {
            log.debug("Could not read /proc/uptime, falling back: {}", e.getMessage());
        }

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

    private String readFirstLine(Path path, String fallback) {
        try {
            if (Files.exists(path)) {
                return Files.readString(path).trim();
            }
        } catch (IOException ignored) {
        }
        return fallback;
    }

    private long parseMemInfoKb(String line) {
        try {
            String[] parts = line.split("\\s+");
            if (parts.length >= 2) {
                return Long.parseLong(parts[1]) * 1024L; // KB to Bytes
            }
        } catch (Exception ignored) {
        }
        return 0L;
    }
}
