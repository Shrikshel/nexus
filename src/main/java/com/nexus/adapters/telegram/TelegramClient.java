package com.nexus.adapters.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lightweight, resilient Telegram Bot API client using standard JDK HttpClient.
 * Automatically splits long messages exceeding Telegram's 4096 character limit.
 */
@Component
public class TelegramClient {

    private static final Logger log = LoggerFactory.getLogger(TelegramClient.class);
    public static final int MAX_MESSAGE_CHUNK_SIZE = 4000;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private String apiBaseUrl = "https://api.telegram.org";

    public TelegramClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public TelegramClient(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    /**
     * Sends an HTML-formatted message to the specified chat.
     * Long messages exceeding 4000 characters are automatically split into sequential chunks.
     */
    public boolean sendMessage(String botToken, long chatId, String htmlText) {
        if (botToken == null || botToken.isBlank()) {
            log.warn("Cannot send Telegram message: botToken is not configured");
            return false;
        }

        if (htmlText == null || htmlText.isBlank()) {
            return false;
        }

        List<String> chunks = splitMessage(htmlText, MAX_MESSAGE_CHUNK_SIZE);
        boolean allSuccess = true;

        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            boolean sent = sendSingleMessage(botToken, chatId, chunk);
            if (!sent) {
                allSuccess = false;
            }

            if (chunks.size() > 1 && i < chunks.size() - 1) {
                try {
                    Thread.sleep(50); // Brief delay so Telegram delivers chunks in order
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        return allSuccess;
    }

    private boolean sendSingleMessage(String botToken, long chatId, String chunk) {
        try {
            String url = String.format("%s/bot%s/sendMessage", apiBaseUrl, botToken);
            String payload = objectMapper.writeValueAsString(Map.of(
                    "chat_id", chatId,
                    "text", chunk,
                    "parse_mode", "HTML"
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return true;
            } else {
                log.warn("Telegram sendMessage returned status {}: {}", response.statusCode(), response.body());
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Telegram sendMessage interrupted");
            return false;
        } catch (Exception e) {
            log.error("Telegram sendMessage failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Splits text into chunks of at most maxChunkSize characters, respecting paragraph and line boundaries.
     */
    public static List<String> splitMessage(String text, int maxChunkSize) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        if (text.length() <= maxChunkSize) {
            return List.of(text);
        }

        List<String> chunks = new ArrayList<>();
        String[] paragraphs = text.split("\n\n");
        StringBuilder current = new StringBuilder();

        for (String paragraph : paragraphs) {
            if (paragraph.length() > maxChunkSize) {
                if (current.length() > 0) {
                    chunks.add(current.toString().trim());
                    current.setLength(0);
                }
                String[] lines = paragraph.split("\n");
                for (String line : lines) {
                    if (line.length() > maxChunkSize) {
                        if (current.length() > 0) {
                            chunks.add(current.toString().trim());
                            current.setLength(0);
                        }
                        int start = 0;
                        while (start < line.length()) {
                            int end = Math.min(start + maxChunkSize, line.length());
                            chunks.add(line.substring(start, end));
                            start = end;
                        }
                    } else if (current.length() + line.length() + 1 > maxChunkSize) {
                        chunks.add(current.toString().trim());
                        current.setLength(0);
                        current.append(line);
                    } else {
                        if (current.length() > 0) {
                            current.append("\n");
                        }
                        current.append(line);
                    }
                }
            } else if (current.length() + paragraph.length() + 2 > maxChunkSize) {
                chunks.add(current.toString().trim());
                current.setLength(0);
                current.append(paragraph);
            } else {
                if (current.length() > 0) {
                    current.append("\n\n");
                }
                current.append(paragraph);
            }
        }

        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }

        return chunks;
    }

    public JsonNode getUpdates(String botToken, long offset, int timeoutSecs) throws IOException, InterruptedException {
        String url = String.format("%s/bot%s/getUpdates?offset=%d&timeout=%d", apiBaseUrl, botToken, offset, timeoutSecs);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSecs + 5))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Telegram getUpdates returned status " + response.statusCode() + ": " + response.body());
        }
        return objectMapper.readTree(response.body());
    }
}
