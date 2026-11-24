package com.nexus.adapters.tui.screens;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.nexus.adapters.tui.NexusTuiScreen;
import com.nexus.adapters.tui.TuiDrawingHelper;
import com.nexus.capabilities.scheduler.timer.dto.TimerScope;
import com.nexus.platform.scheduler.TimerStateManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Screen 2: Timers Management Screen (Incremental foundation).
 */
@Component
public class TimersScreen implements NexusTuiScreen {

    private final TimerStateManager timerStateManager;
    private final List<TimerStateManager.TimerRecord> cachedTimers = new ArrayList<>();

    @Autowired
    public TimersScreen(@Autowired(required = false) TimerStateManager timerStateManager) {
        this.timerStateManager = timerStateManager;
        refresh();
    }

    @Override
    public String getTitle() {
        return "2: Timers";
    }

    @Override
    public synchronized void refresh() {
        cachedTimers.clear();
        if (timerStateManager != null) {
            try {
                for (TimerScope scope : List.of(TimerScope.USER, TimerScope.SYSTEM)) {
                    Map<String, TimerStateManager.TimerRecord> map = timerStateManager.loadState(scope).timers();
                    if (map != null) {
                        cachedTimers.addAll(map.values());
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    @Override
    public synchronized void render(TextGraphics tg, TerminalSize size, int startRow, int availableRows) {
        int width = size.getColumns();
        int boxWidth = Math.max(30, width - 4);
        int boxHeight = Math.max(10, availableRows - 1);

        TuiDrawingHelper.drawBox(tg, 2, startRow, boxWidth, boxHeight, "Timers Engine & Scheduled Tasks", TextColor.ANSI.YELLOW, TextColor.ANSI.YELLOW_BRIGHT);

        tg.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
        tg.enableModifiers(SGR.BOLD);
        tg.putString(4, startRow + 2, "Scheduled Timers (" + cachedTimers.size() + " active)");
        tg.clearModifiers();

        // Table header
        int y = startRow + 4;
        tg.setForegroundColor(TextColor.ANSI.CYAN_BRIGHT);
        tg.enableModifiers(SGR.BOLD);
        tg.putString(4, y, TuiDrawingHelper.padRight("ID / Name", 24) + TuiDrawingHelper.padRight("Schedule", 18) + "Description");
        tg.clearModifiers();

        tg.setForegroundColor(TextColor.ANSI.BLACK_BRIGHT);
        tg.putString(4, y + 1, "─".repeat(Math.max(10, boxWidth - 6)));

        y += 2;
        int maxRows = boxHeight - 8;
        if (cachedTimers.isEmpty()) {
            tg.setForegroundColor(TextColor.ANSI.WHITE);
            tg.putString(4, y, "No timers registered. Create one using CLI: nx timer create <name> -s '<schedule>'");
        } else {
            int displayed = 0;
            for (TimerStateManager.TimerRecord r : cachedTimers) {
                if (displayed >= maxRows) {
                    tg.setForegroundColor(TextColor.ANSI.YELLOW);
                    tg.putString(4, y, "... and " + (cachedTimers.size() - displayed) + " more timers.");
                    break;
                }
                tg.setForegroundColor(TextColor.ANSI.WHITE);
                String idName = (r.id() != null ? r.id() : "") + " " + (r.name() != null ? r.name() : "");
                String sched = r.schedule() != null ? r.schedule() : "N/A";
                String desc = r.description() != null ? r.description() : "";

                tg.putString(4, y, TuiDrawingHelper.padRight(TuiDrawingHelper.truncate(idName, 22), 24)
                        + TuiDrawingHelper.padRight(TuiDrawingHelper.truncate(sched, 16), 18)
                        + TuiDrawingHelper.truncate(desc, Math.max(10, boxWidth - 48)));
                y++;
                displayed++;
            }
        }

        // Footer banner inside box
        tg.setForegroundColor(TextColor.ANSI.BLACK_BRIGHT);
        tg.putString(4, startRow + boxHeight - 2, "Interactive timer management (run, create, delete) arriving in Step 2.");
    }

    @Override
    public boolean handleInput(KeyStroke key) {
        return false;
    }
}
