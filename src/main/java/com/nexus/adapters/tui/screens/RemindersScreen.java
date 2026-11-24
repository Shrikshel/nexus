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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen 3: Reminders Engine Screen (Incremental foundation).
 */
@Component
public class RemindersScreen implements NexusTuiScreen {

    private final ReminderStorageManager reminderStorageManager;
    private final List<ReminderEvent> cachedReminders = new ArrayList<>();

    @Autowired
    public RemindersScreen(@Autowired(required = false) ReminderStorageManager reminderStorageManager) {
        this.reminderStorageManager = reminderStorageManager;
        refresh();
    }

    @Override
    public String getTitle() {
        return "3: Reminders";
    }

    @Override
    public synchronized void refresh() {
        cachedReminders.clear();
        if (reminderStorageManager != null) {
            try {
                cachedReminders.addAll(reminderStorageManager.loadAll());
            } catch (Exception ignored) {}
        }
    }

    @Override
    public synchronized void render(TextGraphics tg, TerminalSize size, int startRow, int availableRows) {
        int width = size.getColumns();
        int boxWidth = Math.max(30, width - 4);
        int boxHeight = Math.max(10, availableRows - 1);

        TuiDrawingHelper.drawBox(tg, 2, startRow, boxWidth, boxHeight, "Reminders Engine & Milestone Notifications", TextColor.ANSI.GREEN, TextColor.ANSI.GREEN_BRIGHT);

        tg.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
        tg.enableModifiers(SGR.BOLD);
        tg.putString(4, startRow + 2, "Registered Reminders (" + cachedReminders.size() + " total)");
        tg.clearModifiers();

        int y = startRow + 4;
        tg.setForegroundColor(TextColor.ANSI.CYAN_BRIGHT);
        tg.enableModifiers(SGR.BOLD);
        tg.putString(4, y, TuiDrawingHelper.padRight("ID", 10) + TuiDrawingHelper.padRight("Target Date", 16) + TuiDrawingHelper.padRight("Type", 12) + "Title");
        tg.clearModifiers();

        tg.setForegroundColor(TextColor.ANSI.BLACK_BRIGHT);
        tg.putString(4, y + 1, "─".repeat(Math.max(10, boxWidth - 6)));

        y += 2;
        int maxRows = boxHeight - 8;
        if (cachedReminders.isEmpty()) {
            tg.setForegroundColor(TextColor.ANSI.WHITE);
            tg.putString(4, y, "No reminders registered. Create one using CLI: nx reminder add --title \"Title\" --date YYYY-MM-DD");
        } else {
            int displayed = 0;
            for (ReminderEvent r : cachedReminders) {
                if (displayed >= maxRows) {
                    tg.setForegroundColor(TextColor.ANSI.YELLOW);
                    tg.putString(4, y, "... and " + (cachedReminders.size() - displayed) + " more reminders.");
                    break;
                }
                tg.setForegroundColor(TextColor.ANSI.WHITE);
                String id = r.id() != null ? r.id() : "";
                String date = r.targetDate() != null ? r.targetDate().toString() : "";
                String type = r.type() != null ? r.type().name() : "";
                String title = r.title() != null ? r.title() : "";

                tg.putString(4, y, TuiDrawingHelper.padRight(id, 10)
                        + TuiDrawingHelper.padRight(date, 16)
                        + TuiDrawingHelper.padRight(type, 12)
                        + TuiDrawingHelper.truncate(title, Math.max(10, boxWidth - 44)));
                y++;
                displayed++;
            }
        }

        tg.setForegroundColor(TextColor.ANSI.BLACK_BRIGHT);
        tg.putString(4, startRow + boxHeight - 2, "Interactive reminder creator and stage triggers arriving in Step 3.");
    }

    @Override
    public boolean handleInput(KeyStroke key) {
        return false;
    }
}
