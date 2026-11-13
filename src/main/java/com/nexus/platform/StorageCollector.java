package com.nexus.platform;

import com.nexus.capabilities.storage.dto.DiskListReport;
import com.nexus.capabilities.storage.dto.SmartReport;

import java.util.List;

/**
 * Platform abstraction for collecting storage, filesystem, and SMART health telemetry.
 */
public interface StorageCollector {

    DiskListReport collectDiskList();

    List<SmartReport> collectSmartReports(String targetDevice);
}
