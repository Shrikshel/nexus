package com.nexus.adapters.tui.screens;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.nexus.adapters.tui.NexusTuiScreen;
import com.nexus.adapters.tui.TuiDrawingHelper;
import com.nexus.capabilities.reminder.dto.ReminderEvent;
import com.nexus.capabilities.reminder.storage.ReminderStorageManager;
import com.nexus.capabilities.scheduler.timer.dto.TimerScope;
import com.nexus.capabilities.system.dto.SystemInfoReport;
import com.nexus.capabilities.system.dto.SystemUptimeReport;
import com.nexus.platform.SystemCollector;
import com.nexus.platform.scheduler.TimerStateManager;
import com.nexus.platform.service.DaemonServiceManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Screen 1: Executive Overview Dashboard providing real-time host telemetry,
 * resource utilization, daemon supervisor status, and scheduler summaries.
 */
@Component
public class OverviewScreen implements NexusTuiScreen {

    private final SystemCollector systemCollector;
    private final DaemonServiceManager daemonServiceManager;
    private final TimerStateManager timerStateManager;
    private final ReminderStorageManager reminderStorageManager;

    // Cached telemetry data
    private SystemInfoReport systemInfo;
    private SystemUptimeReport systemUptime;
    private boolean systemDaemonRunning;
    private boolean userDaemonRunning;
    private String supervisorName = "N/A";
    private int totalTimers = 0;
    private int totalReminders = 0;
    private String nextUpcomingSummary = "None";
    private Instant lastRefreshed = Instant.now();

    @Autowired
    public OverviewScreen(
            @Autowired(required = false) SystemCollector systemCollector,
            @Autowired(required = false) DaemonServiceManager daemonServiceManager,
            @Autowired(required = false) TimerStateManager timerStateManager,
            @Autowired(required = false) ReminderStorageManager reminderStorageManager
    ) {
        this.systemCollector = systemCollector;
        this.daemonServiceManager = daemonServiceManager;
        this.timerStateManager = timerStateManager;
        this.reminderStorageManager = reminderStorageManager;
        refresh();
    }

    @Override
    public String getTitle() {
        return "1: Overview";
    }

    @Override
    public String getActionShortcuts() {
        return "[r] Refresh";
    }

    @Override
    public synchronized void refresh() {
        lastRefreshed = Instant.now();

        // Host telemetry
        if (systemCollector != null) {
            try {
                this.systemInfo = systemCollector.collectSystemInfo();
                this.systemUptime = systemCollector.collectSystemUptime();
            } catch (Exception ignored) {}
        }

        // Daemon supervisor status
        if (daemonServiceManager != null && daemonServiceManager.isSupported()) {
            try {
                supervisorName = daemonServiceManager.getActiveProvider().getPlatformName();
                systemDaemonRunning = daemonServiceManager.isServiceRunning(true);
                userDaemonRunning = daemonServiceManager.isServiceRunning(false);
            } catch (Exception ignored) {}
        }

        // Timers count
        if (timerStateManager != null) {
            try {
                int userCount = timerStateManager.loadState(TimerScope.USER).timers().size();
                int sysCount = timerStateManager.loadState(TimerScope.SYSTEM).timers().size();
                this.totalTimers = userCount + sysCount;
            } catch (Exception ignored) {}
        }

        // Reminders count & next upcoming
        if (reminderStorageManager != null) {
            try {
                List<ReminderEvent> events = reminderStorageManager.loadAll();
                this.totalReminders = events.size();
                Optional<ReminderEvent> next = events.stream()
                        .filter(e -> e.state() != null && "ACTIVE".equalsIgnoreCase(e.state().status()))
                        .filter(e -> e.targetDate() != null)
                        .min((a, b) -> a.targetDate().compareTo(b.targetDate()));
                if (next.isPresent()) {
                    ReminderEvent r = next.get();
                    nextUpcomingSummary = r.id() + ": " + TuiDrawingHelper.truncate(r.title(), 20) + " (" + r.targetDate() + ")";
                } else {
                    nextUpcomingSummary = events.isEmpty() ? "No reminders configured" : "All completed/inactive";
                }
            } catch (Exception ignored) {}
        }
    }

