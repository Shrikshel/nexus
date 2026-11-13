package com.nexus.capabilities.service.dto;

import java.time.Instant;
import java.util.List;

/**
 * Report containing system service units and statuses.
 */
public record ServiceListReport(
    List<ServiceUnitInfo> services,
    int totalCount,
    Instant timestamp
) {}
