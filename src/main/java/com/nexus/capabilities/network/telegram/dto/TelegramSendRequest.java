package com.nexus.capabilities.network.telegram.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Request payload for sending messages, documents, photos, or stickers via Telegram.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TelegramSendRequest(
        String message,
        String filePath,
        TelegramSendType sendType,
        String caption,
        List<String> recipients,
        String parseMode,
        boolean silent,
        String botToken
) {
    public TelegramSendRequest(String message) {
        this(message, null, TelegramSendType.TEXT, null, null, "HTML", false, null);
    }
}
