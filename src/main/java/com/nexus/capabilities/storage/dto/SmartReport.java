package com.nexus.capabilities.storage.dto;

import java.time.Instant;
import java.util.List;

/**
 * SMART health report for a physical drive or NVMe controller.
 */
public record SmartReport(
    String devicePath,
    String modelName,
    String serialNumber,
    String firmwareVersion,
    String protocol,
    boolean passed,
    int temperatureCelsius,
    long powerOnHours,
    long reallocatedSectors,
    long totalBytesWritten, // Total bytes written (-1 if not available)
    long unsafeShutdowns,   // Unsafe shutdowns count (-1 if not available)
    long powerCycles,       // Power cycle count (-1 if not available)
    List<String> warningsAndErrors,
    List<SmartAttribute> attributes,
    String healthStatus, // HEALTHY, WARNING, FAILING, UNKNOWN
    Instant timestamp
) {

    /**
     * Backward-compatible 12-parameter constructor for existing tests and callers.
     */
    public SmartReport(
            String devicePath,
            String modelName,
            String serialNumber,
            String firmwareVersion,
            String protocol,
            boolean passed,
            int temperatureCelsius,
            long powerOnHours,
            long reallocatedSectors,
            List<SmartAttribute> attributes,
            String healthStatus,
            Instant timestamp) {
        this(devicePath, modelName, serialNumber, firmwareVersion, protocol, passed,
                temperatureCelsius, powerOnHours, reallocatedSectors, -1, -1, -1,
                List.of(), attributes, healthStatus, timestamp);
    }

    public boolean isHealthy() {
        return passed && reallocatedSectors == 0 && (warningsAndErrors == null || warningsAndErrors.isEmpty());
    }

    public String getFormattedPowerOnTime() {
        if (powerOnHours <= 0) return "N/A";
        long days = powerOnHours / 24;
        long remainingHours = powerOnHours % 24;
        if (days >= 365) {
            double years = (double) days / 365.25;
            return String.format("%,d hrs (~%.1f yrs)", powerOnHours, years);
        } else if (days > 0) {
            return String.format("%,d hrs (%dd %dh)", powerOnHours, days, remainingHours);
        }
        return String.format("%,d hrs", powerOnHours);
    }

    public String getFormattedTbw() {
        if (totalBytesWritten <= 0) return "N/A";
        double tb = (double) totalBytesWritten / (1024.0 * 1024.0 * 1024.0 * 1024.0);
        if (tb >= 1.0) {
            return String.format("%.2f TBW", tb);
        }
        double gb = (double) totalBytesWritten / (1024.0 * 1024.0 * 1024.0);
        return String.format("%.2f GBW", gb);
    }

    public String getFormattedShutdowns() {
        if (unsafeShutdowns < 0 && powerCycles < 0) return "N/A";
        if (unsafeShutdowns >= 0 && powerCycles >= 0) {
            return String.format("%,d (Cycles: %,d)", unsafeShutdowns, powerCycles);
        } else if (unsafeShutdowns >= 0) {
            return String.format("%,d", unsafeShutdowns);
        } else {
            return String.format("Cycles: %,d", powerCycles);
        }
    }

    public String getFormattedWarnings() {
        if (warningsAndErrors == null || warningsAndErrors.isEmpty()) {
            return "None (0 errors)";
        }
        return String.join("; ", warningsAndErrors);
    }
}
