package com.nexus.platform.service;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ServiceStatusDetails(
        boolean installed,
        boolean enabled,
        boolean running,
        String serviceName,
        String unitPath,
        String platform,
        String stateDescription
) {}
