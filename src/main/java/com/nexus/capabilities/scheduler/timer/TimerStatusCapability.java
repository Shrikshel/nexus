package com.nexus.capabilities.scheduler.timer;

import com.nexus.capabilities.scheduler.timer.dto.TimerScope;
import com.nexus.capabilities.scheduler.timer.dto.TimerStatusRequest;
import com.nexus.capabilities.scheduler.timer.dto.TimerStatusResult;
import com.nexus.core.capability.*;
import com.nexus.core.security.Permission;
import com.nexus.platform.scheduler.ScheduledTaskManager;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Capability to inspect live system status and execution journal logs for a scheduled timer.
 */
@Component
public class TimerStatusCapability implements Capability<TimerStatusRequest, TimerStatusResult> {

    public static final String ID = "timer.status";

    private final ScheduledTaskManager taskManager;

    public TimerStatusCapability(ScheduledTaskManager taskManager) {
        this.taskManager = Objects.requireNonNull(taskManager, "ScheduledTaskManager must not be null");
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "Timer Live Status";
    }

    @Override
    public String getDescription() {
        return "Inspects live systemd/launchd timer status, next activation, and recent journal execution logs";
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
    public Class<TimerStatusRequest> getInputType() {
        return TimerStatusRequest.class;
    }

    @Override
    public Class<TimerStatusResult> getOutputType() {
        return TimerStatusResult.class;
    }

    @Override
    public CapabilityResult<TimerStatusResult> execute(ExecutionContext context, TimerStatusRequest input) {
        Instant start = Instant.now();
        if (input == null || input.nameOrId() == null || input.nameOrId().isBlank()) {
            return CapabilityResult.failure(ID, ErrorCode.INVALID_INPUT, "Timer name or ID must be specified", start);
        }
        try {
            TimerScope scope = input.scope() != null ? input.scope() : TimerScope.USER;
            TimerStatusResult result = taskManager.getTimerStatus(input.nameOrId(), scope);
            if (!result.success()) {
                return CapabilityResult.failure(ID, ErrorCode.INTERNAL_ERROR, result.message(), start);
            }
            return CapabilityResult.success(ID, result, start);
        } catch (Exception e) {
            return CapabilityResult.failure(ID, ErrorCode.INTERNAL_ERROR, "Failed to inspect timer status: " + e.getMessage(), start);
        }
    }
}