    @Override
    public synchronized void render(TextGraphics tg, TerminalSize size, int startRow, int availableRows) {
        int width = size.getColumns();
        int height = availableRows;

        if (width < 60 || height < 12) {
            tg.setForegroundColor(TextColor.ANSI.RED_BRIGHT);
            tg.putString(2, startRow + 2, "Window too small (" + width + "x" + size.getRows() + "). Minimum 60x15 required.");
            return;
        }

        boolean twoColumns = width >= 86;
        int halfWidth = twoColumns ? (width - 4) / 2 : width - 4;
        int panelHeight = twoColumns ? Math.min((height - 2) / 2, 8) : 7;

        // Panel 1: Host Telemetry
        renderHostTelemetryPanel(tg, 2, startRow, halfWidth, panelHeight);

        // Panel 2: Memory & Resources
        int p2X = twoColumns ? 2 + halfWidth + 1 : 2;
        int p2Y = twoColumns ? startRow : startRow + panelHeight;
        renderResourcePanel(tg, p2X, p2Y, halfWidth, panelHeight);

        // Panel 3: Daemon & Services
        int p3X = 2;
        int p3Y = twoColumns ? startRow + panelHeight : startRow + (panelHeight * 2);
        renderDaemonPanel(tg, p3X, p3Y, halfWidth, panelHeight);

        // Panel 4: Scheduler & Automation
        int p4X = twoColumns ? 2 + halfWidth + 1 : 2;
        int p4Y = twoColumns ? startRow + panelHeight : startRow + (panelHeight * 3);
        renderSchedulerPanel(tg, p4X, p4Y, halfWidth, panelHeight);
    }

    private void renderHostTelemetryPanel(TextGraphics tg, int x, int y, int w, int h) {
        TuiDrawingHelper.drawBox(tg, x, y, w, h, "Host & System Telemetry", TextColor.ANSI.CYAN, TextColor.ANSI.CYAN_BRIGHT);

        String host = systemInfo != null ? systemInfo.hostname() : "localhost";
        String os = systemInfo != null ? systemInfo.osName() + " " + systemInfo.osArch() : System.getProperty("os.name");
        String kernel = systemInfo != null ? systemInfo.kernelVersion() : System.getProperty("os.version");
        String uptime = systemUptime != null ? systemUptime.hostUptimeFormatted() : "N/A";

        drawKeyValue(tg, x + 2, y + 1, "Hostname:", host, w - 4);
        drawKeyValue(tg, x + 2, y + 2, "OS / Arch:", os, w - 4);
        drawKeyValue(tg, x + 2, y + 3, "Kernel:", kernel, w - 4);
        drawKeyValue(tg, x + 2, y + 4, "Host Uptime:", uptime, w - 4);
        if (h >= 7 && systemInfo != null) {
            drawKeyValue(tg, x + 2, y + 5, "Timezone:", systemInfo.timezone(), w - 4);
        }
    }

    private void renderResourcePanel(TextGraphics tg, int x, int y, int w, int h) {
        TuiDrawingHelper.drawBox(tg, x, y, w, h, "Memory & Compute Resources", TextColor.ANSI.BLUE, TextColor.ANSI.BLUE_BRIGHT);

        int cores = systemInfo != null ? systemInfo.availableProcessors() : Runtime.getRuntime().availableProcessors();
        drawKeyValue(tg, x + 2, y + 1, "CPU Cores:", cores + " logical cores", w - 4);

        if (systemInfo != null && systemInfo.totalPhysicalMemoryBytes() > 0) {
            long total = systemInfo.totalPhysicalMemoryBytes();
            long free = systemInfo.freePhysicalMemoryBytes();
            long used = Math.max(0, total - free);
            double pct = (used * 100.0) / total;

            drawKeyValue(tg, x + 2, y + 2, "RAM Used:",
                    String.format("%.1f%% (%s / %s)", pct, TuiDrawingHelper.formatBytes(used), TuiDrawingHelper.formatBytes(total)), w - 4);

            int barWidth = Math.min(w - 6, 28);
            if (barWidth >= 10 && h >= 5) {
                TextColor barColor = pct > 85 ? TextColor.ANSI.RED_BRIGHT : (pct > 70 ? TextColor.ANSI.YELLOW_BRIGHT : TextColor.ANSI.GREEN_BRIGHT);
                TuiDrawingHelper.drawProgressBar(tg, x + 2, y + 3, barWidth, pct, barColor, TextColor.ANSI.BLACK_BRIGHT);
            }

            if (h >= 6) {
                drawKeyValue(tg, x + 2, y + 4, "RAM Free:", TuiDrawingHelper.formatBytes(free), w - 4);
            }
        } else {
            long totalMem = Runtime.getRuntime().totalMemory();
            long freeMem = Runtime.getRuntime().freeMemory();
            drawKeyValue(tg, x + 2, y + 2, "JVM Memory:", TuiDrawingHelper.formatBytes(totalMem - freeMem) + " used", w - 4);
        }
    }

