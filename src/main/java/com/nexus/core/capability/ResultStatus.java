package com.nexus.core.capability;

/**
 * Execution status states for a capability invocation.
 */
public enum ResultStatus {
    SUCCESS,
    FAILURE,
    CANCELLED,
    TIMEOUT,
    UNAUTHORIZED,
    DEGRADED
}
