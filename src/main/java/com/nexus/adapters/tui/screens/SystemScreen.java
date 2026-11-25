package com.nexus.adapters.tui.screens;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.nexus.adapters.tui.NexusTuiScreen;
import com.nexus.adapters.tui.TuiDrawingHelper;
import com.nexus.capabilities.process.ProcessListCapability;
import com.nexus.capabilities.process.dto.ProcessInfo;
import com.nexus.capabilities.process.dto.ProcessListReport;
import com.nexus.capabilities.storage.dto.DiskListReport;
import com.nexus.capabilities.storage.dto.DiskPartitionReport;
import com.nexus.capabilities.storage.zfs.ZfsPoolListCapability;
import com.nexus.capabilities.system.audit.SystemAuditCapability;
import com.nexus.capabilities.system.audit.dto.AuditCheckItem;
import com.nexus.capabilities.system.audit.dto.AuditSeverity;
import com.nexus.capabilities.system.audit.dto.SystemAuditReport;
import com.nexus.capabilities.system.dto.SystemInfoReport;
import com.nexus.core.capability.CapabilityResult;
import com.nexus.core.capability.ExecutionContext;
import com.nexus.core.capability.InvocationSource;
import com.nexus.core.security.Subject;
import com.nexus.platform.StorageCollector;
import com.nexus.platform.SystemCollector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Screen 4: Host System Audit & Storage Screen.
 * Provides interactive inspection across 3 distinct sub-views:
 *   1. DISKS: Filesystem mounts, capacities, and visual utilization progress bars.
 *   2. AUDIT: Deep system health diagnostics and security audit checklist.
 *   3. PROCESSES: Top active host processes, PID, CPU time, and commands.
 */
@Component
public class SystemScreen implements NexusTuiScreen {

    public enum ViewMode {
        DISKS("1: Disks & Storage"),
        AUDIT("2: Health Audit"),
        PROCESSES("3: Process Monitor");

        private final String label;
        ViewMode(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    private final SystemCollector systemCollector;
    private final StorageCollector storageCollector;
    private final SystemAuditCapability auditCapability;
    private final ProcessListCapability processListCapability;
    private final ZfsPoolListCapability zfsPoolCapability;

    private ViewMode activeView = ViewMode.DISKS;
    private int selectedIndex = 0;
    private int scrollOffset = 0;

    // Telemetry caches
    private SystemInfoReport systemInfo;
    private final List<DiskPartitionReport> partitions = new ArrayList<>();
    private DiskListReport diskReport;
    private final List<AuditCheckItem> auditChecks = new ArrayList<>();
    private SystemAuditReport auditReport;
    private final List<ProcessInfo> processes = new ArrayList<>();
    private ProcessListReport processReport;

    private final java.util.concurrent.atomic.AtomicBoolean auditRunning = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicBoolean processLoading = new java.util.concurrent.atomic.AtomicBoolean(false);

    private String toastMessage = null;
    private boolean toastIsError = false;
    private Instant toastTimestamp = null;

    public SystemScreen(SystemCollector systemCollector) {
        this(systemCollector, null, null, null, null);
    }

    @Autowired
    public SystemScreen(
            @Autowired(required = false) SystemCollector systemCollector,
            @Autowired(required = false) StorageCollector storageCollector,
            @Autowired(required = false) SystemAuditCapability auditCapability,
            @Autowired(required = false) ProcessListCapability processListCapability,
            @Autowired(required = false) ZfsPoolListCapability zfsPoolCapability
    ) {
        this.systemCollector = systemCollector;
        this.storageCollector = storageCollector;
        this.auditCapability = auditCapability;
        this.processListCapability = processListCapability;
        this.zfsPoolCapability = zfsPoolCapability;
        refresh();
    }

    @Override
    public String getTitle() {
        return "4: System";
    }

    @Override
    public String getActionShortcuts() {
        return "[▲/▼ / j/k] Nav  │  [v] Sub-View  │  [a] Deep Audit  │  [r] Refresh";
    }

    public ViewMode getActiveView() {
        return activeView;
    }

    public void setActiveView(ViewMode view) {
        this.activeView = view != null ? view : ViewMode.DISKS;
        this.selectedIndex = 0;
        this.scrollOffset = 0;
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public void setSelectedIndex(int index) {
        int max = getActiveItemCount();
        if (max > 0) {
            this.selectedIndex = Math.max(0, Math.min(index, max - 1));
        }
    }

    public List<DiskPartitionReport> getPartitions() {
        return partitions;
    }

    public List<AuditCheckItem> getAuditChecks() {
        return auditChecks;
    }

    public List<ProcessInfo> getProcesses() {
        return processes;
    }

    public String getToastMessage() {
        return toastMessage;
    }

    private int getActiveItemCount() {
        return switch (activeView) {
            case DISKS -> partitions.size();
            case AUDIT -> auditChecks.size();
            case PROCESSES -> processes.size();
        };
    }

    public boolean isAuditRunning() {
        return auditRunning.get();
    }

    public void triggerAuditAsync() {
        if (auditCapability == null || !auditRunning.compareAndSet(false, true)) {
            return;
        }
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                ExecutionContext ctx = ExecutionContext.create(InvocationSource.CLI, Subject.localCli(), Duration.ofSeconds(15));
                CapabilityResult<SystemAuditReport> res = auditCapability.execute(ctx, null);
                if (res != null && res.payload() != null) {
                    synchronized (SystemScreen.this) {
                        this.auditReport = res.payload();
                        auditChecks.clear();
                        if (auditReport.checks() != null) {
                            auditChecks.addAll(auditReport.checks());
                        }
                        toastMessage = "✔ System health audit complete (" + auditChecks.size() + " checks)";
                        toastIsError = false;
                        toastTimestamp = Instant.now();
                    }
                }
            } catch (Exception e) {
                synchronized (SystemScreen.this) {
                    toastMessage = "❌ Health audit error: " + e.getMessage();
                    toastIsError = true;
                    toastTimestamp = Instant.now();
                }
            } finally {
                auditRunning.set(false);
            }
        });
    }

