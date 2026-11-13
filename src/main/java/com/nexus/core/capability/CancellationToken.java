package com.nexus.core.capability;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread-safe cancellation token allowing cooperative cancellation of long-running operations.
 */
public class CancellationToken {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile String reason;

    public static CancellationToken none() {
        return new CancellationToken();
    }

    public void cancel(String reason) {
        this.reason = reason;
        this.cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public String getReason() {
        return reason;
    }

    public void throwIfCancelled() throws InterruptedException {
        if (isCancelled()) {
            throw new InterruptedException("Operation was cancelled: " + reason);
        }
    }
}
