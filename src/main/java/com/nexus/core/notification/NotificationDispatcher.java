package com.nexus.core.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Central dispatcher routing host events to all configured outbound notification channels asynchronously.
 */
@Service
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final List<OutboundNotificationChannel> channels;
    private final ExecutorService dispatcherExecutor;

    public NotificationDispatcher(List<OutboundNotificationChannel> channels) {
        this.channels = channels != null ? Collections.unmodifiableList(channels) : Collections.emptyList();
        this.dispatcherExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "nexus-notif-dispatcher");
            t.setDaemon(true);
            return t;
        });
        log.info("Initialized NotificationDispatcher with {} notification channels", this.channels.size());
    }

    public void dispatch(HostEvent event) {
        Objects.requireNonNull(event, "HostEvent must not be null");
        log.debug("Dispatching host event [{}] severity={}: {}", event.eventType(), event.severity(), event.title());

        dispatcherExecutor.submit(() -> {
            for (OutboundNotificationChannel channel : channels) {
                if (channel.isEnabled()) {
                    try {
                        channel.sendNotification(event);
                    } catch (Exception e) {
                        log.error("Failed to send notification via channel [{}]: {}", channel.getChannelName(), e.getMessage(), e);
                    }
                }
            }
        });
    }

    public List<OutboundNotificationChannel> getChannels() {
        return channels;
    }
}
