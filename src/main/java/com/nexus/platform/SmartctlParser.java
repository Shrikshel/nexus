package com.nexus.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.capabilities.storage.dto.SmartAttribute;
import com.nexus.capabilities.storage.dto.SmartReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses smartctl JSON output (format version 1.0) into structured SmartReport domain objects,
 * extracting TBW, power-on hours, unsafe shutdowns, power cycles, and diagnostics.
 */
@Component
public class SmartctlParser {

    private static final Logger log = LoggerFactory.getLogger(SmartctlParser.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SmartReport parse(String devicePath, String jsonOutput) {
        try {
            JsonNode root = objectMapper.readTree(jsonOutput);

            String model = root.path("model_name").asText(root.path("device").path("name").asText("Unknown Model"));
            String serial = root.path("serial_number").asText("Unknown Serial");
            String firmware = root.path("firmware_version").asText("N/A");
            String protocol = root.path("device").path("protocol").asText("ATA/SCSI");

            boolean passed = root.path("smart_status").path("passed").asBoolean(true);
            int temp = root.path("temperature").path("current").asInt(0);
            long hours = root.path("power_on_time").path("hours").asLong(0);

            long reallocated = 0;
            long totalBytesWritten = -1;
            long unsafeShutdowns = -1;
            long powerCycles = -1;
            List<String> warningsAndErrors = new ArrayList<>();
            List<SmartAttribute> attributes = new ArrayList<>();

            if (!passed) {
                warningsAndErrors.add("SMART health check reported FAILING");
            }

            // Check messages array
            JsonNode messages = root.path("messages");
            if (messages.isArray()) {
                for (JsonNode msg : messages) {
                    String str = msg.path("string").asText("");
                    String severity = msg.path("severity").asText("info");
                    if ("warning".equalsIgnoreCase(severity) || "error".equalsIgnoreCase(severity)) {
                        warningsAndErrors.add(str);
                    }
                }
            }

            // Parse NVMe health information log
            JsonNode nvmeLog = root.path("nvme_smart_health_information_log");
            if (!nvmeLog.isMissingNode() && !nvmeLog.isNull()) {
                if (temp == 0) {
                    temp = nvmeLog.path("temperature").asInt(0);
                }
                if (hours == 0) {
                    hours = nvmeLog.path("power_on_hours").asLong(0);
                }

                // Data Units Written in NVMe is 1,000 sectors of 512 bytes = 512,000 bytes
                long dataUnitsWritten = nvmeLog.path("data_units_written").asLong(-1);
                if (dataUnitsWritten > 0) {
                    totalBytesWritten = dataUnitsWritten * 1000L * 512L;
                }

                unsafeShutdowns = nvmeLog.path("unsafe_shutdowns").asLong(-1);
                powerCycles = nvmeLog.path("power_cycles").asLong(-1);

                int crit = nvmeLog.path("critical_warning").asInt(0);
                if (crit != 0) {
                    warningsAndErrors.add(String.format("NVMe Critical Warning flag active: 0x%02x", crit));
                }

                long mediaErrors = nvmeLog.path("media_and_data_integrity_errors").asLong(0);
                if (mediaErrors > 0) {
                    warningsAndErrors.add(String.format("Media/Data Integrity Errors: %,d", mediaErrors));
                }

                long errLogEntries = nvmeLog.path("num_err_log_entries").asLong(0);
                if (errLogEntries > 0) {
                    warningsAndErrors.add(String.format("Error Information Log Entries: %,d", errLogEntries));
                }
            }

            // Parse ATA SMART attributes table
            JsonNode table = root.path("ata_smart_attributes").path("table");
            if (table.isArray()) {
                for (JsonNode row : table) {
                    int id = row.path("id").asInt();
                    String name = row.path("name").asText("Attr_" + id);
                    long rawVal = row.path("raw").path("value").asLong(0);
                    int val = row.path("value").asInt(0);
                    int worst = row.path("worst").asInt(0);
                    int thresh = row.path("thresh").asInt(0);
                    boolean failing = row.path("when_failed").asText("").length() > 0;

                    if (id == 5) { // Reallocated_Sector_Ct
                        reallocated = rawVal;
                        if (reallocated > 0) {
                            warningsAndErrors.add(String.format("Reallocated Sectors: %,d", reallocated));
                        }
                    } else if (id == 9 && hours == 0) { // Power_On_Hours
                        hours = rawVal;
                    } else if (id == 12 && powerCycles < 0) { // Power_Cycle_Count
                        powerCycles = rawVal;
                    } else if ((id == 192 || id == 174) && unsafeShutdowns < 0) { // Power-Off_Retract_Count / Unsafe Shutdown
                        unsafeShutdowns = rawVal;
                    } else if (id == 197 && rawVal > 0) { // Current_Pending_Sector
                        warningsAndErrors.add(String.format("Current Pending Sectors: %,d", rawVal));
                    } else if (id == 198 && rawVal > 0) { // Offline_Uncorrectable
                        warningsAndErrors.add(String.format("Offline Uncorrectable Sectors: %,d", rawVal));
                    } else if (id == 241 && totalBytesWritten < 0) { // Total_LBAs_Written / Lifetime_Writes_GiB
                        if (name.toLowerCase().contains("gib") || name.toLowerCase().contains("gb")) {
                            totalBytesWritten = rawVal * 1024L * 1024L * 1024L;
                        } else {
                            totalBytesWritten = rawVal * 512L;
                        }
                    }

                    if (failing) {
                        warningsAndErrors.add(String.format("Attribute '%s' (ID %d) is FAILING threshold", name, id));
                    }

                    attributes.add(new SmartAttribute(id, name, rawVal, val, worst, thresh, failing));
                }
            }

            // Determine overall health status
            String status = "HEALTHY";
            if (!passed || !warningsAndErrors.isEmpty()) {
                if (!passed) {
                    status = "FAILING";
                } else {
                    status = "WARNING";
                }
            } else if (temp > 60 && temp < 100) {
                status = "WARNING";
                warningsAndErrors.add(String.format("High drive temperature: %d°C", temp));
            }

            return new SmartReport(
                    devicePath,
                    model,
                    serial,
                    firmware,
                    protocol,
                    passed,
                    temp,
                    hours,
                    reallocated,
                    totalBytesWritten,
                    unsafeShutdowns,
                    powerCycles,
                    warningsAndErrors,
                    attributes,
                    status,
                    Instant.now()
            );
        } catch (Exception e) {
            log.error("Failed to parse smartctl JSON for {}: {}", devicePath, e.getMessage());
            return new SmartReport(
                    devicePath,
                    "Unknown Drive",
                    "Unknown",
                    "N/A",
                    "Unknown",
                    false,
                    0,
                    0,
                    0,
                    -1,
                    -1,
                    -1,
                    List.of("Parser error: " + e.getMessage()),
                    List.of(),
                    "UNKNOWN",
                    Instant.now()
            );
        }
    }
}
