package com.nexus.capabilities.network.telegram;

import com.nexus.adapters.telegram.TelegramClient;
import com.nexus.capabilities.network.telegram.dto.TelegramRecipientResult;
import com.nexus.capabilities.network.telegram.dto.TelegramSendRequest;
import com.nexus.capabilities.network.telegram.dto.TelegramSendResult;
import com.nexus.capabilities.network.telegram.dto.TelegramSendType;
import com.nexus.config.NexusProperties;
import com.nexus.core.capability.*;
import com.nexus.core.security.Permission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * Capability providing native outbound Telegram message, document, photo, and sticker delivery.
 */
@Component
public class TelegramSendCapability implements Capability<TelegramSendRequest, TelegramSendResult> {

    public static final String ID = "telegram.send";
    private static final Logger log = LoggerFactory.getLogger(TelegramSendCapability.class);

    private final TelegramClient telegramClient;
    private final NexusProperties properties;

    public TelegramSendCapability(TelegramClient telegramClient, NexusProperties properties) {
        this.telegramClient = Objects.requireNonNull(telegramClient, "TelegramClient must not be null");
        this.properties = Objects.requireNonNull(properties, "NexusProperties must not be null");
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "Send Telegram Notification";
    }

    @Override
    public String getDescription() {
        return "Dispatches text messages, photos, documents, and stickers to configured Telegram recipients or custom chat IDs";
    }

    @Override
    public CapabilityCategory getCategory() {
        return CapabilityCategory.NETWORK;
    }

    @Override
    public Set<Permission> getRequiredPermissions() {
        return Set.of(Permission.WRITE_NETWORK);
    }

    @Override
    public Class<TelegramSendRequest> getInputType() {
        return TelegramSendRequest.class;
    }

    @Override
    public Class<TelegramSendResult> getOutputType() {
        return TelegramSendResult.class;
    }

    @Override
    public CapabilityResult<TelegramSendResult> execute(ExecutionContext context, TelegramSendRequest input) {
        Instant start = Instant.now();

        if (input == null) {
            return CapabilityResult.failure(ID, ErrorCode.INVALID_INPUT, "Request payload must not be null", start);
        }

        // 1. Resolve Bot Token
        String botToken = input.botToken();
        if (botToken == null || botToken.isBlank()) {
            if (properties.getTelegram() != null && properties.getTelegram().getBotToken() != null && !properties.getTelegram().getBotToken().isBlank()) {
                botToken = properties.getTelegram().getBotToken();
            } else {
                botToken = getEnvBotToken();
            }
        }

        if (botToken == null || botToken.isBlank()) {
            return CapabilityResult.failure(ID, ErrorCode.INVALID_INPUT,
                    "Telegram bot token is not configured. Configure it in server.yaml, pass --token, or set TELEGRAM_BOT_TOKEN env variable.", start);
        }

        // 2. Resolve Recipients
        List<String> targetRecipients = new ArrayList<>();
        if (input.recipients() != null && !input.recipients().isEmpty()) {
            for (String r : input.recipients()) {
                if (r != null && !r.isBlank()) targetRecipients.add(r.trim());
            }
        } else if (properties.getTelegram() != null) {
            targetRecipients.addAll(properties.getTelegram().getAllResolvedRecipients());
        }

        if (targetRecipients.isEmpty()) {
            return CapabilityResult.failure(ID, ErrorCode.INVALID_INPUT,
                    "No Telegram recipients specified. Configure 'recipients' in server.yaml or pass -r / --chat-id.", start);
        }

        // 3. Resolve Content & Payload Type
        TelegramSendType type = input.sendType() != null ? input.sendType() : TelegramSendType.AUTO;
        Path localFile = null;
        if (input.filePath() != null && !input.filePath().isBlank()) {
            localFile = Path.of(input.filePath());
            if (type != TelegramSendType.STICKER && !Files.exists(localFile)) {
                return CapabilityResult.failure(ID, ErrorCode.INVALID_INPUT, "Target file does not exist: " + input.filePath(), start);
            }
            if (type == TelegramSendType.AUTO) {
                String mime = TelegramClient.detectMimeType(localFile);
                if (mime.startsWith("image/") && !mime.equals("image/webp")) {
                    type = TelegramSendType.PHOTO;
                } else {
                    type = TelegramSendType.DOCUMENT;
                }
            }
        } else if (type == TelegramSendType.AUTO) {
            type = TelegramSendType.TEXT;
        }

        String message = input.message() != null ? input.message() : "";
        String caption = input.caption() != null && !input.caption().isBlank() ? input.caption() : message;
        String parseMode = input.parseMode() != null ? input.parseMode() : "HTML";
        boolean silent = input.silent();

        // Validate text if message-based
        if (type == TelegramSendType.TEXT && message.isBlank()) {
            return CapabilityResult.failure(ID, ErrorCode.INVALID_INPUT, "Message text must not be empty", start);
        }

        List<TelegramRecipientResult> deliveries = new ArrayList<>();
        int sentCount = 0;
        int failedCount = 0;

        for (String recipient : targetRecipients) {
            TelegramClient.TelegramSendResponse resp;
            try {
                switch (type) {
                    case PHOTO -> resp = telegramClient.sendPhoto(botToken, recipient, localFile, caption, parseMode, silent);
                    case DOCUMENT -> resp = telegramClient.sendDocument(botToken, recipient, localFile, caption, parseMode, silent);
                    case STICKER -> resp = telegramClient.sendSticker(botToken, recipient, input.filePath() != null ? input.filePath() : message, silent);
                    default -> resp = telegramClient.sendMessage(botToken, recipient, message, parseMode, silent);
                }
            } catch (Exception e) {
                log.error("Telegram dispatch error for {}: {}", recipient, e.getMessage());
                resp = new TelegramClient.TelegramSendResponse(false, null, e.getMessage());
            }

            if (resp != null && resp.success()) {
                sentCount++;
                deliveries.add(new TelegramRecipientResult(recipient, true, resp.messageId(), null));
            } else {
                failedCount++;
                String errDesc = (resp != null && resp.error() != null) ? resp.error() : "Unknown error";
                deliveries.add(new TelegramRecipientResult(recipient, false, null, errDesc));
            }
        }

        boolean overallSuccess = failedCount == 0;
        String summary = String.format("Delivered to %d/%d recipient(s)%s",
                sentCount, targetRecipients.size(),
                failedCount > 0 ? " (" + failedCount + " failed)" : "");

        TelegramSendResult result = new TelegramSendResult(
                overallSuccess,
                sentCount,
                failedCount,
                deliveries,
                summary,
                Instant.now()
        );

        if (sentCount == 0 && failedCount > 0) {
            StringBuilder errDetails = new StringBuilder();
            for (TelegramRecipientResult d : deliveries) {
                errDetails.append("\n  • ").append(d.recipient()).append(": ").append(d.errorMessage());
            }
            return CapabilityResult.failure(ID, ErrorCode.INTERNAL_ERROR,
                    "Failed to deliver Telegram message to all recipients. " + summary + errDetails, start);
        }

        return CapabilityResult.success(ID, result, start);
    }

    protected String getEnvBotToken() {
        return System.getenv("TELEGRAM_BOT_TOKEN");
    }
}
