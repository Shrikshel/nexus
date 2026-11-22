package com.nexus.adapters.cli.commands;

import com.nexus.adapters.cli.CliExecutionBridge;
import com.nexus.capabilities.network.telegram.TelegramSendCapability;
import com.nexus.capabilities.network.telegram.dto.TelegramSendRequest;
import com.nexus.capabilities.network.telegram.dto.TelegramSendType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramCommandTest {

    private CliExecutionBridge bridge;
    private CommandLine commandLine;

    @BeforeEach
    void setUp() {
        bridge = Mockito.mock(CliExecutionBridge.class);
        TelegramCommand telegramCommand = new TelegramCommand(bridge);
        commandLine = new CommandLine(telegramCommand);
    }

    @Test
    @DisplayName("Root telegram command prints usage and returns 0")
    void testRootCommandHelp() {
        int exitCode = commandLine.execute();
        assertThat(exitCode).isEqualTo(0);
    }

    @Test
    @DisplayName("telegram send message delegates with correct text and defaults")
    void testSendMessage() {
        when(bridge.execute(eq(TelegramSendCapability.ID), any(TelegramSendRequest.class), anyBoolean(), any(), any()))
                .thenReturn(0);

        int exitCode = commandLine.execute("send", "Server backup completed successfully");
        assertThat(exitCode).isEqualTo(0);

        ArgumentCaptor<TelegramSendRequest> captor = ArgumentCaptor.forClass(TelegramSendRequest.class);
        verify(bridge).execute(eq(TelegramSendCapability.ID), captor.capture(), eq(false), any(), any());

        TelegramSendRequest req = captor.getValue();
        assertThat(req.message()).isEqualTo("Server backup completed successfully");
        assertThat(req.sendType()).isEqualTo(TelegramSendType.AUTO);
    }

    @Test
    @DisplayName("telegram send with recipient options and silent flag")
    void testSendWithOptions() {
        when(bridge.execute(eq(TelegramSendCapability.ID), any(TelegramSendRequest.class), anyBoolean(), any(), any()))
                .thenReturn(0);

        int exitCode = commandLine.execute("send", "Urgent Alert", "-r", "123456", "-r", "@ops_channel", "-s", "--format", "Markdown");
        assertThat(exitCode).isEqualTo(0);

        ArgumentCaptor<TelegramSendRequest> captor = ArgumentCaptor.forClass(TelegramSendRequest.class);
        verify(bridge).execute(eq(TelegramSendCapability.ID), captor.capture(), eq(false), any(), any());

        TelegramSendRequest req = captor.getValue();
        assertThat(req.message()).isEqualTo("Urgent Alert");
        assertThat(req.recipients()).containsExactly("123456", "@ops_channel");
        assertThat(req.silent()).isTrue();
        assertThat(req.parseMode()).isEqualTo("Markdown");
    }

    @Test
    @DisplayName("telegram send file subcommand routes to document type")
    void testSendFileSubcommand() {
        when(bridge.execute(eq(TelegramSendCapability.ID), any(TelegramSendRequest.class), anyBoolean(), any(), any()))
                .thenReturn(0);

        int exitCode = commandLine.execute("send", "file", "/tmp/report.pdf", "--caption", "PDF Report", "-r", "999999");
        assertThat(exitCode).isEqualTo(0);

        ArgumentCaptor<TelegramSendRequest> captor = ArgumentCaptor.forClass(TelegramSendRequest.class);
        verify(bridge).execute(eq(TelegramSendCapability.ID), captor.capture(), eq(false), any(), any());

        TelegramSendRequest req = captor.getValue();
        assertThat(req.filePath()).isEqualTo("/tmp/report.pdf");
        assertThat(req.caption()).isEqualTo("PDF Report");
        assertThat(req.sendType()).isEqualTo(TelegramSendType.DOCUMENT);
        assertThat(req.recipients()).containsExactly("999999");
    }

    @Test
    @DisplayName("telegram send photo subcommand routes to photo type")
    void testSendPhotoSubcommand() {
        when(bridge.execute(eq(TelegramSendCapability.ID), any(TelegramSendRequest.class), anyBoolean(), any(), any()))
                .thenReturn(0);

        int exitCode = commandLine.execute("send", "photo", "/tmp/screenshot.png", "--caption", "Server graph");
        assertThat(exitCode).isEqualTo(0);

        ArgumentCaptor<TelegramSendRequest> captor = ArgumentCaptor.forClass(TelegramSendRequest.class);
        verify(bridge).execute(eq(TelegramSendCapability.ID), captor.capture(), eq(false), any(), any());

        TelegramSendRequest req = captor.getValue();
        assertThat(req.filePath()).isEqualTo("/tmp/screenshot.png");
        assertThat(req.caption()).isEqualTo("Server graph");
        assertThat(req.sendType()).isEqualTo(TelegramSendType.PHOTO);
    }

    @Test
    @DisplayName("telegram send sticker subcommand routes to sticker type")
    void testSendStickerSubcommand() {
        when(bridge.execute(eq(TelegramSendCapability.ID), any(TelegramSendRequest.class), anyBoolean(), any(), any()))
                .thenReturn(0);

        int exitCode = commandLine.execute("send", "sticker", "CAACAgIAAxkBA...");
        assertThat(exitCode).isEqualTo(0);

        ArgumentCaptor<TelegramSendRequest> captor = ArgumentCaptor.forClass(TelegramSendRequest.class);
        verify(bridge).execute(eq(TelegramSendCapability.ID), captor.capture(), eq(false), any(), any());

        TelegramSendRequest req = captor.getValue();
        assertThat(req.filePath()).isEqualTo("CAACAgIAAxkBA...");
        assertThat(req.sendType()).isEqualTo(TelegramSendType.STICKER);
    }

    @Test
    @DisplayName("telegram send with --file flag routes file upload")
    void testSendWithFileFlag() {
        when(bridge.execute(eq(TelegramSendCapability.ID), any(TelegramSendRequest.class), anyBoolean(), any(), any()))
                .thenReturn(0);

        int exitCode = commandLine.execute("send", "-f", "/var/log/nexus.log", "--caption", "Logs");
        assertThat(exitCode).isEqualTo(0);

        ArgumentCaptor<TelegramSendRequest> captor = ArgumentCaptor.forClass(TelegramSendRequest.class);
        verify(bridge).execute(eq(TelegramSendCapability.ID), captor.capture(), eq(false), any(), any());

        TelegramSendRequest req = captor.getValue();
        assertThat(req.filePath()).isEqualTo("/var/log/nexus.log");
        assertThat(req.caption()).isEqualTo("Logs");
        assertThat(req.sendType()).isEqualTo(TelegramSendType.DOCUMENT);
    }

    @Test
    @DisplayName("telegram send without arguments or input returns exit code 1")
    void testSendMissingInputFails() {
        int exitCode = commandLine.execute("send");
        assertThat(exitCode).isEqualTo(1);
    }
}
