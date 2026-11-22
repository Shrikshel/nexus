package com.nexus.capabilities.scheduler.timer;

import com.nexus.capabilities.scheduler.timer.dto.TimerInfo;
import com.nexus.capabilities.scheduler.timer.dto.TimerListReport;
import com.nexus.capabilities.scheduler.timer.dto.TimerListRequest;
import com.nexus.capabilities.scheduler.timer.dto.TimerScope;
import com.nexus.core.capability.*;
import com.nexus.core.security.Permission;
import com.nexus.platform.scheduler.ScheduledTaskManager;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Capability to query and list scheduled timers across user or system scope.
 */
@Component
public class TimerListCapability implements Capability<TimerListRequest, TimerListReport> {

    public static final String ID = "timer.list";

    private final ScheduledTaskManager taskManager;

    public TimerListCapability(ScheduledTaskManager taskManager) {
        this.taskManager = Objects.requireNonNull(taskManager, "ScheduledTaskManager must not be null");
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "List Scheduled Timers";
    }

    @Override
    public String getDescription() {
        return "Lists configured systemd/launchd/TaskScheduler recurring timers and live execution telemetry";
    }

    @Override
    public CapabilityCategory getCategory() {
        return CapabilityCategory.SERVICE;
    }

    @Override
    public Set<Permission> getRequiredPermissions() {
        return Set.of(Permission.READ_SERVICES);
    }

    @Override
    public Class<TimerListRequest> getInputType() {
        return TimerListRequest.class;
    }

    @Override
    public Class<TimerListReport> getOutputType() {
        return TimerListReport.class;
    }

    @Override
    public CapabilityResult<TimerListReport> execute(ExecutionContext context, TimerListRequest input) {
        Instant start = Instant.now();
        TimerScope scope = (input != null && input.scope() != null) ? input.scope() : TimerScope.USER;
        try {
            List<TimerInfo> timers = taskManager.listTimers(scope);
            TimerListReport report = new TimerListReport(timers, timers.size(), scope, Instant.now());
            return CapabilityResult.success(ID, report, start);
        } catch (Exception e) {
            return CapabilityResult.failure(ID, ErrorCode.INTERNAL_ERROR, "Failed to list timers: " + e.getMessage(), start);
        }
    }
}
