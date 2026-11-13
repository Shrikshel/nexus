package com.nexus.core.capability;

import com.nexus.core.security.Subject;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Execution context carrying invocation metadata, security subject, cancellation tokens, and timeouts.
 */
public record ExecutionContext(
    String invocationId,
    InvocationSource source,
    Subject caller,
    CancellationToken cancellationToken,
    Duration timeout,
    Map<String, Object> attributes
) {
    public static ExecutionContext create(InvocationSource source, Subject caller) {
        return new ExecutionContext(
                UUID.randomUUID().toString(),
                source,
                caller,
                CancellationToken.none(),
                Duration.ofSeconds(30),
                Collections.emptyMap()
        );
    }

    public static ExecutionContext create(InvocationSource source, Subject caller, Duration timeout) {
        return new ExecutionContext(
                UUID.randomUUID().toString(),
                source,
                caller,
                CancellationToken.none(),
                timeout,
                Collections.emptyMap()
        );
    }

    public ExecutionContext withAttribute(String key, Object value) {
        Map<String, Object> newAttrs = new HashMap<>(attributes != null ? attributes : Collections.emptyMap());
        newAttrs.put(key, value);
        return new ExecutionContext(invocationId, source, caller, cancellationToken, timeout, Collections.unmodifiableMap(newAttrs));
    }
}
