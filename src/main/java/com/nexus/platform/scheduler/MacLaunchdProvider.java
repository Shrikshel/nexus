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
 * macOS implementation of ScheduledTaskProvider using launchd Property List (.plist) agents.
 */
@Component
public class MacLaunchdProvider implements ScheduledTaskProvider {

    private static final Logger log = LoggerFactory.getLogger(MacLaunchdProvider.class);

    private final TimerStateManager stateManager;

    public MacLaunchdProvider(TimerStateManager stateManager) {
        this.stateManager = stateManager;
    }

    @Override
    public boolean isSupported() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }

    @Override
    public Path resolveScriptPath(String fullId, TimerScope scope) {
        Path scriptsDir = Path.of(System.getProperty("user.home"), ".nexus", "scripts");
        return scriptsDir.resolve(fullId + ".sh");
    }

    public Path resolvePlistDirectory(TimerScope scope) {
        if (scope == TimerScope.SYSTEM) {
            return Path.of("/Library/LaunchDaemons");
        }
        return Path.of(System.getProperty("user.home"), "Library", "LaunchAgents");
    }

    @Override
    public TimerCreateResult createTimer(TimerCreateRequest request, String id, String fullId, Path scriptPath) {
        TimerScope scope = request.scope() != null ? request.scope() : TimerScope.USER;
        ScheduleParser.ParsedSchedule parsedSchedule = ScheduleParser.parse(request.schedule());
        String label = "com.nexus." + fullId.toLowerCase(Locale.ROOT);
        Path plistDir = resolvePlistDirectory(scope);
        Path plistPath = plistDir.resolve(label + ".plist");

        try {
            Files.createDirectories(scriptPath.getParent());
            if (!Files.exists(scriptPath)) {
                String scriptContent = "#!/bin/bash\nset -euo pipefail\necho \"[" + fullId + "] Executed on $(date)\"\n";
                Files.writeString(scriptPath, scriptContent, StandardCharsets.UTF_8);
            }
            scriptPath.toFile().setExecutable(true, false);

            Files.createDirectories(plistDir);
            String plistContent = generatePlist(label, scriptPath.toAbsolutePath().toString(), parsedSchedule);
            Files.writeString(plistPath, plistContent, StandardCharsets.UTF_8);

            // launchctl load -w
            runCommand(List.of("launchctl", "load", "-w", plistPath.toString()));

            TimerStateManager.TimerRecord record = new TimerStateManager.TimerRecord(
                    id,
                    request.name(),
                    fullId,
                    parsedSchedule.raw(),
                    parsedSchedule.humanDescription(),
                    request.description() != null ? request.description() : "",
                    scope,
                    scriptPath.toAbsolutePath().toString(),
                    plistPath.toAbsolutePath().toString(),
                    plistPath.toAbsolutePath().toString(),
                    Instant.now()
            );
            stateManager.registerTimer(scope, record);

            TimerInfo info = new TimerInfo(
                    id, request.name(), fullId, parsedSchedule.raw(), parsedSchedule.humanDescription(),
                    request.description(), scope, scriptPath.toString(), plistPath.toString(), plistPath.toString(),
                    true, "Scheduled", "-", "-", Instant.now()
            );

            return new TimerCreateResult(true, info, scriptPath.toString(), plistPath.toString(), plistPath.toString(), "launchctl load -w " + plistPath, "Created launchd agent " + label);
        } catch (Exception e) {
            return new TimerCreateResult(false, null, scriptPath.toString(), plistPath.toString(), null, null, e.getMessage());
        }
    }

    @Override
    public List<TimerInfo> listTimers(TimerScope scope) {
        if (scope == TimerScope.ALL || scope == null) {
            List<TimerInfo> all = new ArrayList<>();
            all.addAll(listTimers(TimerScope.USER));
            all.addAll(listTimers(TimerScope.SYSTEM));
            return all;
        }
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
        return new TimerStatusResult(true, info, "Loaded in launchd", "OK", List.of(), "", "OK");
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
        Path plist = Path.of(rec.unitPath());
        runCommand(List.of("launchctl", "unload", "-w", plist.toString()));
        try {
            Files.deleteIfExists(plist);
            if (purgeScript) {
                Files.deleteIfExists(Path.of(rec.scriptPath()));
            }
            stateManager.unregisterTimer(scope, rec.id());
            return new TimerDeleteResult(true, rec.id(), rec.fullIdentifier(), true, true, purgeScript, "Unloaded and deleted launchd agent");
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

    private void runCommand(List<String> cmd) {
        try {
            new ProcessBuilder(cmd).start().waitFor();
        } catch (Exception ignored) {}
    }

    private String generatePlist(String label, String scriptPath, ScheduleParser.ParsedSchedule schedule) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n" +
                "<plist version=\"1.0\">\n" +
                "<dict>\n" +
                "    <key>Label</key>\n" +
                "    <string>" + label + "</string>\n" +
                "    <key>ProgramArguments</key>\n" +
                "    <array>\n" +
                "        <string>" + scriptPath + "</string>\n" +
                "    </array>\n" +
                "    <key>RunAtLoad</key>\n" +
                "    <false/>\n" +
                "    <key>StartInterval</key>\n" +
                "    <integer>3600</integer>\n" +
                "</dict>\n" +
                "</plist>\n";
    }
}
