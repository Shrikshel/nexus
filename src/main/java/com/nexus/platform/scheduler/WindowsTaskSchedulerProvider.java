package com.nexus.platform.scheduler;

import com.nexus.capabilities.scheduler.timer.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * Windows implementation of ScheduledTaskProvider using schtasks.exe.
 */
@Component
public class WindowsTaskSchedulerProvider implements ScheduledTaskProvider {

    private static final Logger log = LoggerFactory.getLogger(WindowsTaskSchedulerProvider.class);

    private final TimerStateManager stateManager;

    public WindowsTaskSchedulerProvider(TimerStateManager stateManager) {
        this.stateManager = stateManager;
    }

    @Override
    public boolean isSupported() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    @Override
    public Path resolveScriptPath(String fullId, TimerScope scope) {
        Path scriptsDir = Path.of(System.getProperty("user.home"), ".nexus", "scripts");
        return scriptsDir.resolve(fullId + ".bat");
    }

    @Override
    public TimerCreateResult createTimer(TimerCreateRequest request, String id, String fullId, Path scriptPath) {
        TimerScope scope = request.scope() != null ? request.scope() : TimerScope.USER;
        ScheduleParser.ParsedSchedule parsedSchedule = ScheduleParser.parse(request.schedule());
        String taskName = "nexus-" + fullId.toLowerCase(Locale.ROOT);

        try {
            Files.createDirectories(scriptPath.getParent());
            if (!Files.exists(scriptPath)) {
                String scriptContent = "@echo off\r\necho [" + fullId + "] Task started\r\necho Task completed\r\n";
                Files.writeString(scriptPath, scriptContent, StandardCharsets.UTF_8);
            }

            // schtasks /create /tn taskName /tr "scriptPath" /sc ...
            List<String> cmd = new ArrayList<>();
            cmd.add("schtasks.exe");
            cmd.add("/create");
            cmd.add("/tn");
            cmd.add(taskName);
            cmd.add("/tr");
            cmd.add(scriptPath.toAbsolutePath().toString());
            cmd.addAll(Arrays.asList(parsedSchedule.windowsScheduleArgs().split("\\s+")));
            cmd.add("/f");

            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            int exit = p.waitFor();

            TimerStateManager.TimerRecord record = new TimerStateManager.TimerRecord(
                    id,
                    request.name(),
                    fullId,
                    parsedSchedule.raw(),
                    parsedSchedule.humanDescription(),
                    request.description() != null ? request.description() : "",
                    scope,
                    scriptPath.toAbsolutePath().toString(),
                    taskName,
                    taskName,
                    Instant.now()
            );
            stateManager.registerTimer(scope, record);

            TimerInfo info = new TimerInfo(
                    id, request.name(), fullId, parsedSchedule.raw(), parsedSchedule.humanDescription(),
                    request.description(), scope, scriptPath.toString(), taskName, taskName,
                    exit == 0, "Scheduled", "-", "-", Instant.now()
            );

            return new TimerCreateResult(exit == 0, info, scriptPath.toString(), taskName, taskName, "schtasks /run /tn " + taskName, "Created Task Scheduler task " + taskName);
        } catch (Exception e) {
            return new TimerCreateResult(false, null, scriptPath.toString(), taskName, taskName, null, e.getMessage());
        }
    }

    @Override
    public List<TimerInfo> listTimers(TimerScope scope) {
        TimerStateManager.StateFile state = stateManager.loadState(scope);
        List<TimerInfo> list = new ArrayList<>();
        for (TimerStateManager.TimerRecord r : state.timers().values()) {
            list.add(new TimerInfo(
                    r.id(), r.name(), r.fullIdentifier(), r.schedule(), r.scheduleDetails(),
                    r.description(), r.scope(), r.scriptPath(), r.unitPath(), r.serviceUnitPath(),
                    true, "Active", "-", "-", r.createdAt()
            ));
        }
        return list;
    }

