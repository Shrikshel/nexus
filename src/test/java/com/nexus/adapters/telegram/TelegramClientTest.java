package com.nexus.adapters.telegram;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramClientTest {

    @Test
    @DisplayName("Short message is not split")
    void testShortMessageNotSplit() {
        String msg = "Hello from Nexus host management agent!";
        List<String> chunks = TelegramClient.splitMessage(msg, 4000);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).isEqualTo(msg);
    }

    @Test
    @DisplayName("Long message exceeding max size is split into chunks under max size")
    void testLongMessageSplit() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            sb.append("• Line ").append(i).append(": process description data payload with extra text\n");
        }
        String longText = sb.toString();
        assertThat(longText.length()).isGreaterThan(4000);

        List<String> chunks = TelegramClient.splitMessage(longText, 4000);
        assertThat(chunks.size()).isGreaterThan(1);

        for (String chunk : chunks) {
            assertThat(chunk.length()).isLessThanOrEqualTo(4000);
            assertThat(chunk).isNotBlank();
        }

        // Verify content integrity
        String recombined = String.join("\n", chunks);
        assertThat(recombined).contains("Line 0");
        assertThat(recombined).contains("Line 199");
    }

    @Test
    @DisplayName("Empty or null message returns empty list")
    void testEmptyMessage() {
        assertThat(TelegramClient.splitMessage("", 4000)).isEmpty();
        assertThat(TelegramClient.splitMessage(null, 4000)).isEmpty();
        assertThat(TelegramClient.splitMessage("   ", 4000)).isEmpty();
    }

    @Test
    @DisplayName("MIME type detection identifies standard file extensions correctly")
    void testDetectMimeType() {
        assertThat(TelegramClient.detectMimeType(java.nio.file.Path.of("photo.jpg"))).isEqualTo("image/jpeg");
        assertThat(TelegramClient.detectMimeType(java.nio.file.Path.of("image.PNG"))).isEqualTo("image/png");
        assertThat(TelegramClient.detectMimeType(java.nio.file.Path.of("document.pdf"))).isEqualTo("application/pdf");
        assertThat(TelegramClient.detectMimeType(java.nio.file.Path.of("data.json"))).isEqualTo("application/json");
        assertThat(TelegramClient.detectMimeType(java.nio.file.Path.of("archive.zip"))).isEqualTo("application/zip");
        assertThat(TelegramClient.detectMimeType(java.nio.file.Path.of("unknown.xyz"))).isEqualTo("application/octet-stream");
        assertThat(TelegramClient.detectMimeType(null)).isEqualTo("application/octet-stream");
    }
}
