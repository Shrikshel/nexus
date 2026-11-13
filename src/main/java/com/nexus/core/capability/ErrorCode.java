package com.nexus.core.capability;

/**
 * Standard error codes for capability failures across all interfaces.
 */
public enum ErrorCode {
    INTERNAL_ERROR("ERR_SYS_001", "Internal processing error"),
    TIMEOUT("ERR_SYS_002", "Execution timed out"),
    CANCELLED("ERR_SYS_003", "Execution cancelled by caller"),
    UNAUTHORIZED("ERR_SEC_001", "Unauthorized execution"),
    FORBIDDEN("ERR_SEC_002", "Permission denied for operation"),
    RATE_LIMITED("ERR_SEC_003", "Request rate limit exceeded"),
    INVALID_INPUT("ERR_REQ_001", "Invalid request parameters"),
    CAPABILITY_NOT_FOUND("ERR_CAP_001", "Capability does not exist"),
    PLATFORM_UNSUPPORTED("ERR_OS_001", "Capability not supported on current platform"),
    PRIVILEGE_REQUIRED("ERR_OS_002", "Elevated privilege required for operation"),
    DEVICE_ERROR("ERR_HW_001", "Hardware device communication failed");

    private final String code;
    private final String defaultMessage;

    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