    @Override
    public TimerStatusResult getTimerStatus(String nameOrId, TimerScope scope) {
        Optional<TimerStateManager.TimerRecord> r = stateManager.resolveTimer(scope, nameOrId);
        if (r.isEmpty()) {
            return new TimerStatusResult(false, null, null, null, List.of(), null, "Timer not found: " + nameOrId);
        }
        TimerStateManager.TimerRecord rec = r.get();
        TimerInfo info = new TimerInfo(
                rec.id(), rec.name(), rec.fullIdentifier(), rec.schedule(), rec.scheduleDetails(),
                rec.description(), rec.scope(), rec.scriptPath(), rec.unitPath(), rec.serviceUnitPath(),
                true, "Active", "-", "-", rec.createdAt()
        );
        return new TimerStatusResult(true, info, "Task Scheduler", "OK", List.of(), "", "OK");
    }

    @Override
    public TimerUpdateResult updateTimer(TimerUpdateRequest request) {
        TimerScope scope = request.scope() != null ? request.scope() : TimerScope.USER;
        Optional<TimerStateManager.TimerRecord> r = stateManager.resolveTimer(scope, request.nameOrId());
        if (r.isEmpty()) {
            return new TimerUpdateResult(false, null, "Timer not found: " + request.nameOrId());
        }
        TimerStateManager.TimerRecord existing = r.get();
        String newSchedule = request.schedule() != null && !request.schedule().isBlank() ? request.schedule() : existing.schedule();
        String newDesc = request.description() != null ? request.description() : existing.description();
        ScheduleParser.ParsedSchedule parsed = ScheduleParser.parse(newSchedule);

        TimerStateManager.TimerRecord updated = new TimerStateManager.TimerRecord(
                existing.id(), existing.name(), existing.fullIdentifier(),
                parsed.raw(), parsed.humanDescription(), newDesc,
                scope, existing.scriptPath(), existing.unitPath(), existing.serviceUnitPath(), existing.createdAt()
        );
        stateManager.registerTimer(scope, updated);

        TimerInfo info = new TimerInfo(
                updated.id(), updated.name(), updated.fullIdentifier(),
                updated.schedule(), updated.scheduleDetails(), updated.description(),
                scope, updated.scriptPath(), updated.unitPath(), updated.serviceUnitPath(),
                true, "Updated", "-", "-", updated.createdAt()
        );
        return new TimerUpdateResult(true, info, "Updated timer " + existing.fullIdentifier());
    }

    @Override
    public TimerDeleteResult deleteTimer(String nameOrId, TimerScope scope, boolean purgeScript) {
        Optional<TimerStateManager.TimerRecord> r = stateManager.resolveTimer(scope, nameOrId);
        if (r.isEmpty()) {
            return new TimerDeleteResult(false, nameOrId, null, false, false, false, "Timer not found: " + nameOrId);
        }
        TimerStateManager.TimerRecord rec = r.get();
        String taskName = "nexus-" + rec.fullIdentifier().toLowerCase(Locale.ROOT);
        try {
            new ProcessBuilder("schtasks.exe", "/delete", "/tn", taskName, "/f").start().waitFor();
            if (purgeScript) {
                Files.deleteIfExists(Path.of(rec.scriptPath()));
            }
            stateManager.unregisterTimer(scope, rec.id());
            return new TimerDeleteResult(true, rec.id(), rec.fullIdentifier(), true, true, purgeScript, "Deleted task " + taskName);
        } catch (Exception e) {
            return new TimerDeleteResult(false, rec.id(), rec.fullIdentifier(), false, false, false, e.getMessage());
        }
    }

    @Override
    public TimerRunResult runTimer(String nameOrId, TimerScope scope) {
        Optional<TimerStateManager.TimerRecord> r = stateManager.resolveTimer(scope, nameOrId);
        if (r.isEmpty()) {
            return new TimerRunResult(false, nameOrId, 1, "", 0, "Timer not found: " + nameOrId);
        }
        long start = System.currentTimeMillis();
        try {
            Process p = new ProcessBuilder(r.get().scriptPath()).start();
            int code = p.waitFor();
            long dur = System.currentTimeMillis() - start;
            return new TimerRunResult(code == 0, r.get().fullIdentifier(), code, "Executed", dur, "Finished with code " + code);
        } catch (Exception e) {
            return new TimerRunResult(false, r.get().fullIdentifier(), 1, e.getMessage(), 0, e.getMessage());
        }
    }
}
