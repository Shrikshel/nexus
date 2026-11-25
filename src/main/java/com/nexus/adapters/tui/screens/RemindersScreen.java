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
import com.nexus.capabilities.reminder.dispatch.ReminderDispatchService;
import com.nexus.capabilities.reminder.dto.*;
import com.nexus.capabilities.reminder.storage.ReminderStorageManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Screen 3: Interactive Reminders Engine Screen.
 * Provides live table listing with filter modes (ALL/ACTIVE/BIRTHDAYS/BILLS/EVENTS),
 * keyboard cursor navigation, stage delivery pill indicators, detailed inspector pane,
 * on-demand test dispatch ([t]), next stage trigger ([x]/[Enter]), and deletion with confirmation.
 */
@Component
public class RemindersScreen implements NexusTuiScreen {

    public enum FilterMode {
        ALL("ALL"),
        ACTIVE("ACTIVE"),
        BIRTHDAYS("BIRTHDAY"),
        BILLS("BILL"),
        EVENTS("EVENT");

        private final String display;
        FilterMode(String display) { this.display = display; }
        public String getDisplay() { return display; }
    }

    private final ReminderStorageManager storageManager;
    private final ReminderDispatchService dispatchService;

    private FilterMode currentFilter = FilterMode.ALL;
    private final List<ReminderEvent> allReminders = new ArrayList<>();
    private final List<ReminderEvent> filteredReminders = new ArrayList<>();
    private int selectedIndex = 0;
    private int scrollOffset = 0;

    // Inline action and confirmation states
    private boolean confirmingDelete = false;
    private TuiFormModal activeModal = null;
    private String toastMessage = null;
    private boolean toastIsError = false;
    private Instant toastTimestamp = null;

    public RemindersScreen(ReminderStorageManager storageManager) {
        this(storageManager, null);
    }

    @Autowired
    public RemindersScreen(
            @Autowired(required = false) ReminderStorageManager storageManager,
            @Autowired(required = false) ReminderDispatchService dispatchService
    ) {
        this.storageManager = storageManager;
        this.dispatchService = dispatchService;
        refresh();
    }

    @Override
    public String getTitle() {
        return "3: Reminders";
    }

    public FilterMode getCurrentFilter() {
        return currentFilter;
    }

