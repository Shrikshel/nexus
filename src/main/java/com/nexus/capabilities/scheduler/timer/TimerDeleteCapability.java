package com.nexus.capabilities.scheduler.timer;

import com.nexus.capabilities.scheduler.timer.dto.TimerDeleteRequest;
import com.nexus.capabilities.scheduler.timer.dto.TimerDeleteResult;
import com.nexus.core.capability.*;
import com.nexus.core.security.Permission;
import com.nexus.platform.scheduler.ScheduledTaskManager;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Capability to delete and unregister a scheduled timer.
 */
@Component
public class TimerDeleteCapability implements Capability<TimerDeleteRequest, TimerDeleteResult> {

    public static final String ID = "timer.delete";

    private final ScheduledTaskManager taskManager;

    public TimerDeleteCapability(ScheduledTaskManager taskManager) {
        this.taskManager = Objects.requireNonNull(taskManager, "ScheduledTaskManager must not be null");
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "Delete Scheduled Timer";
    }

    @Override
    public String getDescription() {
        return "Disables and deletes timer and service units from systemd/launchd and unregisters state";
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
    public Class<TimerDeleteRequest> getInputType() {
        return TimerDeleteRequest.class;
    }

    @Override
    public Class<TimerDeleteResult> getOutputType() {
        return TimerDeleteResult.class;
    }

    @Override
    public CapabilityResult<TimerDeleteResult> execute(ExecutionContext context, TimerDeleteRequest input) {
        Instant start = Instant.now();
        if (input == null || input.nameOrId() == null || input.nameOrId().isBlank()) {
            return CapabilityResult.failure(ID, ErrorCode.INVALID_INPUT, "Timer name or ID must be specified", start);
        }
        try {
            TimerDeleteResult result = taskManager.deleteTimer(input);
            if (!result.success()) {
                return CapabilityResult.failure(ID, ErrorCode.INTERNAL_ERROR, result.message(), start);
            }
            return CapabilityResult.success(ID, result, start);
        } catch (Exception e) {
            return CapabilityResult.failure(ID, ErrorCode.INTERNAL_ERROR, "Failed to delete timer: " + e.getMessage(), start);
        }
    }
}
