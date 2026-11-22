package com.nexus.platform.scheduler;

import com.nexus.capabilities.scheduler.timer.dto.*;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * High-level orchestration manager coordinating platform providers and state tracking.
 */
@Service
public class ScheduledTaskManager {

    private final List<ScheduledTaskProvider> providers;
    private final TimerStateManager stateManager;

    public ScheduledTaskManager(List<ScheduledTaskProvider> providers, TimerStateManager stateManager) {
        this.providers = providers;
        this.stateManager = stateManager;
    }

    public ScheduledTaskProvider getActiveProvider() {
        for (ScheduledTaskProvider provider : providers) {
            if (provider.isSupported()) {
                return provider;
            }
        }
        // Fallback to first available provider
        return providers.get(0);
    }

    public TimerCreateResult createTimer(TimerCreateRequest request) {
        TimerScope scope = request.scope() != null ? request.scope() : TimerScope.USER;
        ScheduledTaskProvider provider = getActiveProvider();

        // Sanitize name: alphanumeric and dashes only
        String sanitizedName = request.name().trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]", "-")
                .replaceAll("-+", "-");
        if (sanitizedName.startsWith("-")) sanitizedName = sanitizedName.substring(1);
        if (sanitizedName.endsWith("-")) sanitizedName = sanitizedName.substring(0, sanitizedName.length() - 1);
        if (sanitizedName.isEmpty()) sanitizedName = "task";

        // Allocate NX001 identifier
        String id = stateManager.allocateNextId(scope, sanitizedName);
        String fullIdentifier = id + "-" + sanitizedName;

        Path scriptPath = provider.resolveScriptPath(fullIdentifier, scope);
        return provider.createTimer(request, id, fullIdentifier, scriptPath);
    }

    public List<TimerInfo> listTimers(TimerScope scope) {
        TimerScope s = scope != null ? scope : TimerScope.USER;
        return getActiveProvider().listTimers(s);
    }

    public TimerStatusResult getTimerStatus(String nameOrId, TimerScope scope) {
        TimerScope s = scope != null ? scope : TimerScope.USER;
        return getActiveProvider().getTimerStatus(nameOrId, s);
    }

    public TimerDeleteResult deleteTimer(TimerDeleteRequest request) {
        TimerScope s = request.scope() != null ? request.scope() : TimerScope.USER;
        return getActiveProvider().deleteTimer(request.nameOrId(), s, request.purgeScript());
    }

    public TimerUpdateResult updateTimer(TimerUpdateRequest request) {
        return getActiveProvider().updateTimer(request);
    }

    public TimerRunResult runTimer(TimerRunRequest request) {
        TimerScope s = request.scope() != null ? request.scope() : TimerScope.USER;
        return getActiveProvider().runTimer(request.nameOrId(), s);
    }

    public Path resolveScriptPath(String nameOrId, TimerScope scope) {
        TimerScope s = scope != null ? scope : TimerScope.USER;
        var opt = stateManager.resolveTimer(s, nameOrId);
        if (opt.isPresent()) {
            return Path.of(opt.get().scriptPath());
        }

        Path defaultPath = getActiveProvider().resolveScriptPath(nameOrId, s);
        if (Files.exists(defaultPath)) {
            return defaultPath;
        }

        // Fallback: If nameOrId is an ID (e.g. "nx001" or "NX001"), look for "NX001-*.sh" in scripts directory
        Path scriptsDir = defaultPath.getParent();
        if (scriptsDir != null && Files.exists(scriptsDir) && nameOrId != null) {
            String prefix = nameOrId.trim().toLowerCase(java.util.Locale.ROOT) + "-";
            java.io.File[] matches = scriptsDir.toFile().listFiles((dir, name) ->
                    name.toLowerCase(java.util.Locale.ROOT).startsWith(prefix) && (name.endsWith(".sh") || name.endsWith(".bat")));
            if (matches != null && matches.length > 0) {
                return matches[0].toPath();
            }
        }

        return defaultPath;
    }
}