    public void setCurrentFilter(FilterMode filter) {
        this.currentFilter = filter != null ? filter : FilterMode.ALL;
        applyFilter();
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public void setSelectedIndex(int index) {
        if (!filteredReminders.isEmpty()) {
            this.selectedIndex = Math.max(0, Math.min(index, filteredReminders.size() - 1));
        }
    }

    public List<ReminderEvent> getFilteredReminders() {
        return filteredReminders;
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
        allReminders.clear();
        if (storageManager != null) {
            try {
                allReminders.addAll(storageManager.loadAll());
            } catch (Exception ignored) {}
        }
        applyFilter();
    }

    private void applyFilter() {
        filteredReminders.clear();
        if (currentFilter == FilterMode.ALL) {
            filteredReminders.addAll(allReminders);
        } else if (currentFilter == FilterMode.ACTIVE) {
            filteredReminders.addAll(allReminders.stream()
                    .filter(e -> e.state() != null && "ACTIVE".equalsIgnoreCase(e.state().status()))
                    .collect(Collectors.toList()));
        } else {
            String typeName = currentFilter.getDisplay();
            filteredReminders.addAll(allReminders.stream()
                    .filter(e -> e.type() != null && typeName.equalsIgnoreCase(e.type().name()))
                    .collect(Collectors.toList()));
        }

        if (selectedIndex >= filteredReminders.size()) {
            selectedIndex = Math.max(0, filteredReminders.size() - 1);
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

        // Sub-bar with filter modes
        renderFilterSubBar(tg, 2, startRow, width - 4);

        int bodyStart = startRow + 2;
        int remainingHeight = height - 2;
        int tableHeight = Math.max(6, (remainingHeight * 55) / 100);
        int inspectorHeight = Math.max(6, remainingHeight - tableHeight);

        // Top Box: Reminders Table
        renderReminderTable(tg, 2, bodyStart, width - 4, tableHeight);

        // Bottom Box: Reminder Inspector & Stages Details
        int inspectorY = bodyStart + tableHeight;
        renderInspectorPane(tg, 2, inspectorY, width - 4, inspectorHeight);

        // Render modal overlay if active
        if (activeModal != null) {
            activeModal.render(tg, width + 4, size.getRows());
        }
    }

    private void renderFilterSubBar(TextGraphics tg, int x, int y, int width) {
        tg.setForegroundColor(TextColor.ANSI.WHITE);
        tg.putString(x, y, "Filter: ");

        int currX = x + 8;
        for (FilterMode f : FilterMode.values()) {
            boolean active = (this.currentFilter == f);
            String label = " [" + f.name() + "] ";
            if (active) {
                tg.setBackgroundColor(TextColor.ANSI.GREEN);
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
        tg.putString(currX + 1, y, "(Press 'f' to cycle) │ Total: " + filteredReminders.size() + " events");
    }

    private void renderReminderTable(TextGraphics tg, int x, int y, int width, int height) {
        String boxTitle = "Registered Reminders [" + currentFilter.name() + "]";
        TuiDrawingHelper.drawBox(tg, x, y, width, height, boxTitle, TextColor.ANSI.GREEN, TextColor.ANSI.GREEN_BRIGHT);

        int headerY = y + 1;
        tg.setForegroundColor(TextColor.ANSI.CYAN_BRIGHT);
        tg.enableModifiers(SGR.BOLD);
        String header = String.format("  %-9s %-20s %-10s %-16s %-10s %s",
                "ID", "TITLE", "TYPE", "TARGET DATE", "RECUR", "STAGES PROGRESS");
        tg.putString(x + 1, headerY, TuiDrawingHelper.truncate(header, width - 2));
        tg.clearModifiers();

        tg.setForegroundColor(TextColor.ANSI.BLACK_BRIGHT);
        tg.putString(x + 1, headerY + 1, "─".repeat(Math.max(0, width - 2)));

        int visibleRows = height - 3;
        if (visibleRows <= 0) return;

        if (selectedIndex < scrollOffset) {
            scrollOffset = selectedIndex;
        } else if (selectedIndex >= scrollOffset + visibleRows) {
            scrollOffset = selectedIndex - visibleRows + 1;
        }
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, filteredReminders.size() - visibleRows)));

        if (filteredReminders.isEmpty()) {
            tg.setForegroundColor(TextColor.ANSI.WHITE);
            tg.putString(x + 4, headerY + 2, "No reminders in this filter. Add one via: nx reminder add --title \"Title\" --date YYYY-MM-DD");
            return;
        }

        int rowY = headerY + 2;
        for (int i = 0; i < visibleRows; i++) {
            int remIndex = scrollOffset + i;
            if (remIndex >= filteredReminders.size()) break;

            ReminderEvent r = filteredReminders.get(remIndex);
            boolean isSelected = (remIndex == selectedIndex);

            String cursor = isSelected ? "► " : "  ";
            String idStr = r.id() != null ? r.id() : "-";
            String titleStr = r.title() != null ? r.title() : "-";
            String typeStr = r.type() != null ? r.type().name() : "-";
            String dateStr = (r.targetDate() != null ? r.targetDate().toString() : "")
                    + (r.targetTime() != null ? " " + r.targetTime().toString() : "");
            String recurStr = (r.recurrence() != null && !r.recurrence().isNone()) ? r.recurrence().format() : "ONCE";

            String stagesSummary = formatStagesSummary(r);

            String row = String.format("%s%-9s %-20s %-10s %-16s %-10s %s",
                    cursor,
                    TuiDrawingHelper.truncate(idStr, 9),
                    TuiDrawingHelper.truncate(titleStr, 20),
                    TuiDrawingHelper.truncate(typeStr, 10),
                    TuiDrawingHelper.truncate(dateStr, 16),
                    TuiDrawingHelper.truncate(recurStr, 10),
                    stagesSummary
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

    private String formatStagesSummary(ReminderEvent r) {
        if (r.stages() == null || r.stages().isEmpty()) {
            return "No stages";
        }
        StringBuilder sb = new StringBuilder();
        for (ReminderStage s : r.stages()) {
            if (s.status() == ReminderStageStatus.DELIVERED) {
                sb.append("✔").append(s.stage()).append(" ");
            } else if (s.status() == ReminderStageStatus.SKIPPED) {
                sb.append("⊘").append(s.stage()).append(" ");
            } else {
                sb.append("●").append(s.stage()).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private void renderInspectorPane(TextGraphics tg, int x, int y, int width, int height) {
        ReminderEvent selected = (!filteredReminders.isEmpty() && selectedIndex >= 0 && selectedIndex < filteredReminders.size())
                ? filteredReminders.get(selectedIndex)
                : null;

        String title = selected != null
                ? "Inspector: " + selected.id() + " (" + selected.title() + ")"
                : "Reminder Inspector";

        TuiDrawingHelper.drawBox(tg, x, y, width, height, title, TextColor.ANSI.CYAN, TextColor.ANSI.CYAN_BRIGHT);

        if (selected == null) {
            tg.setForegroundColor(TextColor.ANSI.BLACK_BRIGHT);
            tg.putString(x + 4, y + 2, "No reminder selected. Press [f] to cycle filters or create one via CLI.");
            return;
        }

        int currY = y + 1;
        String line1 = "Title: " + selected.title() + "  │  Type: " + (selected.type() != null ? selected.type().name() : "N/A")
                + "  │  Category: " + (selected.category() != null ? selected.category() : "General");
        drawInspectorLine(tg, x + 2, currY++, "Event:", line1, width - 4);

        String targetStr = (selected.targetDate() != null ? selected.targetDate().toString() : "N/A")
                + (selected.targetTime() != null ? " at " + selected.targetTime().toString() : "")
                + " (" + (selected.timezone() != null ? selected.timezone().getId() : "Host Time") + ")"
                + "  │  Recurrence: " + (selected.recurrence() != null ? selected.recurrence().format() : "NONE");
        drawInspectorLine(tg, x + 2, currY++, "Target:", targetStr, width - 4);

        String stateStr = (selected.state() != null && selected.state().status() != null ? selected.state().status() : "UNKNOWN")
                + (selected.state() != null && selected.state().activeCycle() != null ? " (Cycle: " + selected.state().activeCycle() + ")" : "")
                + "  │  Next Stage: " + (selected.state() != null && selected.state().nextStage() != null ? selected.state().nextStage() : "None")
                + (selected.state() != null && selected.state().nextTriggerAt() != null ? " [at " + selected.state().nextTriggerAt() + "]" : "");
        drawInspectorLine(tg, x + 2, currY++, "Status:", stateStr, width - 4);

        if (height >= 7) {
            String stagesList = formatDetailedStages(selected);
            drawInspectorLine(tg, x + 2, currY++, "Stages:", stagesList, width - 4);
        }

        if (height >= 8 && selected.history() != null) {
            String historySummary = selected.history().size() + " cycle deliveries recorded in audit history";
            drawInspectorLine(tg, x + 2, currY++, "History:", historySummary, width - 4);
        }

        int promptY = y + height - 2;
        if (confirmingDelete) {
            tg.setBackgroundColor(TextColor.ANSI.RED);
            tg.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
            tg.enableModifiers(SGR.BOLD);
            tg.putString(x + 2, promptY, " ⚠ CONFIRM DELETE: Remove reminder " + selected.id() + "? [y] Confirm  [n/ESC] Cancel ");
            tg.clearModifiers();
            tg.setBackgroundColor(TextColor.ANSI.DEFAULT);
        } else if (toastMessage != null) {
            tg.setForegroundColor(toastIsError ? TextColor.ANSI.RED_BRIGHT : TextColor.ANSI.GREEN_BRIGHT);
            tg.enableModifiers(SGR.BOLD);
            tg.putString(x + 2, promptY, TuiDrawingHelper.truncate(toastMessage, width - 4));
            tg.clearModifiers();
        } else {
            tg.setForegroundColor(TextColor.ANSI.BLACK_BRIGHT);
            String actions = "[▲/▼] Navigate  │  [c] Create  │  [e] Edit  │  [t] Test Alert  │  [x] Dispatch  │  [d] Delete  │  [f] Filter";
            tg.putString(x + 2, promptY, TuiDrawingHelper.truncate(actions, width - 4));
        }
    }

    private String formatDetailedStages(ReminderEvent r) {
        if (r.stages() == null || r.stages().isEmpty()) {
            return "No alert stages configured";
        }
        StringBuilder sb = new StringBuilder();
        for (ReminderStage s : r.stages()) {
            sb.append(s.stage()).append(" [").append(s.status()).append("]  ");
        }
        return sb.toString().trim();
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
            return true;
        }

        KeyType type = key.getKeyType();
        Character ch = key.getCharacter();

        // Cursor movement
        if (type == KeyType.ArrowUp || (type == KeyType.Character && (ch == 'k' || ch == 'K'))) {
            if (!filteredReminders.isEmpty()) {
                selectedIndex = Math.max(0, selectedIndex - 1);
            }
            return true;
        } else if (type == KeyType.ArrowDown || (type == KeyType.Character && (ch == 'j' || ch == 'J'))) {
            if (!filteredReminders.isEmpty()) {
                selectedIndex = Math.min(filteredReminders.size() - 1, selectedIndex + 1);
            }
            return true;
        } else if (type == KeyType.PageUp) {
            if (!filteredReminders.isEmpty()) {
                selectedIndex = Math.max(0, selectedIndex - 5);
            }
            return true;
        } else if (type == KeyType.PageDown) {
            if (!filteredReminders.isEmpty()) {
                selectedIndex = Math.min(filteredReminders.size() - 1, selectedIndex + 5);
            }
            return true;
        } else if (type == KeyType.Home) {
            selectedIndex = 0;
            return true;
        } else if (type == KeyType.End) {
            if (!filteredReminders.isEmpty()) {
                selectedIndex = filteredReminders.size() - 1;
            }
            return true;
        }

        // Filter cycling
        if (type == KeyType.Character && (ch == 'f' || ch == 'F')) {
            FilterMode[] modes = FilterMode.values();
            int nextOrd = (currentFilter.ordinal() + 1) % modes.length;
            currentFilter = modes[nextOrd];
            selectedIndex = 0;
            scrollOffset = 0;
            applyFilter();
            toastMessage = "Filter: " + currentFilter.name();
            toastIsError = false;
            toastTimestamp = Instant.now();
            return true;
        }

        // Create reminder modal
        if (type == KeyType.Character && (ch == 'c' || ch == 'C')) {
            openCreateReminderModal();
            return true;
        }

        // Edit reminder modal
        if (type == KeyType.Character && (ch == 'e' || ch == 'E')) {
            openEditReminderModal();
            return true;
        }

        // Test dispatch notification (does not advance stage)
        if (type == KeyType.Character && (ch == 't' || ch == 'T')) {
            executeTestDispatchCurrent();
            return true;
        }

        // Dispatch next stage (advances stage and arms timers)
        if (type == KeyType.Enter || (type == KeyType.Character && (ch == 'x' || ch == 'X'))) {
            executeDispatchCurrent();
            return true;
        }

        // Delete reminder trigger
        if (type == KeyType.Delete || (type == KeyType.Character && (ch == 'd' || ch == 'D'))) {
            if (!filteredReminders.isEmpty() && selectedIndex >= 0 && selectedIndex < filteredReminders.size()) {
                confirmingDelete = true;
            }
            return true;
        }

        // Refresh reminders
        if (type == KeyType.Character && (ch == 'r' || ch == 'R')) {
            refresh();
            toastMessage = "Reminders refreshed.";
            toastIsError = false;
            toastTimestamp = Instant.now();
            return true;
        }

        return false;
    }

    private void executeTestDispatchCurrent() {
        if (filteredReminders.isEmpty() || selectedIndex < 0 || selectedIndex >= filteredReminders.size()) {
            return;
        }
        ReminderEvent rem = filteredReminders.get(selectedIndex);
        if (dispatchService != null) {
            try {
                boolean ok = dispatchService.testDispatch(rem.id());
                if (ok) {
                    toastMessage = "✔ Test alert sent for " + rem.id() + " (" + rem.title() + ")";
                    toastIsError = false;
                } else {
                    toastMessage = "❌ Test dispatch failed for " + rem.id();
                    toastIsError = true;
                }
            } catch (Exception e) {
                toastMessage = "❌ Test dispatch error: " + e.getMessage();
                toastIsError = true;
            }
        } else {
            toastMessage = "⚠ ReminderDispatchService unavailable";
            toastIsError = true;
        }
        toastTimestamp = Instant.now();
    }

    private void executeDispatchCurrent() {
        if (filteredReminders.isEmpty() || selectedIndex < 0 || selectedIndex >= filteredReminders.size()) {
            return;
        }
        ReminderEvent rem = filteredReminders.get(selectedIndex);
        if (dispatchService != null) {
            try {
                boolean ok = dispatchService.dispatch(rem.id());
                if (ok) {
                    toastMessage = "✔ Dispatched stage for " + rem.id() + " (" + rem.title() + ")";
                    toastIsError = false;
                } else {
                    toastMessage = "❌ Stage dispatch failed for " + rem.id();
                    toastIsError = true;
                }
            } catch (Exception e) {
                toastMessage = "❌ Dispatch error: " + e.getMessage();
                toastIsError = true;
            }
        } else {
            toastMessage = "⚠ ReminderDispatchService unavailable";
            toastIsError = true;
        }
        toastTimestamp = Instant.now();
        refresh();
    }

    private void executeDeleteCurrent() {
        if (filteredReminders.isEmpty() || selectedIndex < 0 || selectedIndex >= filteredReminders.size()) {
            return;
        }
        ReminderEvent rem = filteredReminders.get(selectedIndex);
        if (storageManager != null) {
            try {
                boolean ok = storageManager.deleteByIdOrUuid(rem.id());
                if (ok) {
                    toastMessage = "✔ Deleted reminder " + rem.id() + " (" + rem.title() + ")";
                    toastIsError = false;
                } else {
                    toastMessage = "❌ Delete failed for " + rem.id();
                    toastIsError = true;
                }
            } catch (Exception e) {
                toastMessage = "❌ Delete error: " + e.getMessage();
                toastIsError = true;
            }
        } else {
            toastMessage = "⚠ ReminderStorageManager unavailable";
            toastIsError = true;
        }
        toastTimestamp = Instant.now();
        refresh();
        if (selectedIndex >= filteredReminders.size()) {
            selectedIndex = Math.max(0, filteredReminders.size() - 1);
        }
    }

    public void openCreateReminderModal() {
        TuiFormModal modal = new TuiFormModal("Create Milestone Reminder");
        modal.addField(TuiFormModal.FormField.text("title", "Title", ""));
        modal.addField(TuiFormModal.FormField.options("type", "Type", List.of("BIRTHDAY", "BILL", "EVENT", "TASK"), 0));
        modal.addField(TuiFormModal.FormField.text("date", "Target Date", LocalDate.now().plusDays(1).toString()));
        modal.addField(TuiFormModal.FormField.text("time", "Target Time", "09:00"));
        modal.addField(TuiFormModal.FormField.options("recur", "Recurrence", List.of("NONE", "daily", "1w", "1mo", "1y"), 0));
        modal.addField(TuiFormModal.FormField.text("stages", "Alert Stages", "7d,3d,1d,0m"));

        modal.setOnCancel(() -> {
            this.activeModal = null;
            this.toastMessage = "Reminder creation cancelled.";
            this.toastIsError = false;
            this.toastTimestamp = Instant.now();
        });

        modal.setOnSubmit(m -> {
            String title = m.getValue("title");
            String typeStr = m.getValue("type");
            String dateStr = m.getValue("date");
            String timeStr = m.getValue("time");
            String recurStr = m.getValue("recur");
            String stagesStr = m.getValue("stages");

            if (title == null || title.isBlank()) {
                m.setErrorMessage("Title cannot be empty.");
                return;
            }

            LocalDate targetDate;
            try {
                targetDate = LocalDate.parse(dateStr.trim());
            } catch (Exception e) {
                m.setErrorMessage("Invalid date format (use YYYY-MM-DD).");
                return;
            }

            LocalTime targetTime;
            try {
                targetTime = LocalTime.parse(timeStr.trim());
            } catch (Exception e) {
                m.setErrorMessage("Invalid time format (use HH:mm).");
                return;
            }

            ReminderType rType = ReminderType.valueOf(typeStr);
            ReminderRecurrence recur = "NONE".equalsIgnoreCase(recurStr) ? ReminderRecurrence.NONE : ReminderRecurrence.fromString(recurStr);
            List<String> stageList = Arrays.stream(stagesStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();

            if (stageList.isEmpty()) {
                m.setErrorMessage("At least one alert stage is required (e.g. 0m).");
                return;
            }

            if (storageManager != null) {
                try {
                    ReminderCreateRequest req = new ReminderCreateRequest(
                            null, title.trim(), rType, "General", targetDate, targetTime,
                            ZoneId.systemDefault(), recur, stageList, null, null
                    );
                    ReminderEvent created = storageManager.createReminder(req);
                    if (created != null) {
                        this.activeModal = null;
                        refresh();
                        for (int idx = 0; idx < filteredReminders.size(); idx++) {
                            if (created.id().equals(filteredReminders.get(idx).id())) {
                                selectedIndex = idx;
                                break;
                            }
                        }
                        this.toastMessage = "✔ Created reminder " + created.id() + " (" + created.title() + ")";
                        this.toastIsError = false;
                    } else {
                        m.setErrorMessage("Creation failed in storage");
                    }
                } catch (Exception ex) {
                    m.setErrorMessage("Error: " + ex.getMessage());
                }
            } else {
                m.setErrorMessage("ReminderStorageManager unavailable");
            }
            this.toastTimestamp = Instant.now();
        });

        this.activeModal = modal;
    }

    public void openEditReminderModal() {
        if (filteredReminders.isEmpty() || selectedIndex < 0 || selectedIndex >= filteredReminders.size()) {
            return;
        }
        ReminderEvent existing = filteredReminders.get(selectedIndex);

        TuiFormModal modal = new TuiFormModal("Edit Reminder: " + existing.id());
        modal.addField(TuiFormModal.FormField.text("title", "Title", existing.title()));

        List<String> typeList = List.of("BIRTHDAY", "BILL", "EVENT", "TASK");
        int typeIdx = existing.type() != null ? typeList.indexOf(existing.type().name()) : 0;
        modal.addField(TuiFormModal.FormField.options("type", "Type", typeList, Math.max(0, typeIdx)));

        modal.addField(TuiFormModal.FormField.text("date", "Target Date", existing.targetDate() != null ? existing.targetDate().toString() : ""));
        modal.addField(TuiFormModal.FormField.text("time", "Target Time", existing.targetTime() != null ? existing.targetTime().toString() : "09:00"));

        List<String> recurList = List.of("NONE", "daily", "1w", "1mo", "1y");
        String curRecur = (existing.recurrence() != null && !existing.recurrence().isNone()) ? existing.recurrence().format() : "NONE";
        int recurIdx = recurList.indexOf(curRecur);
        modal.addField(TuiFormModal.FormField.options("recur", "Recurrence", recurList, Math.max(0, recurIdx)));

        String existingStages = existing.stages() != null
                ? existing.stages().stream().map(ReminderStage::stage).collect(Collectors.joining(","))
                : "0m";
        modal.addField(TuiFormModal.FormField.text("stages", "Alert Stages", existingStages));

        modal.setOnCancel(() -> {
            this.activeModal = null;
            this.toastMessage = "Edit cancelled.";
            this.toastIsError = false;
            this.toastTimestamp = Instant.now();
        });

        modal.setOnSubmit(m -> {
            String title = m.getValue("title");
            String typeStr = m.getValue("type");
            String dateStr = m.getValue("date");
            String timeStr = m.getValue("time");
            String recurStr = m.getValue("recur");
            String stagesStr = m.getValue("stages");

            if (title == null || title.isBlank()) {
                m.setErrorMessage("Title cannot be empty.");
                return;
            }

            LocalDate targetDate;
            try {
                targetDate = LocalDate.parse(dateStr.trim());
            } catch (Exception e) {
                m.setErrorMessage("Invalid date format (use YYYY-MM-DD).");
                return;
            }

            LocalTime targetTime;
            try {
                targetTime = LocalTime.parse(timeStr.trim());
            } catch (Exception e) {
                m.setErrorMessage("Invalid time format (use HH:mm).");
                return;
            }

            ReminderType rType = ReminderType.valueOf(typeStr);
            ReminderRecurrence recur = "NONE".equalsIgnoreCase(recurStr) ? ReminderRecurrence.NONE : ReminderRecurrence.fromString(recurStr);
            List<String> stageList = Arrays.stream(stagesStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();

            if (storageManager != null) {
                try {
                    ReminderUpdateRequest req = new ReminderUpdateRequest(
                            existing.id(), title.trim(), rType, existing.category(), targetDate, targetTime,
                            existing.timezone(), recur, stageList, null, null
                    );
                    var updated = storageManager.updateReminder(req);
                    if (updated.isPresent()) {
                        this.activeModal = null;
                        refresh();
                        this.toastMessage = "✔ Updated reminder " + existing.id();
                        this.toastIsError = false;
                    } else {
                        m.setErrorMessage("Update failed in storage");
                    }
                } catch (Exception ex) {
                    m.setErrorMessage("Error: " + ex.getMessage());
                }
            } else {
                m.setErrorMessage("ReminderStorageManager unavailable");
            }
            this.toastTimestamp = Instant.now();
        });

        this.activeModal = modal;
    }
}
