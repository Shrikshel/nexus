package com.nexus.core.capability;

import com.nexus.core.security.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionEngineTest {

    private CapabilityRegistry registry;
    private AuthorizationService authorizationService;
    private AuditLogger auditLogger;
    private ExecutionEngine engine;

    private static class SimpleCapability implements Capability<String, String> {
        @Override public String getId() { return "test.echo"; }
        @Override public String getDisplayName() { return "Echo"; }
        @Override public String getDescription() { return "Echoes input"; }
        @Override public CapabilityCategory getCategory() { return CapabilityCategory.SYSTEM; }
        @Override public Set<Permission> getRequiredPermissions() { return Set.of(Permission.READ_SYSTEM); }
        @Override public Class<String> getInputType() { return String.class; }
        @Override public Class<String> getOutputType() { return String.class; }
        @Override
        public CapabilityResult<String> execute(ExecutionContext context, String input) {
            return CapabilityResult.success(getId(), "ECHO:" + input, Instant.now());
        }
    }

    private static class SlowCapability implements Capability<Void, String> {
        @Override public String getId() { return "test.slow"; }
        @Override public String getDisplayName() { return "Slow"; }
        @Override public String getDescription() { return "Sleeps longer than timeout"; }
        @Override public CapabilityCategory getCategory() { return CapabilityCategory.SYSTEM; }
        @Override public Set<Permission> getRequiredPermissions() { return Set.of(); }
        @Override public Class<Void> getInputType() { return Void.class; }
        @Override public Class<String> getOutputType() { return String.class; }
        @Override
        public CapabilityResult<String> execute(ExecutionContext context, Void input) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return CapabilityResult.success(getId(), "FINISHED", Instant.now());
        }
    }

    private static class FaultyCapability implements Capability<Void, String> {
        @Override public String getId() { return "test.fault"; }
        @Override public String getDisplayName() { return "Fault"; }
        @Override public String getDescription() { return "Throws exception"; }
        @Override public CapabilityCategory getCategory() { return CapabilityCategory.SYSTEM; }
        @Override public Set<Permission> getRequiredPermissions() { return Set.of(); }
        @Override public Class<Void> getInputType() { return Void.class; }
        @Override public Class<String> getOutputType() { return String.class; }
        @Override
        public CapabilityResult<String> execute(ExecutionContext context, Void input) {
            throw new RuntimeException("Simulated hardware crash");
        }
    }

    @BeforeEach
    void setup() {
        SimpleCapability echo = new SimpleCapability();
        SlowCapability slow = new SlowCapability();
        FaultyCapability fault = new FaultyCapability();

        registry = new CapabilityRegistry(List.of(echo, slow, fault));
        authorizationService = new AuthorizationService();
        auditLogger = new AuditLogger();
        engine = new ExecutionEngine(registry, authorizationService, auditLogger);
    }

    @Test
    @DisplayName("Authorized caller successfully executes capability")
    void shouldExecuteSuccessfullyWhenAuthorized() {
        Subject caller = Subject.localCli(); // Has ADMIN_ALL
        ExecutionContext context = ExecutionContext.create(InvocationSource.CLI, caller);

        CapabilityResult<String> result = engine.execute("test.echo", context, "hello");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.payload()).isEqualTo("ECHO:hello");
        assertThat(result.status()).isEqualTo(ResultStatus.SUCCESS);
    }

    @Test
    @DisplayName("Unauthorized caller is blocked before capability runs")
    void shouldBlockUnauthorizedCaller() {
        Subject unprivileged = new Subject("guest", "guest", null, Set.of());
        ExecutionContext context = ExecutionContext.create(InvocationSource.CLI, unprivileged);

        CapabilityResult<String> result = engine.execute("test.echo", context, "hello");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.status()).isEqualTo(ResultStatus.UNAUTHORIZED);
        assertThat(result.error().code()).isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("Operation exceeding timeout fails with TIMEOUT status")
    void shouldTimeoutWhenExceedingLimit() {
        Subject caller = Subject.localCli();
        ExecutionContext context = ExecutionContext.create(InvocationSource.CLI, caller, Duration.ofMillis(100));

        CapabilityResult<String> result = engine.execute("test.slow", context, null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.status()).isEqualTo(ResultStatus.TIMEOUT);
        assertThat(result.error().code()).isEqualTo(ErrorCode.TIMEOUT);
    }

    @Test
    @DisplayName("Unhandled exception inside capability is safely caught and wrapped")
    void shouldCatchUnhandledException() {
        Subject caller = Subject.localCli();
        ExecutionContext context = ExecutionContext.create(InvocationSource.CLI, caller);

        CapabilityResult<String> result = engine.execute("test.fault", context, null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.status()).isEqualTo(ResultStatus.FAILURE);
        assertThat(result.error().code()).isEqualTo(ErrorCode.INTERNAL_ERROR);
        assertThat(result.error().message()).contains("Simulated hardware crash");
    }

    @Test
    @DisplayName("Pre-cancelled execution context cancels without running")
    void shouldAbortWhenContextCancelled() {
        Subject caller = Subject.localCli();
        ExecutionContext context = ExecutionContext.create(InvocationSource.CLI, caller);
        context.cancellationToken().cancel("User pressed Ctrl+C");

        CapabilityResult<String> result = engine.execute("test.echo", context, "hello");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.status()).isEqualTo(ResultStatus.CANCELLED);
        assertThat(result.error().message()).contains("User pressed Ctrl+C");
    }
}
