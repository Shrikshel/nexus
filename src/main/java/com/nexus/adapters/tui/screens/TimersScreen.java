package com.nexus.adapters.tui.screens;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.nexus.adapters.tui.NexusTuiScreen;
import com.nexus.adapters.tui.TuiDrawingHelper;
import com.nexus.adapters.tui.dialogs.TuiFormModal;
import com.nexus.capabilities.scheduler.timer.dto.TimerCreateRequest;
import com.nexus.capabilities.scheduler.timer.dto.TimerCreateResult;
import com.nexus.capabilities.scheduler.timer.dto.TimerDeleteRequest;
import com.nexus.capabilities.scheduler.timer.dto.TimerDeleteResult;
import com.nexus.capabilities.scheduler.timer.dto.TimerInfo;
import com.nexus.capabilities.scheduler.timer.dto.TimerRunRequest;
import com.nexus.capabilities.scheduler.timer.dto.TimerRunResult;
import com.nexus.capabilities.scheduler.timer.dto.TimerScope;
import com.nexus.platform.scheduler.ScheduledTaskManager;
import com.nexus.platform.scheduler.TimerStateManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Screen 2: Interactive Timers Management Screen.
 * Provides live table listing with scope filtering (ALL/USER/SYSTEM), keyboard cursor navigation,
 * real-time timer inspector pane, on-demand execution ([x]/[Enter]), and deletion with confirmation.
 */
@Component
public class TimersScreen implements NexusTuiScreen {

    private final ScheduledTaskManager taskManager;
    private final TimerStateManager timerStateManager;

    private TimerScope currentScope = TimerScope.ALL;
    private final List<TimerInfo> cachedTimers = new ArrayList<>();
    private int selectedIndex = 0;
    private int scrollOffset = 0;

    // Inline action and confirmation states
    private boolean confirmingDelete = false;
    private TuiFormModal activeModal = null;
    private String toastMessage = null;
    private boolean toastIsError = false;
    private Instant toastTimestamp = null;

    public TimersScreen(ScheduledTaskManager taskManager) {
        this(taskManager, null);
    }

    @Autowired
    public TimersScreen(
            @Autowired(required = false) ScheduledTaskManager taskManager,
            @Autowired(required = false) TimerStateManager timerStateManager
    ) {
        this.taskManager = taskManager;
        this.timerStateManager = timerStateManager;
        refresh();
    }

    @Override
    public String getTitle() {
        return "2: Timers";
    }

    @Override
    public String getActionShortcuts() {
        return "[▲/▼] Nav  │  [c] Create  │  [x/Enter] Run  │  [d] Delete  │  [s] Scope  │  [r] Refresh";
    }

    public TimerScope getCurrentScope() {
        return currentScope;
    }

