package com.nexus.adapters.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

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

    public record TelegramSendResponse(boolean success, Long messageId, String error) {}

    /**
     * Sends an HTML-formatted message to the specified chat ID.
     * Preserved for backward compatibility.
     */
    public boolean sendMessage(String botToken, long chatId, String htmlText) {
        TelegramSendResponse resp = sendMessage(botToken, String.valueOf(chatId), htmlText, "HTML", false);
        return resp.success();
    }

    /**
     * Sends a text message to a specific recipient (numeric ID or channel username @channel).
     * Long messages exceeding 4000 characters are automatically split into sequential chunks.
     */
    public TelegramSendResponse sendMessage(String botToken, String chatId, String text, String parseMode, boolean disableNotification) {
        if (botToken == null || botToken.isBlank()) {
            log.warn("Cannot send Telegram message: botToken is not configured");
            return new TelegramSendResponse(false, null, "Bot token is not configured");
        }

        if (text == null || text.isBlank()) {
            return new TelegramSendResponse(false, null, "Message text is empty");
        }

        List<String> chunks = splitMessage(text, MAX_MESSAGE_CHUNK_SIZE);
        Long lastMessageId = null;

        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            TelegramSendResponse sent = sendSingleMessage(botToken, chatId, chunk, parseMode, disableNotification);
            if (!sent.success()) {
                return sent;
            }
            lastMessageId = sent.messageId();

            if (chunks.size() > 1 && i < chunks.size() - 1) {
                try {
                    Thread.sleep(50); // Brief delay so Telegram delivers chunks in order
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        return new TelegramSendResponse(true, lastMessageId, null);
    }

    private TelegramSendResponse sendSingleMessage(String botToken, String chatId, String chunk, String parseMode, boolean disableNotification) {
        try {
            String url = String.format("%s/bot%s/sendMessage", apiBaseUrl, botToken);
            Map<String, Object> payloadMap = new LinkedHashMap<>();
            payloadMap.put("chat_id", chatId);
            payloadMap.put("text", chunk);
            if (parseMode != null && !parseMode.equalsIgnoreCase("NONE") && !parseMode.equalsIgnoreCase("PLAIN")) {
                payloadMap.put("parse_mode", parseMode);
            }
            if (disableNotification) {
                payloadMap.put("disable_notification", true);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payloadMap)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return parseApiResponse(response.statusCode(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Telegram sendMessage interrupted");
            return new TelegramSendResponse(false, null, "Telegram sendMessage interrupted");
        } catch (Exception e) {
            log.error("Telegram sendMessage failed: {}", e.getMessage());
            return new TelegramSendResponse(false, null, e.getMessage());
        }
    }

    /**
     * Uploads and sends a photo / image file to Telegram.
     */
    public TelegramSendResponse sendPhoto(String botToken, String chatId, Path photoPath, String caption, String parseMode, boolean disableNotification) {
        if (botToken == null || botToken.isBlank()) {
            return new TelegramSendResponse(false, null, "Bot token is not configured");
        }
        if (photoPath == null || !Files.exists(photoPath)) {
            return new TelegramSendResponse(false, null, "Photo file not found: " + photoPath);
        }

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("chat_id", chatId);
        if (caption != null && !caption.isBlank()) fields.put("caption", caption);
        if (parseMode != null && !parseMode.equalsIgnoreCase("NONE") && !parseMode.equalsIgnoreCase("PLAIN")) {
            fields.put("parse_mode", parseMode);
        }
        if (disableNotification) fields.put("disable_notification", "true");

        String url = String.format("%s/bot%s/sendPhoto", apiBaseUrl, botToken);
        return executeMultipart(url, fields, "photo", photoPath, detectMimeType(photoPath));
    }

    /**
     * Uploads and sends a generic document or file to Telegram.
     */
    public TelegramSendResponse sendDocument(String botToken, String chatId, Path documentPath, String caption, String parseMode, boolean disableNotification) {
        if (botToken == null || botToken.isBlank()) {
            return new TelegramSendResponse(false, null, "Bot token is not configured");
        }
        if (documentPath == null || !Files.exists(documentPath)) {
            return new TelegramSendResponse(false, null, "Document file not found: " + documentPath);
        }

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("chat_id", chatId);
        if (caption != null && !caption.isBlank()) fields.put("caption", caption);
        if (parseMode != null && !parseMode.equalsIgnoreCase("NONE") && !parseMode.equalsIgnoreCase("PLAIN")) {
            fields.put("parse_mode", parseMode);
        }
        if (disableNotification) fields.put("disable_notification", "true");

        String url = String.format("%s/bot%s/sendDocument", apiBaseUrl, botToken);
        return executeMultipart(url, fields, "document", documentPath, detectMimeType(documentPath));
    }

    /**
     * Sends a sticker (either a local .webp file or a Telegram sticker file_id) to Telegram.
     */
    public TelegramSendResponse sendSticker(String botToken, String chatId, String stickerFileOrId, boolean disableNotification) {
        if (botToken == null || botToken.isBlank()) {
            return new TelegramSendResponse(false, null, "Bot token is not configured");
        }
        if (stickerFileOrId == null || stickerFileOrId.isBlank()) {
            return new TelegramSendResponse(false, null, "Sticker file or ID not provided");
        }

        Path localPath = Path.of(stickerFileOrId);
        if (Files.exists(localPath)) {
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("chat_id", chatId);
            if (disableNotification) fields.put("disable_notification", "true");
            String url = String.format("%s/bot%s/sendSticker", apiBaseUrl, botToken);
            return executeMultipart(url, fields, "sticker", localPath, detectMimeType(localPath));
        } else {
            try {
                String url = String.format("%s/bot%s/sendSticker", apiBaseUrl, botToken);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("chat_id", chatId);
                body.put("sticker", stickerFileOrId);
                if (disableNotification) body.put("disable_notification", true);

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(15))
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                        .build();

                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                return parseApiResponse(resp.statusCode(), resp.body());
            } catch (Exception e) {
                return new TelegramSendResponse(false, null, e.getMessage());
            }
        }
    }

    private TelegramSendResponse executeMultipart(String url, Map<String, String> fields, String fileField, Path filePath, String mimeType) {
        try {
            String boundary = "----NexusTelegram" + UUID.randomUUID().toString().replace("-", "");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] boundaryBytes = ("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8);

            for (Map.Entry<String, String> entry : fields.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isBlank()) {
                    baos.write(boundaryBytes);
                    String header = "Content-Disposition: form-data; name=\"" + entry.getKey() + "\"\r\n\r\n";
                    baos.write(header.getBytes(StandardCharsets.UTF_8));
                    baos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                    baos.write("\r\n".getBytes(StandardCharsets.UTF_8));
                }
            }

            if (filePath != null && Files.exists(filePath)) {
                baos.write(boundaryBytes);
                String filename = filePath.getFileName().toString();
                String fileHeader = "Content-Disposition: form-data; name=\"" + fileField + "\"; filename=\"" + filename + "\"\r\n" +
                        "Content-Type: " + mimeType + "\r\n\r\n";
                baos.write(fileHeader.getBytes(StandardCharsets.UTF_8));
                Files.copy(filePath, baos);
                baos.write("\r\n".getBytes(StandardCharsets.UTF_8));
            }

            baos.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(baos.toByteArray()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return parseApiResponse(response.statusCode(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new TelegramSendResponse(false, null, "Telegram upload interrupted");
        } catch (Exception e) {
            log.error("Telegram upload failed: {}", e.getMessage());
            return new TelegramSendResponse(false, null, e.getMessage());
        }
    }

    private TelegramSendResponse parseApiResponse(int statusCode, String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            boolean ok = root.path("ok").asBoolean(false);
            if (ok) {
                Long messageId = root.path("result").path("message_id").isIntegralNumber()
                        ? root.path("result").path("message_id").asLong() : null;
                return new TelegramSendResponse(true, messageId, null);
            } else {
                String desc = root.path("description").asText("HTTP " + statusCode);
                return new TelegramSendResponse(false, null, desc);
            }
        } catch (Exception e) {
            if (statusCode == 200) {
                return new TelegramSendResponse(true, null, null);
            }
            return new TelegramSendResponse(false, null, "HTTP " + statusCode + ": " + responseBody);
        }
    }

    public static String detectMimeType(Path path) {
        if (path == null) return "application/octet-stream";
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".webp")) return "image/webp";
        if (name.endsWith(".gif")) return "image/gif";
        if (name.endsWith(".pdf")) return "application/pdf";
        if (name.endsWith(".json")) return "application/json";
        if (name.endsWith(".txt") || name.endsWith(".log")) return "text/plain";
        if (name.endsWith(".zip") || name.endsWith(".tar") || name.endsWith(".gz")) return "application/zip";
        return "application/octet-stream";
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
