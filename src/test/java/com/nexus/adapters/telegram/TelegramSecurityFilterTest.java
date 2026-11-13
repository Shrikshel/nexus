package com.nexus.adapters.telegram;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramSecurityFilterTest {

    private TelegramSecurityFilter filter;

    @BeforeEach
    void setup() {
        filter = new TelegramSecurityFilter();
    }

    @Test
    @DisplayName("Allowlist accepts matching chatId and rejects unauthorized chatId")
    void testChatAllowlist() {
        Long configuredChat = 123456789L;

        assertThat(filter.isChatAllowed(123456789L, configuredChat)).isTrue();
        assertThat(filter.isChatAllowed(987654321L, configuredChat)).isFalse();
        assertThat(filter.isChatAllowed(null, configuredChat)).isFalse();
    }

    @Test
    @DisplayName("Sender allowlist accepts matching sender or all when allowlist empty")
    void testSenderAllowlist() {
        Set<Long> allowedSenders = Set.of(100L, 200L);

        assertThat(filter.isSenderAllowed(100L, allowedSenders)).isTrue();
        assertThat(filter.isSenderAllowed(300L, allowedSenders)).isFalse();
        assertThat(filter.isSenderAllowed(300L, Set.of())).isTrue(); // Empty allowlist allows any sender in allowed chat
    }

    @Test
    @DisplayName("Rate limiter allows requests within capacity and blocks over-limit requests")
    void testRateLimiter() {
        Long senderId = 555L;
        int maxTokens = 5;

        // First 5 requests should pass
        for (int i = 0; i < maxTokens; i++) {
            assertThat(filter.isRateLimited(senderId, maxTokens))
                    .as("Request " + (i + 1) + " should not be rate limited")
                    .isFalse();
        }

        // 6th request must be rate limited
        assertThat(filter.isRateLimited(senderId, maxTokens))
                .as("Request 6 should be rate limited")
                .isTrue();
    }
}
