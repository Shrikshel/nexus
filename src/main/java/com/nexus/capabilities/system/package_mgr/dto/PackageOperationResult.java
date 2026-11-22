package com.nexus.capabilities.system.package_mgr.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PackageOperationResult(
        boolean success,
        String action,
        String packageManager,
        int exitCode,
        String message,
        String output,
        Instant timestamp
) {}
