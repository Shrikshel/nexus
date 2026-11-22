package com.nexus.capabilities.system.audit.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditCheckItem(
        String category,
        String checkName,
        AuditSeverity severity,
        String message,
        String remediation
) {}
