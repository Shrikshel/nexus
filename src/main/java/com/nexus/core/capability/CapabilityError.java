package com.nexus.core.capability;

/**
 * Structured error details returned upon capability failure.
 */
public record CapabilityError(
    ErrorCode code,
    String message,
    String details,
    String remediation
) {
    public static CapabilityError of(ErrorCode code, String message) {
        return new CapabilityError(code, message, null, null);
    }

    public static CapabilityError of(ErrorCode code, String message, String details, String remediation) {
        return new CapabilityError(code, message, details, remediation);
    }
}
