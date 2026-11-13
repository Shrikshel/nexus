package com.nexus.core.capability;

/**
 * Transport/interface source from which a request was triggered.
 */
public enum InvocationSource {
    CLI,
    TELEGRAM,
    IPC,
    REST,
    SCHEDULER,
    INTERNAL
}
