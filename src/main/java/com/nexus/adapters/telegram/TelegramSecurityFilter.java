package com.nexus.adapters.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Enforces sender and chat allowlisting plus token-bucket rate limiting for Telegram requests.
 */
@Component
public class TelegramSecurityFilter {

    private static final Logger log = LoggerFactory.getLogger(TelegramSecurityFilter.class);

    private final Map<Long, RateBucket> rateBuckets = new ConcurrentHashMap<>();

    private static class RateBucket {
        final AtomicInteger tokens;
        volatile long lastRefillEpochSec;

        RateBucket(int capacity) {
            this.tokens = new AtomicInteger(capacity);
            this.lastRefillEpochSec = System.currentTimeMillis() / 1000;
        }

        boolean tryConsume(int maxTokens, int refillRatePerMinute) {
            long nowSec = System.currentTimeMillis() / 1000;
            long elapsedSec = nowSec - lastRefillEpochSec;
            if (elapsedSec > 0) {
                int refill = (int) (elapsedSec * refillRatePerMinute / 60);
                if (refill > 0) {
                    tokens.updateAndGet(current -> Math.min(maxTokens, current + refill));
                    lastRefillEpochSec = nowSec;
                }
            }
            return tokens.getAndUpdate(current -> current > 0 ? current - 1 : 0) > 0;
        }
    }

    public boolean isChatAllowed(Long chatId, Long configuredChatId) {
        if (chatId == null || configuredChatId == null) {
            return false;
        }
        return chatId.equals(configuredChatId);
    }

    public boolean isSenderAllowed(Long senderId, Set<Long> allowedSenders) {
        if (senderId == null) {
            return false;
        }
        if (allowedSenders == null || allowedSenders.isEmpty()) {
            return true; // If no sender allowlist specified, chat allowlist governs
        }
        return allowedSenders.contains(senderId);
    }

    public boolean isRateLimited(Long senderId, int maxTokensPerMinute) {
        if (senderId == null) {
            return true;
        }
        RateBucket bucket = rateBuckets.computeIfAbsent(senderId, id -> new RateBucket(maxTokensPerMinute));
        boolean allowed = bucket.tryConsume(maxTokensPerMinute, maxTokensPerMinute);
        if (!allowed) {
            log.warn("Rate limit exceeded for Telegram sender ID: {}", senderId);
        }
        return !allowed;
    }
}
