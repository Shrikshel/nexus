package com.nexus.core.capability;

import java.time.Instant;

/**
 * Type-safe, structured result returned by every capability execution.
 */
public record CapabilityResult<T>(
    String capabilityId,
    ResultStatus status,
    T payload,
    CapabilityError error,
    Instant startedAt,
    Instant completedAt,
    long durationMs
) {
    public boolean isSuccess() {
        return status == ResultStatus.SUCCESS;
    }

    public static <T> CapabilityResult<T> success(String capabilityId, T payload, Instant startedAt) {
        Instant now = Instant.now();
        long duration = java.time.Duration.between(startedAt, now).toMillis();
        return new CapabilityResult<>(capabilityId, ResultStatus.SUCCESS, payload, null, startedAt, now, duration);
    }

    public static <T> CapabilityResult<T> failure(String capabilityId, ErrorCode code, String message, Instant startedAt) {
        Instant now = Instant.now();
        long duration = java.time.Duration.between(startedAt, now).toMillis();
        return new CapabilityResult<>(capabilityId, ResultStatus.FAILURE, null, CapabilityError.of(code, message), startedAt, now, duration);
    }

    public static <T> CapabilityResult<T> failure(String capabilityId, ErrorCode code, String message, String details, String remediation, Instant startedAt) {
        Instant now = Instant.now();
        long duration = java.time.Duration.between(startedAt, now).toMillis();
        return new CapabilityResult<>(capabilityId, ResultStatus.FAILURE, null, CapabilityError.of(code, message, details, remediation), startedAt, now, duration);
    }

    public static <T> CapabilityResult<T> timeout(String capabilityId, Instant startedAt) {
        Instant now = Instant.now();
        long duration = java.time.Duration.between(startedAt, now).toMillis();
        return new CapabilityResult<>(capabilityId, ResultStatus.TIMEOUT, null,
                CapabilityError.of(ErrorCode.TIMEOUT, "Capability execution timed out"), startedAt, now, duration);
    }

    public static <T> CapabilityResult<T> cancelled(String capabilityId, String reason, Instant startedAt) {
        Instant now = Instant.now();
        long duration = java.time.Duration.between(startedAt, now).toMillis();
        return new CapabilityResult<>(capabilityId, ResultStatus.CANCELLED, null,
                CapabilityError.of(ErrorCode.CANCELLED, "Operation cancelled: " + reason), startedAt, now, duration);
    }

    public static <T> CapabilityResult<T> unauthorized(String capabilityId, String reason, Instant startedAt) {
        Instant now = Instant.now();
        long duration = java.time.Duration.between(startedAt, now).toMillis();
        return new CapabilityResult<>(capabilityId, ResultStatus.UNAUTHORIZED, null,
                CapabilityError.of(ErrorCode.FORBIDDEN, reason), startedAt, now, duration);
    }
}
