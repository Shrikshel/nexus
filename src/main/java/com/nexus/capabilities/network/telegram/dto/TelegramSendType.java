package com.nexus.capabilities.network.telegram.dto;

/**
 * Supported payload types for outbound Telegram dispatch.
 */
public enum TelegramSendType {
    TEXT,
    PHOTO,
    DOCUMENT,
    STICKER,
    AUTO
}
