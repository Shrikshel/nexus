package com.nexus.platform.scheduler;

import com.nexus.capabilities.scheduler.timer.dto.*;
import com.nexus.core.security.privilege.PrivilegeRunner;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Linux implementation of ScheduledTaskProvider using systemd timer and service units.
 * Supports both User mode (default: systemctl --user) and System mode (systemctl).
 */
@Component
public class LinuxSystemdTimerProvider implements ScheduledTaskProvider {

    private static final Logger log = LoggerFactory.getLogger(LinuxSystemdTimerProvider.class);

    private final TimerStateManager stateManager;
    private final PrivilegeRunner privilegeRunner;

    public LinuxSystemdTimerProvider(TimerStateManager stateManager, PrivilegeRunner privilegeRunner) {
        this.stateManager = stateManager;
        this.privilegeRunner = privilegeRunner;
    }

    @Override
    public boolean isSupported() {
        return System.getProperty("os.name", "").toLowerCase().contains("linux");
    }

    @Override
    public Path resolveScriptPath(String fullId, TimerScope scope) {
        Path scriptsDir;
        if (scope == TimerScope.SYSTEM) {
            scriptsDir = Path.of("/root/.nexus/scripts");
        } else {
            scriptsDir = Path.of(System.getProperty("user.home"), ".nexus", "scripts");
        }
        return scriptsDir.resolve(fullId + ".sh");
    }

    public Path resolveUnitDirectory(TimerScope scope) {
        if (scope == TimerScope.SYSTEM) {
            return Path.of("/etc/systemd/system");
        }
        return Path.of(System.getProperty("user.home"), ".config", "systemd", "user");
    }

