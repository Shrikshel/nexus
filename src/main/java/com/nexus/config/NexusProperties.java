package com.nexus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Strong-typed configuration properties for Nexus.
 */
@Configuration(proxyBeanMethods = false)
@ConfigurationProperties(prefix = "nexus")
public class NexusProperties {

    private String mode = "cli";
    private Duration defaultTimeout = Duration.ofSeconds(30);
    private boolean auditEnabled = true;

    private TelegramConfig telegram = new TelegramConfig();

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public Duration getDefaultTimeout() {
        return defaultTimeout;
    }

    public void setDefaultTimeout(Duration defaultTimeout) {
        this.defaultTimeout = defaultTimeout;
    }

    public boolean isAuditEnabled() {
        return auditEnabled;
    }

    public void setAuditEnabled(boolean auditEnabled) {
        this.auditEnabled = auditEnabled;
    }

    public TelegramConfig getTelegram() {
        return telegram;
    }

    public void setTelegram(TelegramConfig telegram) {
        this.telegram = telegram;
    }

    public static class TelegramConfig {
        private boolean enabled = false;
        private String botToken;
        private Long allowedChatId;
        private java.util.List<String> recipients = new java.util.ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBotToken() {
            return botToken;
        }

        public void setBotToken(String botToken) {
            this.botToken = botToken;
        }

        public Long getAllowedChatId() {
            return allowedChatId;
        }

        public void setAllowedChatId(Long allowedChatId) {
            this.allowedChatId = allowedChatId;
        }

        public java.util.List<String> getRecipients() {
            return recipients;
        }

        public void setRecipients(java.util.List<String> recipients) {
            this.recipients = recipients != null ? recipients : new java.util.ArrayList<>();
        }

        public java.util.List<String> getAllResolvedRecipients() {
            java.util.Set<String> set = new java.util.LinkedHashSet<>();
            if (allowedChatId != null && allowedChatId != 0) {
                set.add(String.valueOf(allowedChatId));
            }
            if (recipients != null) {
                for (String r : recipients) {
                    if (r != null && !r.isBlank()) {
                        set.add(r.trim());
                    }
                }
            }
            return new java.util.ArrayList<>(set);
        }
    }
}
