package com.nexus.capabilities.network.telegram.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Result of delivering a Telegram payload to an individual recipient.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TelegramRecipientResult(
        String recipient,
        boolean success,
        Long messageId,
        String errorMessage
) {}
