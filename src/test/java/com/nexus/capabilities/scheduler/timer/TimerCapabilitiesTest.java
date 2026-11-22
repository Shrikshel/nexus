package com.nexus.capabilities.scheduler.timer;

import com.nexus.capabilities.scheduler.timer.dto.*;
import com.nexus.core.capability.ExecutionContext;
import com.nexus.core.capability.InvocationSource;
import com.nexus.core.security.Permission;
import com.nexus.core.security.Subject;
import com.nexus.platform.scheduler.ScheduledTaskManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class TimerCapabilitiesTest {

    private ScheduledTaskManager taskManager;
    private ExecutionContext context;

    @BeforeEach
    void setUp() {
        taskManager = Mockito.mock(ScheduledTaskManager.class);
        context = ExecutionContext.create(InvocationSource.CLI, Subject.localCli(), Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("TimerListCapability executes and returns TimerListReport")
    void testTimerListCapability() {
        TimerListCapability capability = new TimerListCapability(taskManager);
        assertThat(capability.getId()).isEqualTo("timer.list");
        assertThat(capability.getRequiredPermissions()).contains(Permission.READ_SERVICES);

        TimerInfo timer = new TimerInfo(
                "NX001", "backup", "NX001-backup", "daily", "Every day", "Desc",
                TimerScope.USER, "/tmp/s.sh", "/tmp/u.timer", "/tmp/u.service",
                true, "Tomorrow", "-", "-", Instant.now()
        );
        when(taskManager.listTimers(TimerScope.USER)).thenReturn(List.of(timer));

        var result = capability.execute(context, new TimerListRequest(TimerScope.USER));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.payload().timers()).hasSize(1);
        assertThat(result.payload().timers().get(0).id()).isEqualTo("NX001");
    }

    @Test
    @DisplayName("TimerCreateCapability validates blank name and delegates creation")
    void testTimerCreateCapability() {
        TimerCreateCapability capability = new TimerCreateCapability(taskManager);
        assertThat(capability.getId()).isEqualTo("timer.create");
        assertThat(capability.getRequiredPermissions()).contains(Permission.WRITE_SERVICES);

        // Validation failure
        var failResult = capability.execute(context, new TimerCreateRequest("", "daily", null, TimerScope.USER, null, false));
        assertThat(failResult.isSuccess()).isFalse();

        // Successful creation
        TimerInfo timer = new TimerInfo("NX001", "backup", "NX001-backup", "daily", "Every day", "Desc", TimerScope.USER, "/tmp/s.sh", "/tmp/u.timer", "/tmp/u.service", true, "-", "-", "-", Instant.now());
        when(taskManager.createTimer(any())).thenReturn(new TimerCreateResult(true, timer, "/tmp/s.sh", "/tmp/u.timer", "/tmp/u.service", "enable", "OK"));

        var successResult = capability.execute(context, new TimerCreateRequest("backup", "daily", null, TimerScope.USER, null, false));
        assertThat(successResult.isSuccess()).isTrue();
        assertThat(successResult.payload().scriptPath()).isEqualTo("/tmp/s.sh");
    }

    @Test
    @DisplayName("TimerStatusCapability delegates to ScheduledTaskManager")
    void testTimerStatusCapability() {
        TimerStatusCapability capability = new TimerStatusCapability(taskManager);
        assertThat(capability.getId()).isEqualTo("timer.status");

        TimerInfo timer = new TimerInfo("NX001", "backup", "NX001-backup", "daily", "Every day", "Desc", TimerScope.USER, "/tmp/s.sh", "/tmp/u.timer", "/tmp/u.service", true, "-", "-", "-", Instant.now());
        when(taskManager.getTimerStatus(eq("NX001"), eq(TimerScope.USER)))
                .thenReturn(new TimerStatusResult(true, timer, "Active", "Active", List.of("log 1"), "output", "OK"));

        var result = capability.execute(context, new TimerStatusRequest("NX001", TimerScope.USER));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.payload().timer().name()).isEqualTo("backup");
        assertThat(result.payload().recentLogs()).contains("log 1");
    }

    @Test
    @DisplayName("TimerRunCapability triggers one-off execution")
    void testTimerRunCapability() {
        TimerRunCapability capability = new TimerRunCapability(taskManager);
        assertThat(capability.getId()).isEqualTo("timer.run");

        when(taskManager.runTimer(any())).thenReturn(new TimerRunResult(true, "NX001", 0, "Execution finished", 45, "OK"));

        var result = capability.execute(context, new TimerRunRequest("NX001", TimerScope.USER));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.payload().exitCode()).isEqualTo(0);
        assertThat(result.payload().output()).contains("Execution finished");
    }

    @Test
    @DisplayName("TimerDeleteCapability deletes timer and handles purgeScript")
    void testTimerDeleteCapability() {
        TimerDeleteCapability capability = new TimerDeleteCapability(taskManager);
        assertThat(capability.getId()).isEqualTo("timer.delete");

        when(taskManager.deleteTimer(any())).thenReturn(new TimerDeleteResult(true, "NX001", "NX001-backup", true, true, false, "Deleted"));

        var result = capability.execute(context, new TimerDeleteRequest("NX001", false, TimerScope.USER));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.payload().deletedTimerUnit()).isTrue();
    }
}