    public void setCurrentScope(TimerScope scope) {
        this.currentScope = scope != null ? scope : TimerScope.ALL;
        refresh();
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public void setSelectedIndex(int index) {
        if (!cachedTimers.isEmpty()) {
            this.selectedIndex = Math.max(0, Math.min(index, cachedTimers.size() - 1));
        }
    }

    public List<TimerInfo> getCachedTimers() {
        return cachedTimers;
    }

    public boolean isConfirmingDelete() {
        return confirmingDelete;
    }

    public String getToastMessage() {
        return toastMessage;
    }

    public TuiFormModal getActiveModal() {
        return activeModal;
    }

    public void setActiveModal(TuiFormModal modal) {
        this.activeModal = modal;
    }

    @Override
    public synchronized void refresh() {
        if (toastTimestamp != null && java.time.Duration.between(toastTimestamp, Instant.now()).getSeconds() >= 4) {
            toastMessage = null;
            toastTimestamp = null;
        }

        cachedTimers.clear();
        if (taskManager != null) {
            try {
                List<TimerInfo> list = taskManager.listTimers(currentScope);
                if (list != null) {
                    cachedTimers.addAll(list);
                }
            } catch (Exception e) {
                fallbackLoadState();
            }
        } else {
            fallbackLoadState();
        }

        if (selectedIndex >= cachedTimers.size()) {
            selectedIndex = Math.max(0, cachedTimers.size() - 1);
        }
    }

    private void fallbackLoadState() {
        if (timerStateManager != null) {
            try {
                List<TimerScope> scopes = (currentScope == TimerScope.ALL || currentScope == null)
                        ? List.of(TimerScope.USER, TimerScope.SYSTEM)
                        : List.of(currentScope);
                for (TimerScope s : scopes) {
                    var state = timerStateManager.loadState(s);
                    if (state != null && state.timers() != null) {
                        for (var r : state.timers().values()) {
                            cachedTimers.add(new TimerInfo(
                                    r.id(),
                                    r.name(),
                                    r.id() + "-" + r.name(),
                                    r.schedule(),
                                    r.description(),
                                    r.description(),
                                    r.scope(),
                                    r.scriptPath(),
                                    null,
                                    null,
                                    true,
                                    "Unknown",
                                    "Never",
                                    "N/A",
                                    r.createdAt()
                            ));
                        }
                    }
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

        // Row 0 of screen: Scope selector sub-bar
        renderScopeSubBar(tg, 2, startRow, width - 4);

        // Calculate split between table and inspector
        int bodyStart = startRow + 2;
        int remainingHeight = height - 2;
        int tableHeight = Math.max(6, (remainingHeight * 55) / 100);
        int inspectorHeight = Math.max(6, remainingHeight - tableHeight);

        // Top Box: Timer Table
        renderTimerTable(tg, 2, bodyStart, width - 4, tableHeight);

        // Bottom Box: Timer Inspector & Details
        int inspectorY = bodyStart + tableHeight;
        renderInspectorPane(tg, 2, inspectorY, width - 4, inspectorHeight);

        // Render modal overlay if active
        if (activeModal != null) {
            activeModal.render(tg, width + 4, size.getRows());
        }
    }

    private void renderScopeSubBar(TextGraphics tg, int x, int y, int width) {
        tg.setForegroundColor(TextColor.ANSI.WHITE);
        tg.putString(x, y, "Scope: ");

        int currX = x + 7;
        for (TimerScope s : List.of(TimerScope.ALL, TimerScope.USER, TimerScope.SYSTEM)) {
            boolean active = (this.currentScope == s);
            String label = " [" + s.name() + "] ";
            if (active) {
                tg.setBackgroundColor(TextColor.ANSI.YELLOW);
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

        tg.setForegroundColor(TextColor.ANSI.BLACK_BRIGHT);
        tg.putString(currX + 1, y, "(Press 's' to cycle) │ Total: " + cachedTimers.size() + " timers");
    }

    private void renderTimerTable(TextGraphics tg, int x, int y, int width, int height) {
        String boxTitle = "Scheduled Timers [" + currentScope.name() + "]";
        TuiDrawingHelper.drawBox(tg, x, y, width, height, boxTitle, TextColor.ANSI.YELLOW, TextColor.ANSI.YELLOW_BRIGHT);

        // Column headers
        int headerY = y + 1;
        tg.setForegroundColor(TextColor.ANSI.CYAN_BRIGHT);
        tg.enableModifiers(SGR.BOLD);
        String header = String.format("  %-7s %-16s %-16s %-8s %-12s %s",
                "ID", "NAME", "SCHEDULE", "SCOPE", "STATUS", "NEXT RUN");
        tg.putString(x + 1, headerY, TuiDrawingHelper.truncate(header, width - 2));
        tg.clearModifiers();

        tg.setForegroundColor(TextColor.ANSI.BLACK_BRIGHT);
        tg.putString(x + 1, headerY + 1, "─".repeat(Math.max(0, width - 2)));

        int visibleRows = height - 3;
        if (visibleRows <= 0) return;

        // Viewport bounds calculation
        if (selectedIndex < scrollOffset) {
            scrollOffset = selectedIndex;
        } else if (selectedIndex >= scrollOffset + visibleRows) {
            scrollOffset = selectedIndex - visibleRows + 1;
        }
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, cachedTimers.size() - visibleRows)));

        if (cachedTimers.isEmpty()) {
            tg.setForegroundColor(TextColor.ANSI.WHITE);
            tg.putString(x + 4, headerY + 2, "No timers found in this scope. Create one via: nx timer create <name> -s '<schedule>'");
            return;
        }

        int rowY = headerY + 2;
        for (int i = 0; i < visibleRows; i++) {
            int timerIndex = scrollOffset + i;
            if (timerIndex >= cachedTimers.size()) break;

            TimerInfo t = cachedTimers.get(timerIndex);
            boolean isSelected = (timerIndex == selectedIndex);

            String cursor = isSelected ? "► " : "  ";
            String idStr = t.id() != null ? t.id() : "-";
            String nameStr = t.name() != null ? t.name() : "-";
            String schedStr = t.schedule() != null ? t.schedule() : "N/A";
            String scopeStr = t.scope() != null ? t.scope().name() : "USER";
            String statusStr = t.active() ? "● ACTIVE" : "○ INACTIVE";
            String nextRunStr = t.nextRun() != null ? t.nextRun() : "N/A";

            String row = String.format("%s%-7s %-16s %-16s %-8s %-12s %s",
                    cursor,
                    TuiDrawingHelper.truncate(idStr, 7),
                    TuiDrawingHelper.truncate(nameStr, 16),
                    TuiDrawingHelper.truncate(schedStr, 16),
                    TuiDrawingHelper.truncate(scopeStr, 8),
                    TuiDrawingHelper.truncate(statusStr, 12),
                    nextRunStr
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
                tg.setForegroundColor(t.active() ? TextColor.ANSI.WHITE : TextColor.ANSI.BLACK_BRIGHT);
                tg.putString(x + 2, rowY, paddedRow);
            }

            rowY++;
        }
    }

    private void renderInspectorPane(TextGraphics tg, int x, int y, int width, int height) {
        TimerInfo selected = (!cachedTimers.isEmpty() && selectedIndex >= 0 && selectedIndex < cachedTimers.size())
                ? cachedTimers.get(selectedIndex)
                : null;

        String title = selected != null
                ? "Inspector: " + selected.id() + " (" + selected.name() + ")"
                : "Timer Inspector";

        TuiDrawingHelper.drawBox(tg, x, y, width, height, title, TextColor.ANSI.CYAN, TextColor.ANSI.CYAN_BRIGHT);

        if (selected == null) {
            tg.setForegroundColor(TextColor.ANSI.BLACK_BRIGHT);
            tg.putString(x + 4, y + 2, "No timer selected. Press [c] to create or [s] to toggle scope.");
        } else {
            int currY = y + 1;
            drawInspectorLine(tg, x + 2, currY++, "Description:", selected.description() != null ? selected.description() : "No description provided", width - 4);
            drawInspectorLine(tg, x + 2, currY++, "Schedule:", (selected.schedule() != null ? selected.schedule() : "") + (selected.scheduleDetails() != null ? " (" + selected.scheduleDetails() + ")" : ""), width - 4);

            String runSummary = "Next: " + (selected.nextRun() != null ? selected.nextRun() : "N/A") + "  │  Last: " + (selected.lastRun() != null ? selected.lastRun() : "Never") + " [" + (selected.lastResult() != null ? selected.lastResult() : "N/A") + "]";
            drawInspectorLine(tg, x + 2, currY++, "Runs:", runSummary, width - 4);

            if (height >= 7) {
                drawInspectorLine(tg, x + 2, currY++, "Script Path:", selected.scriptPath() != null ? selected.scriptPath() : "N/A", width - 4);
            }
            if (height >= 8 && selected.unitPath() != null) {
                drawInspectorLine(tg, x + 2, currY++, "Unit File:", selected.unitPath(), width - 4);
            }
        }

        int toastY = y + height - 3;
        int promptY = y + height - 2;

        if (confirmingDelete && selected != null) {
            tg.setBackgroundColor(TextColor.ANSI.RED);
            tg.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
            tg.enableModifiers(SGR.BOLD);
            tg.putString(x + 2, promptY, " ⚠ CONFIRM DELETE: Remove timer " + selected.id() + "? [y] Confirm  [n/ESC] Cancel ");
            tg.clearModifiers();
            tg.setBackgroundColor(TextColor.ANSI.DEFAULT);
        } else {
            if (toastMessage != null) {
                tg.setForegroundColor(toastIsError ? TextColor.ANSI.RED_BRIGHT : TextColor.ANSI.GREEN_BRIGHT);
                tg.enableModifiers(SGR.BOLD);
                tg.putString(x + 2, toastY, TuiDrawingHelper.truncate(toastMessage, width - 4));
                tg.clearModifiers();
            }

            tg.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
            String actions = (selected != null)
                    ? "[▲/▼] Navigate  │  [c] Create  │  [x/Enter] Run  │  [d] Delete  │  [s] Scope  │  [r] Refresh"
                    : "[c] Create Timer  │  [s] Toggle Scope  │  [r] Refresh";
            tg.putString(x + 2, promptY, TuiDrawingHelper.truncate(actions, width - 4));
        }
    }

    private void drawInspectorLine(TextGraphics tg, int x, int y, String label, String value, int maxWidth) {
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

        // Modal dialog interceptor
        if (activeModal != null) {
            return activeModal.handleInput(key);
        }

        // Confirmation dialog interceptor
        if (confirmingDelete) {
            Character ch = key.getCharacter();
            if (ch != null && (ch == 'y' || ch == 'Y')) {
                executeDeleteCurrent();
                confirmingDelete = false;
                return true;
            } else if (key.getKeyType() == KeyType.Escape || (ch != null && (ch == 'n' || ch == 'N'))) {
                confirmingDelete = false;
                toastMessage = "Deletion cancelled.";
                toastIsError = false;
                toastTimestamp = Instant.now();
                return true;
            }
            return true; // absorb all keys during confirmation
        }

        KeyType type = key.getKeyType();
        Character ch = key.getCharacter();

        // Cursor movement
        if (type == KeyType.ArrowUp || (type == KeyType.Character && (ch == 'k' || ch == 'K'))) {
            if (!cachedTimers.isEmpty()) {
                selectedIndex = Math.max(0, selectedIndex - 1);
            }
            return true;
        } else if (type == KeyType.ArrowDown || (type == KeyType.Character && (ch == 'j' || ch == 'J'))) {
            if (!cachedTimers.isEmpty()) {
                selectedIndex = Math.min(cachedTimers.size() - 1, selectedIndex + 1);
            }
            return true;
        } else if (type == KeyType.PageUp) {
            if (!cachedTimers.isEmpty()) {
                selectedIndex = Math.max(0, selectedIndex - 5);
            }
            return true;
        } else if (type == KeyType.PageDown) {
            if (!cachedTimers.isEmpty()) {
                selectedIndex = Math.min(cachedTimers.size() - 1, selectedIndex + 5);
            }
            return true;
        } else if (type == KeyType.Home) {
            selectedIndex = 0;
            return true;
        } else if (type == KeyType.End) {
            if (!cachedTimers.isEmpty()) {
                selectedIndex = cachedTimers.size() - 1;
            }
            return true;
        }

        // Scope toggle
        if (type == KeyType.Character && (ch == 's' || ch == 'S')) {
            if (currentScope == TimerScope.ALL) {
                currentScope = TimerScope.USER;
            } else if (currentScope == TimerScope.USER) {
                currentScope = TimerScope.SYSTEM;
            } else {
                currentScope = TimerScope.ALL;
            }
            selectedIndex = 0;
            scrollOffset = 0;
            toastMessage = "Switched scope to " + currentScope.name();
            toastIsError = false;
            toastTimestamp = Instant.now();
            refresh();
            return true;
        }

        // Create timer modal
        if (type == KeyType.Character && (ch == 'c' || ch == 'C')) {
            openCreateTimerModal();
            return true;
        }

        // Run timer now
        if (type == KeyType.Enter || (type == KeyType.Character && (ch == 'x' || ch == 'X'))) {
            executeRunCurrent();
            return true;
        }

        // Delete timer trigger
        if (type == KeyType.Delete || (type == KeyType.Character && (ch == 'd' || ch == 'D'))) {
            if (!cachedTimers.isEmpty() && selectedIndex >= 0 && selectedIndex < cachedTimers.size()) {
                confirmingDelete = true;
            }
            return true;
        }

        // Refresh timers
        if (type == KeyType.Character && (ch == 'r' || ch == 'R')) {
            refresh();
            toastMessage = "Timers refreshed.";
            toastIsError = false;
            toastTimestamp = Instant.now();
            return true;
        }

        // Unconsumed keys bubble up to global launcher (tabs, quit, etc.)
        return false;
    }

    private void executeRunCurrent() {
        if (cachedTimers.isEmpty() || selectedIndex < 0 || selectedIndex >= cachedTimers.size()) {
            return;
        }
        TimerInfo timer = cachedTimers.get(selectedIndex);
        if (taskManager != null) {
            try {
                TimerRunResult result = taskManager.runTimer(new TimerRunRequest(timer.id(), timer.scope()));
                if (result.success()) {
                    toastMessage = "✔ Successfully ran " + timer.id() + " (exit: " + result.exitCode() + ", " + result.executionTimeMs() + "ms)";
                    toastIsError = false;
                } else {
                    toastMessage = "❌ Execution failed for " + timer.id() + ": " + (result.message() != null ? result.message() : "code " + result.exitCode());
                    toastIsError = true;
                }
            } catch (Exception e) {
                toastMessage = "❌ Execution error: " + e.getMessage();
                toastIsError = true;
            }
        } else {
            toastMessage = "⚠ ScheduledTaskManager unavailable";
            toastIsError = true;
        }
        toastTimestamp = Instant.now();
        refresh();
    }

    private void executeDeleteCurrent() {
        if (cachedTimers.isEmpty() || selectedIndex < 0 || selectedIndex >= cachedTimers.size()) {
            return;
        }
        TimerInfo timer = cachedTimers.get(selectedIndex);
        if (taskManager != null) {
            try {
                TimerDeleteResult result = taskManager.deleteTimer(new TimerDeleteRequest(timer.id(), false, timer.scope()));
                if (result.success()) {
                    toastMessage = "✔ Deleted timer " + timer.id() + " (" + timer.name() + ")";
                    toastIsError = false;
                } else {
                    toastMessage = "❌ Delete failed: " + result.message();
                    toastIsError = true;
                }
            } catch (Exception e) {
                toastMessage = "❌ Delete error: " + e.getMessage();
                toastIsError = true;
            }
        } else {
            toastMessage = "⚠ ScheduledTaskManager unavailable";
            toastIsError = true;
        }
        toastTimestamp = Instant.now();
        refresh();
        if (selectedIndex >= cachedTimers.size()) {
            selectedIndex = Math.max(0, cachedTimers.size() - 1);
        }
    }

    public void openCreateTimerModal() {
        TuiFormModal modal = new TuiFormModal("Create Scheduled Timer");
        modal.addField(TuiFormModal.FormField.text("name", "Name", ""));
        modal.addField(TuiFormModal.FormField.text("schedule", "Schedule", "daily"));
        modal.addField(TuiFormModal.FormField.text("description", "Description", ""));
        modal.addField(TuiFormModal.FormField.options("scope", "Scope", List.of("USER", "SYSTEM"), 0));
        modal.addField(TuiFormModal.FormField.text("command", "Command", "echo 'Running task'"));

        modal.setOnCancel(() -> {
            this.activeModal = null;
        });

        modal.setOnSubmit(m -> {
            String name = m.getValue("name");
            String schedule = m.getValue("schedule");
            String desc = m.getValue("description");
            String scopeStr = m.getValue("scope");
            String script = m.getValue("command");

            if (name == null || name.isBlank()) {
                m.setErrorMessage("Timer name cannot be empty.");
                return;
            }
            if (schedule == null || schedule.isBlank()) {
                m.setErrorMessage("Schedule cannot be empty (e.g. daily, hourly).");
                return;
            }

            TimerScope scope = "SYSTEM".equalsIgnoreCase(scopeStr) ? TimerScope.SYSTEM : TimerScope.USER;

            if (taskManager != null) {
                try {
                    TimerCreateRequest req = new TimerCreateRequest(name.trim(), schedule.trim(), desc, scope, script, false);
                    TimerCreateResult res = taskManager.createTimer(req);
                    if (res != null && res.success()) {
                        this.activeModal = null;
                        refresh();
                        for (int idx = 0; idx < cachedTimers.size(); idx++) {
                            if (name.trim().equalsIgnoreCase(cachedTimers.get(idx).name())) {
                                selectedIndex = idx;
                                break;
                            }
                        }
                        this.toastMessage = "✔ Created timer " + (res.timer() != null ? res.timer().id() : name);
                        this.toastIsError = false;
                    } else {
                        m.setErrorMessage(res != null && res.message() != null ? res.message() : "Creation failed");
                    }
                } catch (Exception ex) {
                    m.setErrorMessage("Error: " + ex.getMessage());
                }
            } else {
                m.setErrorMessage("ScheduledTaskManager unavailable");
            }
            this.toastTimestamp = Instant.now();
        });

        this.activeModal = modal;
    }
}
