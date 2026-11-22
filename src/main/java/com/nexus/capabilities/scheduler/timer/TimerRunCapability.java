package com.nexus.capabilities.scheduler.timer;

import com.nexus.capabilities.scheduler.timer.dto.TimerRunRequest;
import com.nexus.capabilities.scheduler.timer.dto.TimerRunResult;
import com.nexus.core.capability.*;
import com.nexus.core.security.Permission;
import com.nexus.platform.scheduler.ScheduledTaskManager;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Capability to trigger an immediate one-off execution of a timer's underlying task.
 */
@Component
public class TimerRunCapability implements Capability<TimerRunRequest, TimerRunResult> {

    public static final String ID = "timer.run";

    private final ScheduledTaskManager taskManager;

    public TimerRunCapability(ScheduledTaskManager taskManager) {
        this.taskManager = Objects.requireNonNull(taskManager, "ScheduledTaskManager must not be null");
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "Run Scheduled Timer Now";
    }

    @Override
    public String getDescription() {
        return "Triggers an on-demand immediate execution of the timer's underlying executable task";
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
    public Class<TimerRunRequest> getInputType() {
        return TimerRunRequest.class;
    }

    @Override
    public Class<TimerRunResult> getOutputType() {
        return TimerRunResult.class;
    }

    @Override
    public CapabilityResult<TimerRunResult> execute(ExecutionContext context, TimerRunRequest input) {
        Instant start = Instant.now();
        if (input == null || input.nameOrId() == null || input.nameOrId().isBlank()) {
            return CapabilityResult.failure(ID, ErrorCode.INVALID_INPUT, "Timer name or ID must be specified", start);
        }
        try {
            TimerRunResult result = taskManager.runTimer(input);
            if (!result.success()) {
                return CapabilityResult.failure(ID, ErrorCode.INTERNAL_ERROR, result.message(), start);
            }
            return CapabilityResult.success(ID, result, start);
        } catch (Exception e) {
            return CapabilityResult.failure(ID, ErrorCode.INTERNAL_ERROR, "Failed to run timer: " + e.getMessage(), start);
        }
    }
}