    public void triggerProcessListAsync() {
        if (processListCapability == null || !processLoading.compareAndSet(false, true)) {
            return;
        }
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                ExecutionContext ctx = ExecutionContext.create(InvocationSource.CLI, Subject.localCli(), Duration.ofSeconds(10));
                CapabilityResult<ProcessListReport> res = processListCapability.execute(ctx, null);
                if (res != null && res.payload() != null) {
                    synchronized (SystemScreen.this) {
                        this.processReport = res.payload();
                        processes.clear();
                        if (processReport.processes() != null) {
                            processes.addAll(processReport.processes());
                        }
                    }
                }
            } catch (Exception ignored) {
            } finally {
                processLoading.set(false);
            }
        });
    }

    @Override
    public synchronized void refresh() {
        if (toastTimestamp != null && java.time.Duration.between(toastTimestamp, Instant.now()).getSeconds() >= 4) {
            toastMessage = null;
            toastTimestamp = null;
        }

        // Refresh host info
        if (systemCollector != null) {
            try {
                this.systemInfo = systemCollector.collectSystemInfo();
            } catch (Exception ignored) {}
        }

        // Refresh storage mounts
        partitions.clear();
        if (storageCollector != null) {
            try {
                this.diskReport = storageCollector.collectDiskList();
                if (diskReport != null && diskReport.partitions() != null) {
                    partitions.addAll(diskReport.partitions());
                }
            } catch (Exception e) {
                fallbackLoadDisks();
            }
        } else {
            fallbackLoadDisks();
        }

        // Non-blocking trigger for background audit
        if (auditCapability != null && auditChecks.isEmpty() && !auditRunning.get()) {
            triggerAuditAsync();
        }

        // Non-blocking trigger for background process listing
        if (processListCapability != null && processes.isEmpty() && !processLoading.get()) {
            triggerProcessListAsync();
        }

        int max = getActiveItemCount();
        if (selectedIndex >= max) {
            selectedIndex = Math.max(0, max - 1);
        }
    }

    private void fallbackLoadDisks() {
        File[] roots = File.listRoots();
        if (roots != null) {
            for (File r : roots) {
                long total = r.getTotalSpace();
                long free = r.getFreeSpace();
                long used = Math.max(0, total - free);
                double pct = total > 0 ? (used * 100.0) / total : 0.0;
                partitions.add(new DiskPartitionReport(r.getAbsolutePath(), r.getName(), "local", total, free, used, pct));
            }
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

        // Sub-bar with view mode selectors
        renderSubBar(tg, 2, startRow, width - 4);

        int bodyStart = startRow + 2;
        int remainingHeight = height - 2;
        int tableHeight = Math.max(6, (remainingHeight * 55) / 100);
        int inspectorHeight = Math.max(6, remainingHeight - tableHeight);

        // Render Active Table View
        switch (activeView) {
            case DISKS -> renderDisksTable(tg, 2, bodyStart, width - 4, tableHeight);
            case AUDIT -> renderAuditTable(tg, 2, bodyStart, width - 4, tableHeight);
            case PROCESSES -> renderProcessesTable(tg, 2, bodyStart, width - 4, tableHeight);
        }

        // Render Active Inspector View
        int inspectorY = bodyStart + tableHeight;
        renderInspectorPane(tg, 2, inspectorY, width - 4, inspectorHeight);
    }

    private void renderSubBar(TextGraphics tg, int x, int y, int width) {
        tg.setForegroundColor(TextColor.ANSI.WHITE);
        tg.putString(x, y, "View: ");

        int currX = x + 6;
        for (ViewMode vm : ViewMode.values()) {
            boolean active = (this.activeView == vm);
            String label = " [" + vm.getLabel() + "] ";
            if (active) {
                tg.setBackgroundColor(TextColor.ANSI.CYAN);
                tg.setForegroundColor(TextColor.ANSI.BLACK);
                tg.enableModifiers(SGR.BOLD);
                tg.putString(currX, y, label);
                tg.clearModifiers();
                tg.setBackgroundColor(TextColor.ANSI.DEFAULT);
            } else {
                tg.setForegroundColor(TextColor.ANSI.WHITE);
                tg.putString(currX, y, label);
            }
            currX += label.length() + 1;
        }

        String host = systemInfo != null ? systemInfo.hostname() : "localhost";
        tg.setForegroundColor(TextColor.ANSI.BLACK_BRIGHT);
        tg.putString(currX + 1, y, "(Press 'v' to switch) │ Host: " + host);
    }

    private void renderDisksTable(TextGraphics tg, int x, int y, int width, int height) {
        String boxTitle = "Filesystem Mounts & Storage Capacity";
        TuiDrawingHelper.drawBox(tg, x, y, width, height, boxTitle, TextColor.ANSI.CYAN, TextColor.ANSI.CYAN_BRIGHT);

        int headerY = y + 1;
        tg.setForegroundColor(TextColor.ANSI.CYAN_BRIGHT);
        tg.enableModifiers(SGR.BOLD);
        String header = String.format("  %-18s %-8s %-12s %-12s %-12s %s",
                "MOUNT POINT", "FS TYPE", "TOTAL", "USED", "FREE", "CAPACITY GAUGE");
        tg.putString(x + 1, headerY, TuiDrawingHelper.truncate(header, width - 2));
        tg.clearModifiers();

        tg.setForegroundColor(TextColor.ANSI.BLACK_BRIGHT);
        tg.putString(x + 1, headerY + 1, "─".repeat(Math.max(0, width - 2)));

        int visibleRows = height - 4; // leave 1 row for aggregate footer inside box
        if (visibleRows <= 0) return;

        updateViewportBounds(visibleRows, partitions.size());

        if (partitions.isEmpty()) {
            tg.setForegroundColor(TextColor.ANSI.WHITE);
            tg.putString(x + 4, headerY + 2, "No mounted storage volumes detected.");
            return;
        }

        int rowY = headerY + 2;
        for (int i = 0; i < visibleRows; i++) {
            int pIndex = scrollOffset + i;
            if (pIndex >= partitions.size()) break;

            DiskPartitionReport p = partitions.get(pIndex);
            boolean isSelected = (pIndex == selectedIndex);

            String cursor = isSelected ? "► " : "  ";
            String mountStr = p.mountPoint() != null ? p.mountPoint() : "-";
            String typeStr = p.filesystemType() != null ? p.filesystemType() : "auto";
            String totalStr = TuiDrawingHelper.formatBytes(p.totalBytes());
            String usedStr = TuiDrawingHelper.formatBytes(p.usedBytes());
            String freeStr = TuiDrawingHelper.formatBytes(p.usableBytes());
            String pctStr = String.format("%.1f%%", p.usagePercentage());

            String textCols = String.format("%s%-18s %-8s %-12s %-12s %-12s ",
                    cursor,
                    TuiDrawingHelper.truncate(mountStr, 18),
                    TuiDrawingHelper.truncate(typeStr, 8),
                    TuiDrawingHelper.truncate(totalStr, 12),
                    TuiDrawingHelper.truncate(usedStr, 12),
                    TuiDrawingHelper.truncate(freeStr, 12)
            );

            if (isSelected) {
                tg.setBackgroundColor(TextColor.ANSI.BLUE);
                tg.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
                tg.enableModifiers(SGR.BOLD);
                tg.putString(x + 2, rowY, TuiDrawingHelper.padRight(textCols, width - 4));
                tg.clearModifiers();
                tg.setBackgroundColor(TextColor.ANSI.DEFAULT);
            } else {
                tg.setForegroundColor(TextColor.ANSI.WHITE);
                tg.putString(x + 2, rowY, textCols);
            }

            // Draw progress bar in the remaining horizontal space
            int barX = x + 2 + textCols.length();
            int barWidth = Math.min(22, width - (barX - x) - 10);
            if (barWidth >= 6) {
                TextColor barColor = p.usagePercentage() > 85 ? TextColor.ANSI.RED_BRIGHT
                        : (p.usagePercentage() > 70 ? TextColor.ANSI.YELLOW_BRIGHT : TextColor.ANSI.GREEN_BRIGHT);
                TuiDrawingHelper.drawProgressBar(tg, barX, rowY, barWidth, p.usagePercentage(), barColor, TextColor.ANSI.BLACK_BRIGHT);
                tg.setForegroundColor(barColor);
                tg.putString(barX + barWidth + 1, rowY, pctStr);
            }

            rowY++;
        }

        // Aggregate storage footer inside box
        int footerY = y + height - 2;
        long aggTotal = diskReport != null ? diskReport.aggregateTotalBytes() : 0;
        long aggUsable = diskReport != null ? diskReport.aggregateUsableBytes() : 0;
        double aggPct = diskReport != null ? diskReport.aggregateUsagePercentage() : 0.0;
        String aggSummary = String.format("Aggregate Storage: %s Total │ %s Free │ Overall Usage: %.1f%%",
                TuiDrawingHelper.formatBytes(aggTotal), TuiDrawingHelper.formatBytes(aggUsable), aggPct);
        tg.setForegroundColor(TextColor.ANSI.BLACK_BRIGHT);
        tg.putString(x + 2, footerY, TuiDrawingHelper.truncate(aggSummary, width - 4));
    }

    private void renderAuditTable(TextGraphics tg, int x, int y, int width, int height) {
        String status = auditReport != null ? auditReport.overallStatus() : "PENDING";
        int pass = auditReport != null ? auditReport.passedCount() : 0;
        int warn = auditReport != null ? auditReport.warnCount() : 0;
        int crit = auditReport != null ? auditReport.criticalCount() : 0;

        String boxTitle = String.format("System Health Audit [%s: %d pass, %d warn, %d crit]", status, pass, warn, crit);
        if (auditRunning.get()) {
            boxTitle += " [⠋ Evaluating in background...]";
        }
        TextColor borderColor = "PASSED".equalsIgnoreCase(status) ? TextColor.ANSI.GREEN : TextColor.ANSI.YELLOW;
        TuiDrawingHelper.drawBox(tg, x, y, width, height, boxTitle, borderColor, TextColor.ANSI.WHITE_BRIGHT);

        int headerY = y + 1;
        tg.setForegroundColor(TextColor.ANSI.CYAN_BRIGHT);
        tg.enableModifiers(SGR.BOLD);
        String header = String.format("  %-10s %-14s %-26s %s", "SEVERITY", "CATEGORY", "CHECK NAME", "DIAGNOSTIC STATUS");
        tg.putString(x + 1, headerY, TuiDrawingHelper.truncate(header, width - 2));
        tg.clearModifiers();

        tg.setForegroundColor(TextColor.ANSI.BLACK_BRIGHT);
        tg.putString(x + 1, headerY + 1, "─".repeat(Math.max(0, width - 2)));

        int visibleRows = height - 3;
        if (visibleRows <= 0) return;

        updateViewportBounds(visibleRows, auditChecks.size());

        if (auditChecks.isEmpty()) {
            tg.setForegroundColor(TextColor.ANSI.WHITE);
            if (auditRunning.get()) {
                tg.putString(x + 4, headerY + 2, "⠋ Evaluating system health in background (disks, memory, network, services)...");
            } else {
                tg.putString(x + 4, headerY + 2, "No audit checks loaded. Press [a] to trigger deep health audit.");
            }
            return;
        }

        int rowY = headerY + 2;
        for (int i = 0; i < visibleRows; i++) {
            int aIndex = scrollOffset + i;
            if (aIndex >= auditChecks.size()) break;

            AuditCheckItem item = auditChecks.get(aIndex);
            boolean isSelected = (aIndex == selectedIndex);

            String cursor = isSelected ? "► " : "  ";
            AuditSeverity sev = item.severity() != null ? item.severity() : AuditSeverity.PASS;
            String cat = item.category() != null ? item.category() : "-";
            String name = item.checkName() != null ? item.checkName() : "-";
            String msg = item.message() != null ? item.message() : (sev == AuditSeverity.PASS ? "PASSED" : "FAILED");

            String sevBadge = (sev == AuditSeverity.PASS) ? "✔ PASS" : (sev == AuditSeverity.WARN ? "⚠ WARN" : "✖ CRIT");

            String row = String.format("%s%-10s %-14s %-26s %s",
                    cursor,
                    sevBadge,
                    TuiDrawingHelper.truncate(cat, 14),
                    TuiDrawingHelper.truncate(name, 26),
                    msg
            );
            String paddedRow = TuiDrawingHelper.padRight(TuiDrawingHelper.truncate(row, width - 4), width - 4);

            if (isSelected) {
                tg.setBackgroundColor(TextColor.ANSI.BLUE);
                tg.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
                tg.enableModifiers(SGR.BOLD);
                tg.putString(x + 2, rowY, paddedRow);
                tg.clearModifiers();
                tg.setBackgroundColor(TextColor.ANSI.DEFAULT);
            } else {
                TextColor color = (sev == AuditSeverity.PASS) ? TextColor.ANSI.WHITE : (sev == AuditSeverity.WARN ? TextColor.ANSI.YELLOW_BRIGHT : TextColor.ANSI.RED_BRIGHT);
                tg.setForegroundColor(color);
                tg.putString(x + 2, rowY, paddedRow);
            }

            rowY++;
        }
    }

    private void renderProcessesTable(TextGraphics tg, int x, int y, int width, int height) {
        int total = processReport != null ? processReport.totalProcessCount() : processes.size();
        String boxTitle = "Active Host Processes (" + total + " total running)";
        TuiDrawingHelper.drawBox(tg, x, y, width, height, boxTitle, TextColor.ANSI.MAGENTA, TextColor.ANSI.MAGENTA_BRIGHT);

        int headerY = y + 1;
        tg.setForegroundColor(TextColor.ANSI.CYAN_BRIGHT);
        tg.enableModifiers(SGR.BOLD);
        String header = String.format("  %-10s %-14s %-14s %-10s %s", "PID", "USER", "CPU TIME", "STATUS", "COMMAND");
        tg.putString(x + 1, headerY, TuiDrawingHelper.truncate(header, width - 2));
        tg.clearModifiers();

        tg.setForegroundColor(TextColor.ANSI.BLACK_BRIGHT);
        tg.putString(x + 1, headerY + 1, "─".repeat(Math.max(0, width - 2)));

        int visibleRows = height - 3;
        if (visibleRows <= 0) return;

        updateViewportBounds(visibleRows, processes.size());

        if (processes.isEmpty()) {
            tg.setForegroundColor(TextColor.ANSI.WHITE);
            if (processLoading.get()) {
                tg.putString(x + 4, headerY + 2, "⠋ Querying active processes in background...");
            } else {
                tg.putString(x + 4, headerY + 2, "No processes loaded. Press [r] to inspect process tree.");
            }
            return;
        }

        int rowY = headerY + 2;
        for (int i = 0; i < visibleRows; i++) {
            int prIndex = scrollOffset + i;
            if (prIndex >= processes.size()) break;

            ProcessInfo p = processes.get(prIndex);
            boolean isSelected = (prIndex == selectedIndex);

            String cursor = isSelected ? "► " : "  ";
            String pidStr = String.valueOf(p.pid());
            String userStr = p.user() != null ? p.user() : "-";
            String cpuStr = p.cpuDuration() != null ? p.cpuDuration() : "-";
            String statusStr = p.alive() ? "ALIVE" : "EXITED";
            String cmdStr = p.command() != null ? p.command() : "-";

            String row = String.format("%s%-10s %-14s %-14s %-10s %s",
                    cursor,
                    pidStr,
                    TuiDrawingHelper.truncate(userStr, 14),
                    TuiDrawingHelper.truncate(cpuStr, 14),
                    statusStr,
                    cmdStr
            );
            String paddedRow = TuiDrawingHelper.padRight(TuiDrawingHelper.truncate(row, width - 4), width - 4);

            if (isSelected) {
                tg.setBackgroundColor(TextColor.ANSI.BLUE);
                tg.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
                tg.enableModifiers(SGR.BOLD);
                tg.putString(x + 2, rowY, paddedRow);
                tg.clearModifiers();
                tg.setBackgroundColor(TextColor.ANSI.DEFAULT);
            } else {
                tg.setForegroundColor(TextColor.ANSI.WHITE);
                tg.putString(x + 2, rowY, paddedRow);
            }

            rowY++;
        }
    }

    private void updateViewportBounds(int visibleRows, int totalCount) {
        if (selectedIndex < scrollOffset) {
            scrollOffset = selectedIndex;
        } else if (selectedIndex >= scrollOffset + visibleRows) {
            scrollOffset = selectedIndex - visibleRows + 1;
        }
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, totalCount - visibleRows)));
    }

    private void renderInspectorPane(TextGraphics tg, int x, int y, int width, int height) {
        String title = switch (activeView) {
            case DISKS -> "Storage & Filesystem Telemetry";
            case AUDIT -> "Health Audit Diagnostic Details";
            case PROCESSES -> "Process Inspector & Command Line";
        };

        TuiDrawingHelper.drawBox(tg, x, y, width, height, title, TextColor.ANSI.CYAN, TextColor.ANSI.CYAN_BRIGHT);

        int currY = y + 1;
        switch (activeView) {
            case DISKS -> renderDisksInspector(tg, x + 2, currY, width - 4, height);
            case AUDIT -> renderAuditInspector(tg, x + 2, currY, width - 4, height);
            case PROCESSES -> renderProcessesInspector(tg, x + 2, currY, width - 4, height);
        }

        int toastY = y + height - 3;
        int promptY = y + height - 2;

        if (toastMessage != null) {
            tg.setForegroundColor(toastIsError ? TextColor.ANSI.RED_BRIGHT : TextColor.ANSI.GREEN_BRIGHT);
            tg.enableModifiers(SGR.BOLD);
            tg.putString(x + 2, toastY, TuiDrawingHelper.truncate(toastMessage, width - 4));
            tg.clearModifiers();
        }

        tg.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
        String actions = "[▲/▼ / j/k] Navigate  │  [v] Switch Sub-View  │  [r] Refresh  │  [a] Run Deep Audit";
        tg.putString(x + 2, promptY, TuiDrawingHelper.truncate(actions, width - 4));
    }

    private void renderDisksInspector(TextGraphics tg, int x, int y, int width, int height) {
        if (partitions.isEmpty() || selectedIndex < 0 || selectedIndex >= partitions.size()) {
            tg.setForegroundColor(TextColor.ANSI.BLACK_BRIGHT);
            tg.putString(x, y, "No filesystem partition selected.");
            return;
        }
        DiskPartitionReport p = partitions.get(selectedIndex);

        drawKeyValue(tg, x, y, "Mount Point:", p.mountPoint(), width);
        drawKeyValue(tg, x, y + 1, "Volume Name:", p.volumeName() != null ? p.volumeName() : "N/A", width);
        drawKeyValue(tg, x, y + 2, "Filesystem:", p.filesystemType() + "  │  Usage: " + String.format("%.1f%%", p.usagePercentage()), width);
        if (height >= 7) {
            drawKeyValue(tg, x, y + 3, "Space Breakdown:",
                    "Total: " + TuiDrawingHelper.formatBytes(p.totalBytes()) + " │ Used: " + TuiDrawingHelper.formatBytes(p.usedBytes()) + " │ Free: " + TuiDrawingHelper.formatBytes(p.usableBytes()), width);
        }
    }

    private void renderAuditInspector(TextGraphics tg, int x, int y, int width, int height) {
        if (auditChecks.isEmpty() || selectedIndex < 0 || selectedIndex >= auditChecks.size()) {
            tg.setForegroundColor(TextColor.ANSI.BLACK_BRIGHT);
            tg.putString(x, y, "No audit check item selected. Press [a] to run deep health audit.");
            return;
        }
        AuditCheckItem item = auditChecks.get(selectedIndex);
        AuditSeverity sev = item.severity() != null ? item.severity() : AuditSeverity.PASS;
        String sevText = (sev == AuditSeverity.PASS) ? "PASSED (Healthy)" : (sev == AuditSeverity.WARN ? "WARNING" : "CRITICAL");

        drawKeyValue(tg, x, y, "Check Target:", item.checkName() + " (" + item.category() + ")", width);
        drawKeyValue(tg, x, y + 1, "Audit Result:", sevText, width);
        drawKeyValue(tg, x, y + 2, "Diagnostic Info:", item.message() != null ? item.message() : "Passed within acceptable thresholds", width);
        if (height >= 7 && item.remediation() != null) {
            drawKeyValue(tg, x, y + 3, "Remediation:", item.remediation(), width);
        }
    }

    private void renderProcessesInspector(TextGraphics tg, int x, int y, int width, int height) {
        if (processes.isEmpty() || selectedIndex < 0 || selectedIndex >= processes.size()) {
            tg.setForegroundColor(TextColor.ANSI.BLACK_BRIGHT);
            tg.putString(x, y, "No process selected.");
            return;
        }
        ProcessInfo p = processes.get(selectedIndex);

        drawKeyValue(tg, x, y, "Process ID:", String.valueOf(p.pid()) + " (User: " + p.user() + ")", width);
        drawKeyValue(tg, x, y + 1, "CPU Elapsed:", p.cpuDuration() != null ? p.cpuDuration() : "N/A", width);
        drawKeyValue(tg, x, y + 2, "Execution State:", p.alive() ? "RUNNING (Alive)" : "STOPPED", width);
        if (height >= 7 && p.command() != null) {
            drawKeyValue(tg, x, y + 3, "Command Path:", p.command(), width);
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
    public synchronized boolean handleInput(KeyStroke key) {
        if (key == null) return false;

        KeyType type = key.getKeyType();
        Character ch = key.getCharacter();

        int max = getActiveItemCount();

        // Cursor navigation
        if (type == KeyType.ArrowUp || (type == KeyType.Character && (ch == 'k' || ch == 'K'))) {
            if (max > 0) {
                selectedIndex = Math.max(0, selectedIndex - 1);
            }
            return true;
        } else if (type == KeyType.ArrowDown || (type == KeyType.Character && (ch == 'j' || ch == 'J'))) {
            if (max > 0) {
                selectedIndex = Math.min(max - 1, selectedIndex + 1);
            }
            return true;
        } else if (type == KeyType.PageUp) {
            if (max > 0) {
                selectedIndex = Math.max(0, selectedIndex - 5);
            }
            return true;
        } else if (type == KeyType.PageDown) {
            if (max > 0) {
                selectedIndex = Math.min(max - 1, selectedIndex + 5);
            }
            return true;
        } else if (type == KeyType.Home) {
            selectedIndex = 0;
            return true;
        } else if (type == KeyType.End) {
            if (max > 0) {
                selectedIndex = max - 1;
            }
            return true;
        }

        // Sub-view cycling
        if (type == KeyType.Character && (ch == 'v' || ch == 'V')) {
            ViewMode[] modes = ViewMode.values();
            int nextOrd = (activeView.ordinal() + 1) % modes.length;
            setActiveView(modes[nextOrd]);
            refresh();
            toastMessage = "View: " + activeView.name();
            toastIsError = false;
            toastTimestamp = Instant.now();
            return true;
        }

        // Direct sub-view selection via number keys when on this screen (can also use 'v')
        // Note: global numbers 1-4 switch main tabs. So 'v' is primary.

        // Trigger deep audit
        if (type == KeyType.Character && (ch == 'a' || ch == 'A')) {
            setActiveView(ViewMode.AUDIT);
            triggerAuditAsync();
            toastMessage = "⏳ Running deep system health audit in background...";
            toastIsError = false;
            toastTimestamp = Instant.now();
            return true;
        }

        // Refresh active view
        if (type == KeyType.Character && (ch == 'r' || ch == 'R')) {
            if (activeView == ViewMode.AUDIT) {
                triggerAuditAsync();
            } else if (activeView == ViewMode.PROCESSES) {
                triggerProcessListAsync();
            } else {
                refresh();
            }
            toastMessage = "Refreshed " + activeView.name() + " telemetry.";
            toastIsError = false;
            toastTimestamp = Instant.now();
            return true;
        }

        return false;
    }
}
