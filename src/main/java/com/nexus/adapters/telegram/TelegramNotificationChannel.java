package com.nexus.adapters.telegram;

import com.nexus.config.NexusProperties;
import com.nexus.core.notification.HostEvent;
import com.nexus.core.notification.OutboundNotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Outbound notification adapter delivering alerts to Telegram.
 */
@Component
public class TelegramNotificationChannel implements OutboundNotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotificationChannel.class);

    private final NexusProperties properties;
    private final TelegramClient telegramClient;
    private final TelegramFormatter formatter;

    public TelegramNotificationChannel(
            NexusProperties properties,
            TelegramClient telegramClient,
            TelegramFormatter formatter) {
        this.properties = properties;
        this.telegramClient = telegramClient;
        this.formatter = formatter;
    }

    @Override
    public String getChannelName() {
        return "Telegram";
    }

    @Override
    public boolean isEnabled() {
        NexusProperties.TelegramConfig config = properties.getTelegram();
        return config != null &&
               config.isEnabled() &&
               config.getBotToken() != null &&
               !config.getBotToken().isBlank() &&
               config.getAllowedChatId() != null;
    }

    @Override
    public void sendNotification(HostEvent event) {
        if (!isEnabled()) {
            return;
        }

        NexusProperties.TelegramConfig config = properties.getTelegram();
        String message = formatter.formatEvent(event);
        boolean success = telegramClient.sendMessage(config.getBotToken(), config.getAllowedChatId(), message);
        if (success) {
            log.info("Dispatched Telegram alert [{}] to chat {}", event.title(), config.getAllowedChatId());
        } else {
            log.warn("Failed to dispatch Telegram alert [{}] to chat {}", event.title(), config.getAllowedChatId());
        }
    }
}
