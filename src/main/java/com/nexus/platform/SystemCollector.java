package com.nexus.platform;

import com.nexus.capabilities.system.dto.SystemInfoReport;
import com.nexus.capabilities.system.dto.SystemUptimeReport;

/**
 * Platform abstraction for collecting system host information and uptime.
 */
public interface SystemCollector {

    SystemInfoReport collectSystemInfo();

    SystemUptimeReport collectSystemUptime();
}
