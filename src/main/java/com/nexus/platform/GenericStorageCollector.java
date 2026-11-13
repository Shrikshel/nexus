package com.nexus.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.capabilities.storage.dto.DiskListReport;
import com.nexus.capabilities.storage.dto.DiskPartitionReport;
import com.nexus.capabilities.storage.dto.SmartReport;
import com.nexus.core.security.privilege.PrivilegeRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.time.Instant;
import java.util.*;

/**
 * Cross-platform storage and filesystem collector.
 * Accurately filters out loop devices, squashfs, container layers, and redundant ZFS subdatasets,
 * reporting only primary physical disks and mount points.
 */
@Component
public class GenericStorageCollector implements StorageCollector {

    private static final Logger log = LoggerFactory.getLogger(GenericStorageCollector.class);

    private static final Set<String> IGNORED_FS_TYPES = Set.of(
            "squashfs", "tmpfs", "devtmpfs", "ramfs", "overlay", "shm", "proc",
            "sysfs", "cgroup", "cgroup2", "securityfs", "fuse", "pstore", "bpf",
            "autofs", "mqueue", "debugfs", "tracefs", "hugetlbfs", "configfs",
            "binfmt_misc", "nsfs", "efivarfs", "fusectl", "rpc_pipefs", "devpts",
            "fuse.lxcfs"
    );

    private final SmartctlParser smartctlParser;
    private final PrivilegeRunner privilegeRunner;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GenericStorageCollector(SmartctlParser smartctlParser, PrivilegeRunner privilegeRunner) {
        this.smartctlParser = smartctlParser;
        this.privilegeRunner = privilegeRunner;
    }

    @Override
    public DiskListReport collectDiskList() {
        List<DiskPartitionReport> partitions = new ArrayList<>();
        Set<String> seenMounts = new HashSet<>();
        Set<String> seenDevices = new HashSet<>();
        long aggTotal = 0;
        long aggUsable = 0;

        for (FileStore store : FileSystems.getDefault().getFileStores()) {
            try {
                long total = store.getTotalSpace();
                // Filter out tiny pseudo mounts (minimum 50MB)
                if (total < 50L * 1024L * 1024L) {
                    continue;
                }

                String storeType = store.type() != null ? store.type().toLowerCase() : "";
                String storeName = store.name() != null ? store.name() : "";
                String storeString = store.toString();
                String mountPoint = extractMountPoint(storeString);

                // Filter out non-physical or redundant mounts
                if (!isPhysicalDiskMount(storeName, storeType, mountPoint)) {
                    continue;
                }

                // Deduplicate by mount point
                if (!seenMounts.add(mountPoint)) {
                    continue;
                }

                // Deduplicate identical store name/device unless it's root "/"
                if (!mountPoint.equals("/") && !storeName.isBlank() && !seenDevices.add(storeName)) {
                    continue;
                }

                long usable = store.getUsableSpace();
                long used = total - usable;
                double pct = (double) used * 100.0 / (double) total;

                partitions.add(new DiskPartitionReport(
                        mountPoint,
                        storeName,
                        store.type().toUpperCase(),
                        total,
                        usable,
                        used,
                        pct
                ));

                aggTotal += total;
                aggUsable += usable;
            } catch (Exception e) {
                log.debug("Skipping unreadable store {}: {}", store, e.getMessage());
            }
        }

        // Sort partitions by mount point length and name for predictable output
        partitions.sort(Comparator.comparing(DiskPartitionReport::mountPoint));

        long aggUsed = aggTotal - aggUsable;
        double aggPct = aggTotal > 0 ? (double) aggUsed * 100.0 / (double) aggTotal : 0.0;

        return new DiskListReport(partitions, aggTotal, aggUsable, aggPct, Instant.now());
    }

    private String extractMountPoint(String storeString) {
        if (storeString == null) return "/";
        int parenIdx = storeString.indexOf(" (");
        if (parenIdx > 0) {
            return storeString.substring(0, parenIdx).trim();
        }
        return storeString.trim();
    }

