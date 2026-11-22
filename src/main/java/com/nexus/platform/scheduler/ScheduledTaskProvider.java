package com.nexus.platform.scheduler;

import com.nexus.capabilities.scheduler.timer.dto.*;

import java.nio.file.Path;
import java.util.List;

/**
 * Cross-platform Service Provider Interface (SPI) for creating, inspecting,
 * executing, and deleting scheduled tasks across Linux (systemd), macOS (launchd),
 * and Windows (Task Scheduler).
 */
public interface ScheduledTaskProvider {

    /**
     * Creates the timer and service configuration, creates the executable script stub,
     * and enables the timer.
     */
    TimerCreateResult createTimer(TimerCreateRequest request, String id, String fullId, Path scriptPath);

    /**
     * Lists all Nexus-managed scheduled tasks in the given scope.
     */
    List<TimerInfo> listTimers(TimerScope scope);

    /**
     * Inspects live status and recent journal logs for a timer.
     */
    TimerStatusResult getTimerStatus(String nameOrId, TimerScope scope);

    /**
     * Updates an existing timer's schedule, description, or configuration.
     */
    TimerUpdateResult updateTimer(TimerUpdateRequest request);

    /**
     * Deletes and disables a timer, unlinking service/timer unit files and optionally script.
     */
    TimerDeleteResult deleteTimer(String nameOrId, TimerScope scope, boolean purgeScript);

    /**
     * Triggers an immediate one-off execution of the timer's target task for testing.
     */
    TimerRunResult runTimer(String nameOrId, TimerScope scope);

    /**
     * Resolves the standard filesystem path where the executable script stub should live.
     */
    Path resolveScriptPath(String fullId, TimerScope scope);

    /**
     * Returns true if this provider is supported on the current host operating system.
     */
    boolean isSupported();
}
