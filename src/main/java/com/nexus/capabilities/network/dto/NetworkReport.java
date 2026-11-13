package com.nexus.capabilities.network.dto;

import java.time.Instant;
import java.util.List;

/**
 * Host network interfaces and addresses report.
 */
public record NetworkReport(
    List<NetworkInterfaceInfo> interfaces,
    int totalInterfaceCount,
    Instant timestamp
) {}