    private void renderDaemonPanel(TextGraphics tg, int x, int y, int w, int h) {
        TuiDrawingHelper.drawBox(tg, x, y, w, h, "Nexus Agent & Daemon Supervisor", TextColor.ANSI.MAGENTA, TextColor.ANSI.MAGENTA_BRIGHT);

        drawKeyValue(tg, x + 2, y + 1, "Supervisor:", supervisorName, w - 4);

        // System daemon indicator
        tg.setForegroundColor(TextColor.ANSI.WHITE);
        tg.putString(x + 2, y + 2, "System Daemon: ");
        if (systemDaemonRunning) {
            tg.setForegroundColor(TextColor.ANSI.GREEN_BRIGHT);
            tg.enableModifiers(SGR.BOLD);
            tg.putString(x + 17, y + 2, "● ACTIVE (Running)");
        } else {
            tg.setForegroundColor(TextColor.ANSI.RED_BRIGHT);
            tg.putString(x + 17, y + 2, "○ INACTIVE (Stopped)");
        }
        tg.clearModifiers();

        // User daemon indicator
        tg.setForegroundColor(TextColor.ANSI.WHITE);
        tg.putString(x + 2, y + 3, "User Daemon:   ");
        if (userDaemonRunning) {
            tg.setForegroundColor(TextColor.ANSI.GREEN_BRIGHT);
            tg.enableModifiers(SGR.BOLD);
            tg.putString(x + 17, y + 3, "● ACTIVE (Running)");
        } else {
            tg.setForegroundColor(TextColor.ANSI.WHITE);
            tg.putString(x + 17, y + 3, "○ INACTIVE");
        }
        tg.clearModifiers();

        if (h >= 6) {
            String agentUp = systemUptime != null ? systemUptime.agentUptimeFormatted() : "N/A";
            drawKeyValue(tg, x + 2, y + 4, "Agent Uptime:", agentUp, w - 4);
        }
    }

    private void renderSchedulerPanel(TextGraphics tg, int x, int y, int w, int h) {
        TuiDrawingHelper.drawBox(tg, x, y, w, h, "Scheduled Automations & Summary", TextColor.ANSI.YELLOW, TextColor.ANSI.YELLOW_BRIGHT);

        drawKeyValue(tg, x + 2, y + 1, "Active Timers:", totalTimers + " scheduled", w - 4);
        drawKeyValue(tg, x + 2, y + 2, "Reminders:", totalReminders + " events registered", w - 4);
        drawKeyValue(tg, x + 2, y + 3, "Next Event:", nextUpcomingSummary, w - 4);

        if (h >= 6) {
            tg.setForegroundColor(TextColor.ANSI.BLACK_BRIGHT);
            tg.putString(x + 2, y + 4, "Press [2] for Timers | [3] for Reminders");
        }
    }

    private void drawKeyValue(TextGraphics tg, int x, int y, String label, String value, int maxWidth) {
        tg.setForegroundColor(TextColor.ANSI.WHITE);
        tg.putString(x, y, label + " ");
        int labelLen = label.length() + 1;
        int valSpace = Math.max(0, maxWidth - labelLen);
        tg.setForegroundColor(TextColor.ANSI.CYAN_BRIGHT);
        tg.putString(x + labelLen, y, TuiDrawingHelper.truncate(value, valSpace));
    }

    @Override
    public boolean handleInput(KeyStroke key) {
        // OverviewScreen delegates standard keys to launcher
        return false;
    }
}
