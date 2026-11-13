package com.nexus.capabilities.system.dto;

/**
 * Parameters for requesting a privileged host reboot or poweroff operation.
 */
public record PowerOperationRequest(
    boolean confirm,
    int delaySeconds,
    String reason
) {
    public static PowerOperationRequest confirmed() {
        return new PowerOperationRequest(true, 0, "Nexus operator command");
    }

    public static PowerOperationRequest unconfirmed() {
        return new PowerOperationRequest(false, 0, null);
    }
}
