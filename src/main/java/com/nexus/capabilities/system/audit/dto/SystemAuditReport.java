package com.nexus.capabilities.system.audit.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SystemAuditReport(
        String overallStatus,
        int passedCount,
        int warnCount,
        int criticalCount,
        List<AuditCheckItem> checks,
        Instant timestamp
) {}
