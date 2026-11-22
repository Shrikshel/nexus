package com.nexus.platform.service;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ServiceOperationResult(
        boolean success,
        String action,
        String serviceName,
        String unitPath,
        String message
) {}
