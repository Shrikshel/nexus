package com.nexus.core.capability;

import com.nexus.core.security.AuditLogger;
import com.nexus.core.security.AuthorizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;

/**
 * Robust execution engine for orchestrating capability execution with
 * authorization checks, timeout management, cancellation support, and structured auditing.
 */
@Service
public class ExecutionEngine {

    private static final Logger log = LoggerFactory.getLogger(ExecutionEngine.class);

    private final CapabilityRegistry registry;
    private final AuthorizationService authorizationService;
    private final AuditLogger auditLogger;
    private final ExecutorService executorService;

    public ExecutionEngine(
            CapabilityRegistry registry,
            AuthorizationService authorizationService,
            AuditLogger auditLogger) {
        this.registry = Objects.requireNonNull(registry, "CapabilityRegistry must not be null");
        this.authorizationService = Objects.requireNonNull(authorizationService, "AuthorizationService must not be null");
        this.auditLogger = Objects.requireNonNull(auditLogger, "AuditLogger must not be null");
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "nexus-exec-" + System.nanoTime());
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Executes a capability looked up by ID.
     */
    public <I, O> CapabilityResult<O> execute(String capabilityId, ExecutionContext context, I input) {
        Instant start = Instant.now();
        Optional<Capability<I, O>> capOpt = registry.getCapability(capabilityId);
        if (capOpt.isEmpty()) {
            CapabilityResult<O> result = CapabilityResult.failure(
                    capabilityId,
                    ErrorCode.CAPABILITY_NOT_FOUND,
                    "Capability not found: " + capabilityId,
                    start
            );
            auditLogger.logExecution(context, result);
            return result;
        }

        return execute(capOpt.get(), context, input);
    }

    /**
     * Executes a specific capability instance with safety boundaries.
     */
    public <I, O> CapabilityResult<O> execute(Capability<I, O> capability, ExecutionContext context, I input) {
        Instant start = Instant.now();
        String capId = capability.getId();

        // 1. Authorization check
        if (!authorizationService.isAuthorized(context.caller(), capability)) {
            String msg = String.format("Subject '%s' lacks required permissions %s for capability '%s'",
                    context.caller() != null ? context.caller().username() : "anonymous",
                    capability.getRequiredPermissions(),
                    capId);
            log.warn("Access denied: {}", msg);
            CapabilityResult<O> result = CapabilityResult.unauthorized(capId, msg, start);
            auditLogger.logExecution(context, result);
            return result;
        }

        // 2. Cancellation check
        if (context.cancellationToken() != null && context.cancellationToken().isCancelled()) {
            CapabilityResult<O> result = CapabilityResult.cancelled(capId, context.cancellationToken().getReason(), start);
            auditLogger.logExecution(context, result);
            return result;
        }

        // 3. Execution with timeout and exception safety
        Duration timeout = context.timeout() != null ? context.timeout() : Duration.ofSeconds(30);
        Future<CapabilityResult<O>> future = executorService.submit(() -> {
            try {
                return capability.execute(context, input);
            } catch (Throwable t) {
                log.error("Unhandled exception executing capability {}: {}", capId, t.getMessage(), t);
                return CapabilityResult.failure(capId, ErrorCode.INTERNAL_ERROR,
                        "Execution failed: " + (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName()),
                        start);
            }
        });

        CapabilityResult<O> result;
        try {
            result = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("Execution timed out for capability {} after {}ms", capId, timeout.toMillis());
            result = CapabilityResult.timeout(capId, start);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            result = CapabilityResult.cancelled(capId, "Thread interrupted", start);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.error("Execution failed for capability {}: {}", capId, cause.getMessage(), cause);
            result = CapabilityResult.failure(capId, ErrorCode.INTERNAL_ERROR, cause.getMessage(), start);
        }

        // 4. Audit logging
        auditLogger.logExecution(context, result);
        return result;
    }
}
