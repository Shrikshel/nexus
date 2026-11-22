package com.nexus.capabilities.system.package_mgr.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PackageOperationRequest(
        boolean assumeYes,
        boolean dryRun
) {
    public static PackageOperationRequest defaults() {
        return new PackageOperationRequest(true, false);
    }
}
