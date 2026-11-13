package com.nexus.capabilities.system.dto;

import java.time.Instant;

/**
 * Host and agent uptime metrics report.
 */
public record SystemUptimeReport(
    long hostUptimeSeconds,
    String hostUptimeFormatted,
    long agentUptimeSeconds,
    String agentUptimeFormatted,
    Instant bootTimestamp,
    Instant agentStartTimestamp
) {}