    @Override
    public TimerCreateResult createTimer(TimerCreateRequest request, String id, String fullId, Path scriptPath) {
        TimerScope scope = request.scope() != null ? request.scope() : TimerScope.USER;
        ScheduleParser.ParsedSchedule parsedSchedule = ScheduleParser.parse(request.schedule());

        String unitBaseName = "nexus-" + fullId.toLowerCase(Locale.ROOT);
        Path unitDir = resolveUnitDirectory(scope);
        Path timerUnitPath = unitDir.resolve(unitBaseName + ".timer");
        Path serviceUnitPath = unitDir.resolve(unitBaseName + ".service");

        try {
            // 1. Create script directory & default script stub
            createDirectoryWithSudoFallback(scriptPath.getParent(), scope);
            if (!Files.exists(scriptPath)) {
                String scriptContent = request.scriptContent() != null && !request.scriptContent().isBlank()
                        ? request.scriptContent()
                        : generateDefaultScriptStub(id, fullId, parsedSchedule, request.description());
                writeStringWithSudoFallback(scriptPath, scriptContent, scope, true);
            }
            File scriptFile = scriptPath.toFile();
            scriptFile.setExecutable(true, false);

            // 2. Create Service Unit file
            createDirectoryWithSudoFallback(unitDir, scope);
            String serviceContent = generateServiceUnit(fullId, request.description(), scriptPath.toAbsolutePath().toString(), id, request.name());
            writeStringWithSudoFallback(serviceUnitPath, serviceContent, scope, false);

            // 3. Create Timer Unit file
            String timerContent = generateTimerUnit(fullId, request.description(), parsedSchedule.systemdDirective(), id, request.name());
            writeStringWithSudoFallback(timerUnitPath, timerContent, scope, false);

            // 4. Reload systemd daemon
            runSystemctl(scope, List.of("daemon-reload"));

            // 5. Enable and start timer immediately
            runSystemctl(scope, List.of("enable", "--now", unitBaseName + ".timer"));

            // 6. Record metadata in state index
            TimerStateManager.TimerRecord record = new TimerStateManager.TimerRecord(
                    id,
                    request.name(),
                    fullId,
                    parsedSchedule.raw(),
                    parsedSchedule.humanDescription(),
                    request.description() != null ? request.description() : "",
                    scope,
                    scriptPath.toAbsolutePath().toString(),
                    timerUnitPath.toAbsolutePath().toString(),
                    serviceUnitPath.toAbsolutePath().toString(),
                    Instant.now()
            );
            stateManager.registerTimer(scope, record);

            String enableCmd = scope == TimerScope.SYSTEM
                    ? "sudo systemctl enable --now " + unitBaseName + ".timer"
                    : "systemctl --user enable --now " + unitBaseName + ".timer";

            TimerInfo timerInfo = new TimerInfo(
                    id,
                    request.name(),
                    fullId,
                    parsedSchedule.raw(),
                    parsedSchedule.humanDescription(),
                    request.description(),
                    scope,
                    scriptPath.toAbsolutePath().toString(),
                    timerUnitPath.toAbsolutePath().toString(),
                    serviceUnitPath.toAbsolutePath().toString(),
                    true,
                    "Pending next activation",
                    "-",
                    "-",
                    Instant.now()
            );

            return new TimerCreateResult(
                    true,
                    timerInfo,
                    scriptPath.toAbsolutePath().toString(),
                    timerUnitPath.toAbsolutePath().toString(),
                    serviceUnitPath.toAbsolutePath().toString(),
                    enableCmd,
                    "Successfully created and activated systemd timer: " + unitBaseName + ".timer"
            );

        } catch (Exception e) {
            log.error("Failed to create systemd timer {}: {}", fullId, e.getMessage(), e);
            return new TimerCreateResult(
                    false,
                    null,
                    scriptPath.toString(),
                    timerUnitPath.toString(),
                    serviceUnitPath.toString(),
                    null,
                    "Failed to create timer: " + e.getMessage()
            );
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
        Map<String, TimerStateManager.TimerRecord> recorded = new LinkedHashMap<>();
        if (state != null && state.timers() != null) {
            recorded.putAll(state.timers());
        }

        // Also discover any nexus-nx*.timer files on filesystem not in state
        Path unitDir = resolveUnitDirectory(scope);
        if (Files.exists(unitDir)) {
            File[] timerFiles = unitDir.toFile().listFiles((d, name) -> name.startsWith("nexus-nx") && name.endsWith(".timer"));
            if (timerFiles != null) {
                for (File tf : timerFiles) {
                    String baseName = tf.getName().substring(0, tf.getName().length() - 6); // remove .timer
                    String stripped = baseName.substring(6); // remove nexus-
                    if (stateManager.resolveTimer(scope, stripped).isEmpty()) {
                        // Extract ID and Name
                        Matcher m = Pattern.compile("^(nx\\d{3,})-(.+)$", Pattern.CASE_INSENSITIVE).matcher(stripped);
                        if (m.matches()) {
                            String discoveredId = m.group(1).toUpperCase(Locale.ROOT);
                            String discoveredName = m.group(2);
                            Path svcPath = unitDir.resolve(baseName + ".service");
                            Path scriptPath = resolveScriptPath(discoveredId + "-" + discoveredName, scope);
                            TimerStateManager.TimerRecord rec = new TimerStateManager.TimerRecord(
                                    discoveredId,
                                    discoveredName,
                                    discoveredId + "-" + discoveredName,
                                    "custom",
                                    "Discovered systemd timer",
                                    "",
                                    scope,
                                    scriptPath.toString(),
                                    tf.getAbsolutePath(),
                                    svcPath.toString(),
                                    Instant.now()
                            );
                            stateManager.registerTimer(scope, rec);
                            recorded.put(discoveredId, rec);
                        }
                    }
                }
            }
        }

        // Query live systemctl list-timers for next/last execution telemetry
        Map<String, LiveTimerStatus> liveData = queryLiveTimers(scope);

        List<TimerInfo> result = new ArrayList<>();
        for (TimerStateManager.TimerRecord r : recorded.values()) {
            String unitName = "nexus-" + r.fullIdentifier().toLowerCase(Locale.ROOT) + ".timer";
            LiveTimerStatus live = liveData.getOrDefault(unitName, LiveTimerStatus.inactive());

            result.add(new TimerInfo(
                    r.id(),
                    r.name(),
                    r.fullIdentifier(),
                    r.schedule(),
                    r.scheduleDetails(),
                    r.description(),
                    r.scope(),
                    r.scriptPath(),
                    r.unitPath(),
                    r.serviceUnitPath(),
                    live.active(),
                    live.nextRun(),
                    live.lastRun(),
                    live.lastResult(),
                    r.createdAt()
            ));
        }

        result.sort(Comparator.comparing(TimerInfo::id));
        return result;
    }

    @Override
    public TimerStatusResult getTimerStatus(String nameOrId, TimerScope scope) {
        Optional<TimerStateManager.TimerRecord> recordOpt = stateManager.resolveTimer(scope, nameOrId);
        if (recordOpt.isEmpty()) {
            return new TimerStatusResult(false, null, null, null, List.of(), null, "Timer not found: " + nameOrId);
        }
        TimerStateManager.TimerRecord r = recordOpt.get();
        String unitBaseName = "nexus-" + r.fullIdentifier().toLowerCase(Locale.ROOT);

        String timerStatus = runSystemctlCapture(scope, List.of("status", unitBaseName + ".timer", "--no-pager"));
        String serviceStatus = runSystemctlCapture(scope, List.of("status", unitBaseName + ".service", "--no-pager"));
        List<String> recentLogs = fetchRecentJournalLogs(scope, unitBaseName + ".service");

        boolean isActive = timerStatus.contains("Active: active");
        LiveTimerStatus live = queryLiveTimers(scope).getOrDefault(unitBaseName + ".timer", LiveTimerStatus.inactive());

        TimerInfo info = new TimerInfo(
                r.id(),
                r.name(),
                r.fullIdentifier(),
                r.schedule(),
                r.scheduleDetails(),
                r.description(),
                r.scope(),
                r.scriptPath(),
                r.unitPath(),
                r.serviceUnitPath(),
                isActive,
                live.nextRun(),
                live.lastRun(),
                live.lastResult(),
                r.createdAt()
        );

        return new TimerStatusResult(true, info, timerStatus, serviceStatus, recentLogs, timerStatus + "\n" + serviceStatus, "OK");
    }

    @Override
    public TimerUpdateResult updateTimer(TimerUpdateRequest request) {
        TimerScope scope = request.scope() != null ? request.scope() : TimerScope.USER;
        Optional<TimerStateManager.TimerRecord> recordOpt = stateManager.resolveTimer(scope, request.nameOrId());
        if (recordOpt.isEmpty()) {
            return new TimerUpdateResult(false, null, "Timer not found: " + request.nameOrId());
        }
        TimerStateManager.TimerRecord existing = recordOpt.get();
        String newSchedule = request.schedule() != null && !request.schedule().isBlank() ? request.schedule() : existing.schedule();
        String newDesc = request.description() != null ? request.description() : existing.description();
        ScheduleParser.ParsedSchedule parsed = ScheduleParser.parse(newSchedule);

        String unitBaseName = "nexus-" + existing.fullIdentifier().toLowerCase(Locale.ROOT);
        Path unitDir = resolveUnitDirectory(scope);
        Path timerUnitPath = unitDir.resolve(unitBaseName + ".timer");

        try {
            if (request.schedule() != null && !request.schedule().isBlank()) {
                String timerContent = generateTimerUnit(existing.fullIdentifier(), newDesc, parsed.systemdDirective(), existing.id(), existing.name());
                writeStringWithSudoFallback(timerUnitPath, timerContent, scope, false);
                runSystemctl(scope, List.of("daemon-reload"));
                runSystemctl(scope, List.of("restart", unitBaseName + ".timer"));
            }

            TimerStateManager.TimerRecord updatedRecord = new TimerStateManager.TimerRecord(
                    existing.id(),
                    existing.name(),
                    existing.fullIdentifier(),
                    parsed.raw(),
                    parsed.humanDescription(),
                    newDesc,
                    scope,
                    existing.scriptPath(),
                    existing.unitPath(),
                    existing.serviceUnitPath(),
                    existing.createdAt()
            );
            stateManager.registerTimer(scope, updatedRecord);

            TimerInfo timerInfo = new TimerInfo(
                    updatedRecord.id(),
                    updatedRecord.name(),
                    updatedRecord.fullIdentifier(),
                    updatedRecord.schedule(),
                    updatedRecord.scheduleDetails(),
                    updatedRecord.description(),
                    scope,
                    updatedRecord.scriptPath(),
                    updatedRecord.unitPath(),
                    updatedRecord.serviceUnitPath(),
                    true,
                    "Updated",
                    "-",
                    "-",
                    updatedRecord.createdAt()
            );

            return new TimerUpdateResult(true, timerInfo, "Timer " + existing.fullIdentifier() + " successfully updated.");
        } catch (Exception e) {
            log.error("Failed to update timer {}: {}", existing.fullIdentifier(), e.getMessage());
            return new TimerUpdateResult(false, null, "Failed to update timer: " + e.getMessage());
        }
    }

    @Override
    public TimerDeleteResult deleteTimer(String nameOrId, TimerScope scope, boolean purgeScript) {
        Optional<TimerStateManager.TimerRecord> recordOpt = stateManager.resolveTimer(scope, nameOrId);
        if (recordOpt.isEmpty()) {
            return new TimerDeleteResult(false, nameOrId, null, false, false, false, "Timer not found: " + nameOrId);
        }
        TimerStateManager.TimerRecord r = recordOpt.get();
        String unitBaseName = "nexus-" + r.fullIdentifier().toLowerCase(Locale.ROOT);

        // 1. Stop and disable timer in systemd
        runSystemctl(scope, List.of("stop", unitBaseName + ".timer"));
        runSystemctl(scope, List.of("disable", unitBaseName + ".timer"));

        // 2. Remove .timer and .service unit files
        boolean deletedTimer = false;
        boolean deletedService = false;
        boolean deletedScript = false;

        Path timerPath = Path.of(r.unitPath());
        Path servicePath = Path.of(r.serviceUnitPath());
        Path scriptPath = Path.of(r.scriptPath());

        try {
            deleteFileWithSudoFallback(timerPath, scope);
            deletedTimer = true;
            deleteFileWithSudoFallback(servicePath, scope);
            deletedService = true;
            if (purgeScript) {
                deleteFileWithSudoFallback(scriptPath, scope);
                deletedScript = true;
            }

            // 3. Reload systemd
            runSystemctl(scope, List.of("daemon-reload"));

            // 4. Unregister from state index
            stateManager.unregisterTimer(scope, r.id());

            return new TimerDeleteResult(
                    true,
                    r.id(),
                    r.fullIdentifier(),
                    deletedTimer,
                    deletedService,
                    deletedScript,
                    "Successfully deleted timer: " + r.fullIdentifier()
            );

        } catch (Exception e) {
            return new TimerDeleteResult(
                    false,
                    r.id(),
                    r.fullIdentifier(),
                    deletedTimer,
                    deletedService,
                    deletedScript,
                    "Error deleting timer: " + e.getMessage()
            );
        }
    }

    @Override
    public TimerRunResult runTimer(String nameOrId, TimerScope scope) {
        Optional<TimerStateManager.TimerRecord> recordOpt = stateManager.resolveTimer(scope, nameOrId);
        if (recordOpt.isEmpty()) {
            return new TimerRunResult(false, nameOrId, 1, "", 0, "Timer not found: " + nameOrId);
        }
        TimerStateManager.TimerRecord r = recordOpt.get();
        long start = System.currentTimeMillis();

        Path script = Path.of(r.scriptPath());
        if (!Files.exists(script)) {
            if (scope != TimerScope.SYSTEM || "root".equalsIgnoreCase(System.getProperty("user.name"))) {
                return new TimerRunResult(false, r.fullIdentifier(), 1, "", 0, "Script file does not exist at: " + r.scriptPath());
            }
        }

        try {
            List<String> cmd = new ArrayList<>();
            if (scope == TimerScope.SYSTEM && !"root".equalsIgnoreCase(System.getProperty("user.name"))) {
                cmd.add("sudo");
            }
            cmd.add(r.scriptPath());
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = proc.waitFor();
            long duration = System.currentTimeMillis() - start;

            return new TimerRunResult(
                    exitCode == 0,
                    r.fullIdentifier(),
                    exitCode,
                    output.trim(),
                    duration,
                    exitCode == 0 ? "Task executed successfully in " + duration + "ms" : "Task failed with exit code " + exitCode
            );
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            return new TimerRunResult(false, r.fullIdentifier(), 1, e.getMessage(), duration, "Execution error: " + e.getMessage());
        }
    }

    private void runSystemctl(TimerScope scope, List<String> args) {
        try {
            List<String> cmd = new ArrayList<>();
            if (scope == TimerScope.SYSTEM && !"root".equalsIgnoreCase(System.getProperty("user.name"))) {
                cmd.add("sudo");
            }
            cmd.add("systemctl");
            if (scope == TimerScope.USER) {
                cmd.add("--user");
            }
            cmd.addAll(args);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            proc.waitFor();
        } catch (Exception e) {
            log.debug("systemctl command failed: {} ({})", args, e.getMessage());
        }
    }

    private String runSystemctlCapture(TimerScope scope, List<String> args) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("systemctl");
            if (scope == TimerScope.USER) {
                cmd.add("--user");
            }
            cmd.addAll(args);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            return new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return "Failed to query systemctl: " + e.getMessage();
        }
    }

    private List<String> fetchRecentJournalLogs(TimerScope scope, String serviceName) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("journalctl");
            if (scope == TimerScope.USER) {
                cmd.add("--user");
            }
            cmd.addAll(List.of("-u", serviceName, "-n", "15", "--no-pager"));
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            String raw = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            List<String> lines = new ArrayList<>();
            for (String l : raw.split("\n")) {
                if (!l.isBlank()) lines.add(l);
            }
            return lines;
        } catch (Exception e) {
            return List.of("Log retrieval unavailable: " + e.getMessage());
        }
    }

    private Map<String, LiveTimerStatus> queryLiveTimers(TimerScope scope) {
        Map<String, LiveTimerStatus> map = new HashMap<>();
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("systemctl");
            if (scope == TimerScope.USER) {
                cmd.add("--user");
            }
            cmd.addAll(List.of("list-timers", "--all", "--no-pager"));
            ProcessBuilder pb = new ProcessBuilder(cmd);
            Process proc = pb.start();
            String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            for (String line : output.split("\n")) {
                String clean = line.trim();
                if (clean.contains(".timer")) {
                    String[] tokens = clean.split("\\s+");
                    if (tokens.length >= 6) {
                        String timerUnit = tokens[tokens.length - 2];
                        if (timerUnit.endsWith(".timer")) {
                            map.put(timerUnit, new LiveTimerStatus(true, tokens[0] + " " + tokens[1], tokens[3] + " " + tokens[4], "SUCCESS"));
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return map;
    }

    private String generateDefaultScriptStub(String id, String fullId, ScheduleParser.ParsedSchedule schedule, String desc) {
        return "#!/bin/bash\n" +
                "# ==============================================================================\n" +
                "# Nexus Timer Task: " + fullId + "\n" +
                "# Schedule: " + schedule.raw() + " (" + schedule.humanDescription() + ")\n" +
                "# Description: " + (desc != null ? desc : "Automated scheduled task") + "\n" +
                "# Created: " + Instant.now() + "\n" +
                "# ==============================================================================\n" +
                "set -euo pipefail\n\n" +
                "echo \"[$(date -Iseconds)] [" + fullId + "] Task started\"\n\n" +
                "# >>> YOUR AUTOMATION SCRIPT STARTS HERE <<<\n" +
                "# Add your shell commands below:\n" +
                "echo \"Task " + fullId + " executed successfully.\"\n" +
                "# >>> YOUR AUTOMATION SCRIPT ENDS HERE <<<\n\n" +
                "echo \"[$(date -Iseconds)] [" + fullId + "] Task finished successfully\"\n";
    }

    private String generateServiceUnit(String fullId, String desc, String scriptPath, String id, String name) {
        return "[Unit]\n" +
                "Description=Nexus Task: " + fullId + " (" + (desc != null ? desc : "Scheduled Task") + ")\n" +
                "Documentation=https://github.com/Shrikshel/nexus\n" +
                "X-Nexus-Id=" + id + "\n" +
                "X-Nexus-Name=" + name + "\n\n" +
                "[Service]\n" +
                "Type=oneshot\n" +
                "ExecStart=" + scriptPath + "\n" +
                "StandardOutput=journal\n" +
                "StandardError=journal\n";
    }

    private String generateTimerUnit(String fullId, String desc, String timerDirective, String id, String name) {
        return "[Unit]\n" +
                "Description=Nexus Timer: " + fullId + " (" + (desc != null ? desc : "Scheduled Timer") + ")\n" +
                "Documentation=https://github.com/Shrikshel/nexus\n" +
                "X-Nexus-Id=" + id + "\n" +
                "X-Nexus-Name=" + name + "\n\n" +
                "[Timer]\n" +
                timerDirective + "\n" +
                "Persistent=true\n\n" +
                "[Install]\n" +
                "WantedBy=timers.target\n";
    }

    private void createDirectoryWithSudoFallback(Path dir, TimerScope scope) throws IOException {
        if (dir == null || Files.exists(dir)) return;
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            if (scope == TimerScope.SYSTEM) {
                try {
                    new ProcessBuilder("sudo", "mkdir", "-p", dir.toString()).start().waitFor();
                } catch (Exception ex) {
                    throw new IOException("Failed to create directory " + dir + ": " + ex.getMessage(), ex);
                }
            } else {
                throw e;
            }
        }
    }

    private void writeStringWithSudoFallback(Path path, String content, TimerScope scope, boolean makeExecutable) throws IOException {
        try {
            Files.writeString(path, content, StandardCharsets.UTF_8);
            if (makeExecutable) {
                path.toFile().setExecutable(true, false);
            }
        } catch (Exception e) {
            if (scope == TimerScope.SYSTEM) {
                try {
                    File tmp = File.createTempFile("nexus-tmp-", ".tmp");
                    Files.writeString(tmp.toPath(), content, StandardCharsets.UTF_8);
                    new ProcessBuilder("sudo", "cp", tmp.getAbsolutePath(), path.toString()).start().waitFor();
                    if (makeExecutable) {
                        new ProcessBuilder("sudo", "chmod", "755", path.toString()).start().waitFor();
                    } else {
                        new ProcessBuilder("sudo", "chmod", "644", path.toString()).start().waitFor();
                    }
                    tmp.delete();
                } catch (Exception ex) {
                    throw new IOException("Failed to write file " + path + ": " + ex.getMessage(), ex);
                }
            } else {
                throw e;
            }
        }
    }

    private void deleteFileWithSudoFallback(Path path, TimerScope scope) throws IOException {
        if (scope == TimerScope.SYSTEM) {
            try {
                if (Files.exists(path)) {
                    Files.deleteIfExists(path);
                    return;
                }
            } catch (Exception ignored) {}
            try {
                new ProcessBuilder("sudo", "rm", "-f", path.toString()).start().waitFor();
            } catch (Exception ex) {
                throw new IOException("Failed to delete " + path + ": " + ex.getMessage(), ex);
            }
        } else {
            if (Files.exists(path)) {
                Files.deleteIfExists(path);
            }
        }
    }

    private record LiveTimerStatus(boolean active, String nextRun, String lastRun, String lastResult) {
        public static LiveTimerStatus inactive() {
            return new LiveTimerStatus(false, "-", "-", "-");
        }
    }
}