    private boolean isPhysicalDiskMount(String name, String type, String mountPoint) {
        String lowerName = name.toLowerCase();
        String lowerMount = mountPoint.toLowerCase();

        // 1. Exclude loop devices and snap packages unconditionally
        if (lowerName.contains("loop") || lowerMount.contains("/snap") || lowerMount.contains("/var/snap")) {
            return false;
        }

        // 2. Exclude container and docker runtime layers
        if (lowerMount.contains("/docker/") || lowerMount.contains("/containers/") ||
            lowerMount.contains("/overlay2/") || lowerMount.contains("/containerd/") ||
            lowerMount.contains("/lxd/")) {
            return false;
        }

        // 3. Always keep root "/" regardless of filesystem type (handles container roots)
        if (mountPoint.equals("/") || mountPoint.equals("/root")) {
            return true;
        }

        // 4. Exclude pseudo filesystems
        if (IGNORED_FS_TYPES.contains(type.toLowerCase())) {
            return false;
        }

        // 5. Exclude nested ZFS subdatasets and system directories sharing a pool
        if ("zfs".equalsIgnoreCase(type) || lowerName.contains("/")) {
            if (lowerMount.startsWith("/var/") || lowerMount.startsWith("/usr/") ||
                lowerMount.startsWith("/etc") || lowerMount.equals("/bin") ||
                lowerMount.startsWith("/lib") || lowerMount.contains("/.zfs") ||
                lowerMount.startsWith("/run")) {
                return false;
            }
        }

        return true;
    }

    @Override
    public List<SmartReport> collectSmartReports(String targetDevice) {
        List<String> devices = new ArrayList<>();
        if (targetDevice != null && !targetDevice.isBlank()) {
            devices.add(targetDevice.trim());
        } else {
            devices.addAll(scanDevices());
        }

        List<SmartReport> reports = new ArrayList<>();
        for (String dev : devices) {
            String jsonOutput = queryDeviceSmart(dev);
            if (jsonOutput != null && !jsonOutput.isBlank()) {
                reports.add(smartctlParser.parse(dev, jsonOutput));
            }
        }
        return reports;
    }

    protected List<String> scanDevices() {
        List<String> devices = new ArrayList<>();
        try {
            Process process = new ProcessBuilder("smartctl", "--scan", "--json=c").start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                JsonNode root = objectMapper.readTree(reader);
                JsonNode devs = root.path("devices");
                if (devs.isArray()) {
                    for (JsonNode d : devs) {
                        String name = d.path("name").asText();
                        if (!name.isBlank() && !devices.contains(name)) {
                            devices.add(name);
                        }
                    }
                }
            }
            process.waitFor();
        } catch (Exception e) {
            log.warn("smartctl device auto-scan failed: {}", e.getMessage());
        }

        // Fallback device detection if scan returns empty
        if (devices.isEmpty()) {
            for (String fallback : List.of("/dev/sda", "/dev/sdb", "/dev/nvme0", "/dev/nvme0n1")) {
                if (new File(fallback).exists()) {
                    devices.add(fallback);
                }
            }
        }
        return devices;
    }

    protected String queryDeviceSmart(String devicePath) {
        try {
            // Find smartctl binary
            String smartctl = new File("/usr/sbin/smartctl").exists() ? "/usr/sbin/smartctl" : "/usr/bin/smartctl";
            PrivilegeRunner.ExecutionOutput output = privilegeRunner.executeWhitelisted(smartctl, List.of("-a", devicePath, "--json=c"), 15);
            if (output != null && output.stdout() != null && output.stdout().contains("{")) {
                return output.stdout();
            }
        } catch (Exception e) {
            log.debug("Privileged smartctl execution for {} failed, attempting local fallback: {}", devicePath, e.getMessage());
        }

        try {
            Process p = new ProcessBuilder("smartctl", "-a", devicePath, "--json=c").start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            p.waitFor();
            return sb.toString();
        } catch (Exception ex) {
            log.warn("Failed to query SMART telemetry for {}: {}", devicePath, ex.getMessage());
            return null;
        }
    }
}
