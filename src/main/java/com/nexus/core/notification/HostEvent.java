package com.nexus.core.notification;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Domain event representing a host monitoring alert, state change, or health status.
 */
public record HostEvent(
    String id,
    String eventType,
    Severity severity,
    String title,
    String message,
    Instant timestamp,
    Map<String, Object> metadata
) {
    public static HostEvent info(String eventType, String title, String message) {
        return new HostEvent(UUID.randomUUID().toString(), eventType, Severity.INFO, title, message, Instant.now(), Map.of());
    }

    public static HostEvent warning(String eventType, String title, String message, Map<String, Object> metadata) {
        return new HostEvent(UUID.randomUUID().toString(), eventType, Severity.WARNING, title, message, Instant.now(), metadata);
    }

    public static HostEvent critical(String eventType, String title, String message, Map<String, Object> metadata) {
        return new HostEvent(UUID.randomUUID().toString(), eventType, Severity.CRITICAL, title, message, Instant.now(), metadata);
    }
}
