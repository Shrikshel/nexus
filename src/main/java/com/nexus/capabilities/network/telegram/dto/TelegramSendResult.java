package com.nexus.capabilities.network.telegram.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Aggregated result of delivering an outbound Telegram payload across all recipients.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TelegramSendResult(
        boolean success,
        int sentCount,
        int failedCount,
        List<TelegramRecipientResult> deliveries,
        String message,
        Instant timestamp
) {}
