package com.nexus.capabilities.scheduler.timer;

import com.nexus.capabilities.scheduler.timer.dto.TimerUpdateRequest;
import com.nexus.capabilities.scheduler.timer.dto.TimerUpdateResult;
import com.nexus.core.capability.*;
import com.nexus.core.security.Permission;
import com.nexus.platform.scheduler.ScheduledTaskManager;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Capability to update an existing timer's schedule, description, or configuration.
 */
@Component
public class TimerUpdateCapability implements Capability<TimerUpdateRequest, TimerUpdateResult> {

    public static final String ID = "timer.update";

    private final ScheduledTaskManager taskManager;

    public TimerUpdateCapability(ScheduledTaskManager taskManager) {
        this.taskManager = Objects.requireNonNull(taskManager, "ScheduledTaskManager must not be null");
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "Update Scheduled Timer";
    }

    @Override
    public String getDescription() {
        return "Updates the schedule or description of an existing timer and reloads the system daemon";
    }

    @Override
    public CapabilityCategory getCategory() {
        return CapabilityCategory.SERVICE;
    }

    @Override
    public Set<Permission> getRequiredPermissions() {
        return Set.of(Permission.WRITE_SERVICES);
    }

    @Override
    public Class<TimerUpdateRequest> getInputType() {
        return TimerUpdateRequest.class;
    }

    @Override
    public Class<TimerUpdateResult> getOutputType() {
        return TimerUpdateResult.class;
    }

    @Override
    public CapabilityResult<TimerUpdateResult> execute(ExecutionContext context, TimerUpdateRequest input) {
        Instant start = Instant.now();
        if (input == null || input.nameOrId() == null || input.nameOrId().isBlank()) {
            return CapabilityResult.failure(ID, ErrorCode.INVALID_INPUT, "Timer name or ID must be specified", start);
        }
        try {
            TimerUpdateResult result = taskManager.updateTimer(input);
            if (!result.success()) {
                return CapabilityResult.failure(ID, ErrorCode.INTERNAL_ERROR, result.message(), start);
            }
            return CapabilityResult.success(ID, result, start);
        } catch (Exception e) {
            return CapabilityResult.failure(ID, ErrorCode.INTERNAL_ERROR, "Failed to update timer: " + e.getMessage(), start);
        }
    }
}
