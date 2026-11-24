package com.nexus.capabilities.reminder.scheduler;

import com.nexus.capabilities.reminder.dto.ReminderEvent;
import com.nexus.capabilities.reminder.dto.ReminderStage;
import com.nexus.capabilities.reminder.dto.ReminderStageStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Generates and manages native systemd timer and service units for reminders.
 * Projecting reminders into systemd provides hardware-level wakeups with 0% CPU idle burn.
 */
@Component
public class ReminderSchedulerProvider {

    private static final Logger log = LoggerFactory.getLogger(ReminderSchedulerProvider.class);
    private static final DateTimeFormatter SYSTEMD_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Path customUnitDirectory;

    public void setCustomUnitDirectory(Path path) {
        this.customUnitDirectory = path;
    }

    public Path resolveUnitDirectory(boolean systemScope) {
        if (customUnitDirectory != null) {
            return customUnitDirectory;
        }
        if (systemScope) {
            return Path.of("/etc/systemd/system");
        }
        return Path.of(System.getProperty("user.home"), ".config", "systemd", "user");
    }

    public boolean isSystemScope() {
        return "root".equalsIgnoreCase(System.getProperty("user.name"));
    }

    public String resolveUnitBaseName(String eventId) {
        String clean = eventId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
        return "nexus-reminder-" + clean;
    }

    public boolean armTimer(ReminderEvent event) {
        if (event == null) return false;
        boolean systemScope = isSystemScope();
        Path unitDir = resolveUnitDirectory(systemScope);
        String baseName = resolveUnitBaseName(event.id());

        Path timerPath = unitDir.resolve(baseName + ".timer");
        Path servicePath = unitDir.resolve(baseName + ".service");

        // 1. Collect pending trigger timestamps
        List<String> onCalendarEntries = new ArrayList<>();
        ZoneId zone = event.timezone() != null ? event.timezone() : ZoneId.systemDefault();

        for (ReminderStage stage : event.stages()) {
            if (stage.status() == ReminderStageStatus.PENDING && stage.triggerAt() != null) {
                ZonedDateTime zdt = stage.triggerAt().atZone(zone);
                onCalendarEntries.add(zdt.format(SYSTEMD_DATE_FORMAT) + " " + zone.getId());
            }
        }

        if (onCalendarEntries.isEmpty()) {
            log.info("No pending stages for reminder {}. Skipping timer arm.", event.id());
            disarmTimer(event.id());
            return true;
        }

        // 2. Generate Timer Unit
        StringBuilder timerContent = new StringBuilder();
        timerContent.append("[Unit]\n");
        timerContent.append("Description=Nexus Reminder Timer: ").append(event.title()).append(" (").append(event.id()).append(")\n");
        timerContent.append("Documentation=https://github.com/nexus\n\n");
        timerContent.append("[Timer]\n");
        timerContent.append("Persistent=true\n");
        for (String entry : onCalendarEntries) {
            timerContent.append("OnCalendar=").append(entry).append("\n");
        }
        timerContent.append("Unit=").append(baseName).append(".service\n\n");
        timerContent.append("[Install]\n");
        timerContent.append("WantedBy=timers.target\n");

        // 3. Generate Service Unit
        String binaryPath = resolveBinaryPath();
        StringBuilder serviceContent = new StringBuilder();
        serviceContent.append("[Unit]\n");
        serviceContent.append("Description=Nexus Reminder Dispatcher: ").append(event.title()).append(" (").append(event.id()).append(")\n\n");
        serviceContent.append("[Service]\n");
        serviceContent.append("Type=oneshot\n");
        if (systemScope) {
            serviceContent.append("User=root\n");
            serviceContent.append("WorkingDirectory=/root\n");
            serviceContent.append("Environment=HOME=/root\n");
        }
        serviceContent.append("ExecStart=").append(binaryPath).append(" reminder dispatch ").append(event.id()).append("\n");
        serviceContent.append("StandardOutput=journal\n");
        serviceContent.append("StandardError=journal\n");

        try {
            Files.createDirectories(unitDir);
            writeUnitFile(timerPath, timerContent.toString(), systemScope);
            writeUnitFile(servicePath, serviceContent.toString(), systemScope);

            // Reload and enable timer
            runSystemctl(systemScope, List.of("daemon-reload"));
            runSystemctl(systemScope, List.of("enable", "--now", baseName + ".timer"));
            log.info("Successfully armed timer unit {} for {}", baseName, event.id());
            return true;
        } catch (Exception e) {
            log.error("Failed to arm systemd timer for {}: {}", event.id(), e.getMessage());
            return false;
        }
    }

    public boolean disarmTimer(String eventId) {
        if (eventId == null || eventId.isBlank()) return false;
        boolean systemScope = isSystemScope();
        Path unitDir = resolveUnitDirectory(systemScope);
        String baseName = resolveUnitBaseName(eventId);

        Path timerPath = unitDir.resolve(baseName + ".timer");
        Path servicePath = unitDir.resolve(baseName + ".service");

        try {
            runSystemctl(systemScope, List.of("disable", "--now", baseName + ".timer"));
            deleteUnitFile(timerPath, systemScope);
            deleteUnitFile(servicePath, systemScope);
            runSystemctl(systemScope, List.of("daemon-reload"));
            log.info("Disarmed and removed timer units for {}", eventId);
            return true;
        } catch (Exception e) {
            log.warn("Failed to disarm timer for {}: {}", eventId, e.getMessage());
            return false;
        }
    }

    private void writeUnitFile(Path path, String content, boolean systemScope) throws IOException, InterruptedException {
        try {
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (Exception e) {
            if (systemScope) {
                File tmp = File.createTempFile("nexus-rem-", ".unit");
                Files.writeString(tmp.toPath(), content, StandardCharsets.UTF_8);
                new ProcessBuilder("sudo", "cp", tmp.getAbsolutePath(), path.toString()).start().waitFor();
                new ProcessBuilder("sudo", "chmod", "644", path.toString()).start().waitFor();
                tmp.delete();
            } else {
                throw e;
            }
        }
    }

    private void deleteUnitFile(Path path, boolean systemScope) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception e) {
            if (systemScope) {
                try {
                    new ProcessBuilder("sudo", "rm", "-f", path.toString()).start().waitFor();
                } catch (Exception ignored) {}
            }
        }
    }

    private int runSystemctl(boolean systemScope, List<String> args) {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux")) {
            return 0; // Not running on Linux
        }
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("systemctl");
            if (!systemScope) {
                cmd.add("--user");
            }
            cmd.addAll(args);
            Process proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            return proc.waitFor();
        } catch (Exception e) {
            log.debug("systemctl execution failed: {} ({})", args, e.getMessage());
            return -1;
        }
    }

    public static String resolveBinaryPath() {
        String prop = System.getProperty("nexus.binary.path");
        if (prop != null && !prop.isBlank()) {
            return prop;
        }
        File standard = new File("/usr/local/bin/nexus");
        if (standard.exists() && standard.canExecute()) {
            return standard.getAbsolutePath();
        }
        return "nexus";
    }
}
