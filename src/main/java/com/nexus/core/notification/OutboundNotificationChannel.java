package com.nexus.core.notification;

/**
 * Interface for delivery channels capable of sending outbound alerts (Telegram, REST, Email).
 */
public interface OutboundNotificationChannel {

    String getChannelName();

    boolean isEnabled();

    void sendNotification(HostEvent event);
}
