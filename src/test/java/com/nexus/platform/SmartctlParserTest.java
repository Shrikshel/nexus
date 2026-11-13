package com.nexus.platform;

import com.nexus.capabilities.storage.dto.SmartReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SmartctlParserTest {

    private final SmartctlParser parser = new SmartctlParser();

    @Test
    @DisplayName("Parses healthy drive smartctl JSON output")
    void testParseHealthyDrive() {
        String json = """
        {
          "json_format_version": [1, 0],
          "model_name": "Samsung SSD 980 PRO 1TB",
          "serial_number": "S5GXNF0R123456",
          "firmware_version": "5B2QGXA7",
          "device": { "name": "/dev/nvme0", "protocol": "NVMe" },
          "smart_status": { "passed": true },
          "temperature": { "current": 38 },
          "power_on_time": { "hours": 4200 },
          "nvme_smart_health_information_log": {
            "data_units_written": 23456780,
            "unsafe_shutdowns": 5,
            "power_cycles": 120,
            "media_and_data_integrity_errors": 0,
            "critical_warning": 0
          },
          "ata_smart_attributes": {
            "table": [
              { "id": 5, "name": "Reallocated_Sector_Ct", "value": 100, "worst": 100, "thresh": 10, "raw": { "value": 0 } }
            ]
          }
        }
        """;

        SmartReport report = parser.parse("/dev/nvme0", json);

        assertThat(report.devicePath()).isEqualTo("/dev/nvme0");
        assertThat(report.modelName()).isEqualTo("Samsung SSD 980 PRO 1TB");
        assertThat(report.serialNumber()).isEqualTo("S5GXNF0R123456");
        assertThat(report.passed()).isTrue();
        assertThat(report.temperatureCelsius()).isEqualTo(38);
        assertThat(report.powerOnHours()).isEqualTo(4200);
        assertThat(report.reallocatedSectors()).isEqualTo(0);
        assertThat(report.unsafeShutdowns()).isEqualTo(5);
        assertThat(report.powerCycles()).isEqualTo(120);
        assertThat(report.totalBytesWritten()).isGreaterThan(0);
        assertThat(report.getFormattedTbw()).contains("TBW");
        assertThat(report.getFormattedPowerOnTime()).contains("4,200 hrs");
        assertThat(report.getFormattedShutdowns()).contains("5 (Cycles: 120)");
        assertThat(report.healthStatus()).isEqualTo("HEALTHY");
    }

    @Test
    @DisplayName("Parses degraded drive with reallocated sectors as WARNING")
    void testParseDegradedDrive() {
        String json = """
        {
          "json_format_version": [1, 0],
          "model_name": "WDC WD20EZRX-00D8PB0",
          "serial_number": "WD-WCC4M0123456",
          "device": { "name": "/dev/sda", "protocol": "ATA" },
          "smart_status": { "passed": true },
          "temperature": { "current": 42 },
          "power_on_time": { "hours": 28400 },
          "ata_smart_attributes": {
            "table": [
              { "id": 5, "name": "Reallocated_Sector_Ct", "value": 95, "worst": 95, "thresh": 36, "raw": { "value": 48 } },
              { "id": 12, "name": "Power_Cycle_Count", "value": 100, "worst": 100, "thresh": 0, "raw": { "value": 450 } },
              { "id": 192, "name": "Power-Off_Retract_Count", "value": 100, "worst": 100, "thresh": 0, "raw": { "value": 18 } },
              { "id": 241, "name": "Total_LBAs_Written", "value": 100, "worst": 100, "thresh": 0, "raw": { "value": 100000000000 } }
            ]
          }
        }
        """;

        SmartReport report = parser.parse("/dev/sda", json);

        assertThat(report.devicePath()).isEqualTo("/dev/sda");
        assertThat(report.passed()).isTrue();
        assertThat(report.reallocatedSectors()).isEqualTo(48);
        assertThat(report.unsafeShutdowns()).isEqualTo(18);
        assertThat(report.powerCycles()).isEqualTo(450);
        assertThat(report.totalBytesWritten()).isGreaterThan(0);
        assertThat(report.warningsAndErrors()).anyMatch(w -> w.contains("Reallocated Sectors: 48"));
        assertThat(report.healthStatus()).isEqualTo("WARNING");
    }

    @Test
    @DisplayName("Parses failed drive as FAILING")
    void testParseFailingDrive() {
        String json = """
        {
          "json_format_version": [1, 0],
          "model_name": "Seagate Barracuda 4TB",
          "serial_number": "ST4000DM000",
          "device": { "name": "/dev/sdb", "protocol": "ATA" },
          "smart_status": { "passed": false },
          "temperature": { "current": 55 },
          "power_on_time": { "hours": 35000 }
        }
        """;

        SmartReport report = parser.parse("/dev/sdb", json);

        assertThat(report.passed()).isFalse();
        assertThat(report.healthStatus()).isEqualTo("FAILING");
        assertThat(report.warningsAndErrors()).anyMatch(w -> w.contains("FAILING"));
    }
}
