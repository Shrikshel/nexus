package com.nexus.capabilities.scheduler.timer;

import com.nexus.capabilities.scheduler.timer.dto.TimerCreateRequest;
import com.nexus.capabilities.scheduler.timer.dto.TimerCreateResult;
import com.nexus.core.capability.*;
import com.nexus.core.security.Permission;
import com.nexus.platform.scheduler.ScheduledTaskManager;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Capability to create a new scheduled timer and associated executable script stub.
 */
@Component
public class TimerCreateCapability implements Capability<TimerCreateRequest, TimerCreateResult> {

    public static final String ID = "timer.create";

    private final ScheduledTaskManager taskManager;

    public TimerCreateCapability(ScheduledTaskManager taskManager) {
        this.taskManager = Objects.requireNonNull(taskManager, "ScheduledTaskManager must not be null");
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "Create Scheduled Timer";
    }

    @Override
    public String getDescription() {
        return "Creates an automated systemd timer & service unit, scaffolds the executable script stub, and enables the timer";
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
    public Class<TimerCreateRequest> getInputType() {
        return TimerCreateRequest.class;
    }

    @Override
    public Class<TimerCreateResult> getOutputType() {
        return TimerCreateResult.class;
    }

    @Override
    public CapabilityResult<TimerCreateResult> execute(ExecutionContext context, TimerCreateRequest input) {
        Instant start = Instant.now();
        if (input == null || input.name() == null || input.name().isBlank()) {
            return CapabilityResult.failure(ID, ErrorCode.INVALID_INPUT, "Timer name must not be blank", start);
        }
        try {
            TimerCreateResult result = taskManager.createTimer(input);
            if (!result.success()) {
                return CapabilityResult.failure(ID, ErrorCode.INTERNAL_ERROR, result.message(), start);
            }
            return CapabilityResult.success(ID, result, start);
        } catch (Exception e) {
            return CapabilityResult.failure(ID, ErrorCode.INTERNAL_ERROR, "Failed to create timer: " + e.getMessage(), start);
        }
    }
}
