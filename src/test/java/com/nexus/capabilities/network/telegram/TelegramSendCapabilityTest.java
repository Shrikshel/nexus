package com.nexus.capabilities.network.telegram;

import com.nexus.adapters.telegram.TelegramClient;
import com.nexus.capabilities.network.telegram.dto.TelegramSendRequest;
import com.nexus.capabilities.network.telegram.dto.TelegramSendResult;
import com.nexus.capabilities.network.telegram.dto.TelegramSendType;
import com.nexus.config.NexusProperties;
import com.nexus.core.capability.CapabilityResult;
import com.nexus.core.capability.ExecutionContext;
import com.nexus.core.capability.InvocationSource;
import com.nexus.core.security.Subject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class TelegramSendCapabilityTest {

    private TelegramClient telegramClient;
    private NexusProperties properties;
    private NexusProperties.TelegramConfig telegramProperties;
    private TelegramSendCapability capability;
    private ExecutionContext context;

    @BeforeEach
    void setUp() {
        telegramClient = Mockito.mock(TelegramClient.class);
        properties = Mockito.mock(NexusProperties.class);
        telegramProperties = Mockito.mock(NexusProperties.TelegramConfig.class);

        when(properties.getTelegram()).thenReturn(telegramProperties);
        when(telegramProperties.getBotToken()).thenReturn("123456:TEST_TOKEN");
        when(telegramProperties.getAllResolvedRecipients()).thenReturn(List.of("111111", "222222"));

        capability = new TelegramSendCapability(telegramClient, properties);
        context = ExecutionContext.create(InvocationSource.CLI, Subject.localCli(), Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("Send text message to single recipient succeeds")
    void testSendTextMessageSuccess() {
        when(telegramClient.sendMessage(eq("123456:TEST_TOKEN"), eq("999999"), eq("Hello World"), anyString(), anyBoolean()))
                .thenReturn(new TelegramClient.TelegramSendResponse(true, 101L, null));

        TelegramSendRequest req = new TelegramSendRequest(
                "Hello World",
                null,
                TelegramSendType.TEXT,
                null,
                List.of("999999"),
                "HTML",
                false,
                null
        );

        CapabilityResult<TelegramSendResult> result = capability.execute(context, req);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.payload()).isNotNull();
        assertThat(result.payload().sentCount()).isEqualTo(1);
        assertThat(result.payload().failedCount()).isEqualTo(0);
        assertThat(result.payload().deliveries()).hasSize(1);
        assertThat(result.payload().deliveries().get(0).messageId()).isEqualTo(101L);
    }

    @Test
    @DisplayName("Send text message with mixed results across multiple recipients")
    void testSendMultipleRecipientsMixedResults() {
        when(telegramClient.sendMessage(anyString(), eq("111111"), anyString(), anyString(), anyBoolean()))
                .thenReturn(new TelegramClient.TelegramSendResponse(true, 101L, null));
        when(telegramClient.sendMessage(anyString(), eq("222222"), anyString(), anyString(), anyBoolean()))
                .thenReturn(new TelegramClient.TelegramSendResponse(false, null, "Chat not found"));

        TelegramSendRequest req = new TelegramSendRequest(
                "Backup finished",
                null,
                TelegramSendType.TEXT,
                null,
                List.of("111111", "222222"),
                "HTML",
                false,
                null
        );

        CapabilityResult<TelegramSendResult> result = capability.execute(context, req);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.payload().sentCount()).isEqualTo(1);
        assertThat(result.payload().failedCount()).isEqualTo(1);
        assertThat(result.payload().success()).isFalse();
    }

    @Test
    @DisplayName("Send text message fails when all recipients fail")
    void testSendAllRecipientsFail() {
        when(telegramClient.sendMessage(anyString(), anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(new TelegramClient.TelegramSendResponse(false, null, "Unauthorized: bot was blocked"));

        TelegramSendRequest req = new TelegramSendRequest(
                "Alert!",
                null,
                TelegramSendType.TEXT,
                null,
                List.of("111111"),
                "HTML",
                false,
                null
        );

        CapabilityResult<TelegramSendResult> result = capability.execute(context, req);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.error().message()).contains("Failed to deliver Telegram message");
        assertThat(result.error().message()).contains("Unauthorized: bot was blocked");
    }

    @Test
    @DisplayName("Send photo invokes sendPhoto on TelegramClient")
    void testSendPhoto(@TempDir Path tempDir) throws IOException {
        Path imageFile = tempDir.resolve("test.png");
        Files.writeString(imageFile, "fake image bytes");

        when(telegramClient.sendPhoto(anyString(), eq("111111"), eq(imageFile), any(), any(), anyBoolean()))
                .thenReturn(new TelegramClient.TelegramSendResponse(true, 202L, null));

        TelegramSendRequest req = new TelegramSendRequest(
                null,
                imageFile.toString(),
                TelegramSendType.PHOTO,
                "Snapshot",
                List.of("111111"),
                "HTML",
                true,
                null
        );

        CapabilityResult<TelegramSendResult> result = capability.execute(context, req);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.payload().sentCount()).isEqualTo(1);
        assertThat(result.payload().deliveries().get(0).messageId()).isEqualTo(202L);
    }

    @Test
    @DisplayName("Send document invokes sendDocument on TelegramClient")
    void testSendDocument(@TempDir Path tempDir) throws IOException {
        Path docFile = tempDir.resolve("report.pdf");
        Files.writeString(docFile, "%PDF-1.4 fake pdf");

        when(telegramClient.sendDocument(anyString(), eq("111111"), eq(docFile), any(), any(), anyBoolean()))
                .thenReturn(new TelegramClient.TelegramSendResponse(true, 303L, null));

        TelegramSendRequest req = new TelegramSendRequest(
                null,
                docFile.toString(),
                TelegramSendType.DOCUMENT,
                "PDF Report",
                List.of("111111"),
                "HTML",
                false,
                null
        );

        CapabilityResult<TelegramSendResult> result = capability.execute(context, req);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.payload().sentCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Send sticker invokes sendSticker on TelegramClient")
    void testSendSticker() {
        when(telegramClient.sendSticker(anyString(), eq("111111"), eq("sticker_file_id_xyz"), anyBoolean()))
                .thenReturn(new TelegramClient.TelegramSendResponse(true, 404L, null));

        TelegramSendRequest req = new TelegramSendRequest(
                "sticker_file_id_xyz",
                "sticker_file_id_xyz",
                TelegramSendType.STICKER,
                null,
                List.of("111111"),
                null,
                false,
                null
        );

        CapabilityResult<TelegramSendResult> result = capability.execute(context, req);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.payload().sentCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Fallback to server.yaml recipients and token when not specified")
    void testFallbackToConfigRecipientsAndToken() {
        when(telegramClient.sendMessage(eq("123456:TEST_TOKEN"), anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(new TelegramClient.TelegramSendResponse(true, 505L, null));

        TelegramSendRequest req = new TelegramSendRequest(
                "Auto fallback message",
                null,
                TelegramSendType.TEXT,
                null,
                null, // No recipients in request
                null,
                false,
                null // No token in request
        );

        CapabilityResult<TelegramSendResult> result = capability.execute(context, req);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.payload().sentCount()).isEqualTo(2); // Two resolved recipients from server.yaml
    }

    @Test
    @DisplayName("Fails when no bot token is configured anywhere")
    void testMissingTokenFails() {
        when(telegramProperties.getBotToken()).thenReturn(null);

        TelegramSendCapability capabilityNoToken = new TelegramSendCapability(telegramClient, properties) {
            @Override
            protected String getEnvBotToken() {
                return null;
            }
        };

        TelegramSendRequest req = new TelegramSendRequest(
                "Hello",
                null,
                TelegramSendType.TEXT,
                null,
                List.of("111111"),
                null,
                false,
                null
        );

        CapabilityResult<TelegramSendResult> result = capabilityNoToken.execute(context, req);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.error().message()).contains("Telegram bot token is not configured");
    }

    @Test
    @DisplayName("Fails when no recipients are specified")
    void testMissingRecipientsFails() {
        when(telegramProperties.getAllResolvedRecipients()).thenReturn(List.of());

        TelegramSendRequest req = new TelegramSendRequest(
                "Hello",
                null,
                TelegramSendType.TEXT,
                null,
                List.of(),
                null,
                false,
                null
        );

        CapabilityResult<TelegramSendResult> result = capability.execute(context, req);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.error().message()).contains("No Telegram recipients specified");
    }

    @Test
    @DisplayName("Fails when target file does not exist")
    void testNonExistentFileFails() {
        TelegramSendRequest req = new TelegramSendRequest(
                null,
                "/path/to/nonexistent/file.png",
                TelegramSendType.PHOTO,
                null,
                List.of("111111"),
                null,
                false,
                null
        );

        CapabilityResult<TelegramSendResult> result = capability.execute(context, req);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.error().message()).contains("Target file does not exist");
    }
}
