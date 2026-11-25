package com.nexus.adapters.tui;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import com.nexus.adapters.tui.screens.OverviewScreen;
import com.nexus.adapters.tui.screens.RemindersScreen;
import com.nexus.adapters.tui.screens.SystemScreen;
import com.nexus.adapters.tui.screens.TimersScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Terminal User Interface (TUI) engine managing screen lifecycle, event polling,
 * tab switching, and periodic telemetry rendering.
 */
@Component
public class NexusTuiLauncher {

    private static final Logger log = LoggerFactory.getLogger(NexusTuiLauncher.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final List<NexusTuiScreen> screens = new ArrayList<>();
    private int currentScreenIndex = 0;
    private DefaultTerminalFactory terminalFactory;

    @Autowired
    public NexusTuiLauncher(
            OverviewScreen overviewScreen,
            TimersScreen timersScreen,
            RemindersScreen remindersScreen,
            SystemScreen systemScreen
    ) {
        if (overviewScreen != null) screens.add(overviewScreen);
        if (timersScreen != null) screens.add(timersScreen);
        if (remindersScreen != null) screens.add(remindersScreen);
        if (systemScreen != null) screens.add(systemScreen);

        DefaultTerminalFactory factory = new DefaultTerminalFactory();
        factory.setPreferTerminalEmulator(false);
        this.terminalFactory = factory;
    }

    public void setTerminalFactory(DefaultTerminalFactory factory) {
        this.terminalFactory = factory;
    }

    public List<NexusTuiScreen> getScreens() {
        return screens;
    }

    public int getCurrentScreenIndex() {
        return currentScreenIndex;
    }

    public void setCurrentScreenIndex(int index) {
        if (index >= 0 && index < screens.size()) {
            this.currentScreenIndex = index;
        }
    }

    /**
     * Launches the interactive TUI event loop on the default terminal.
     */
    public int launch() {
        Screen screen = null;
        try {
            Terminal terminal = terminalFactory.createTerminal();
            screen = new TerminalScreen(terminal);
            return launch(screen);
        } catch (Exception e) {
            log.error("Failed to initialize terminal for TUI: {}", e.getMessage(), e);
            System.err.println("Cannot launch interactive TUI: No compatible terminal detected (" + e.getMessage() + ").");
            System.err.println("Please run Nexus in an interactive terminal session (TTY/PTY).");
            return 1;
        } finally {
            if (screen != null) {
                try {
                    screen.stopScreen();
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Executes the interactive TUI event loop on an existing Screen instance (useful for testing).
     */
    public int launch(Screen screen) {
        if (screens.isEmpty()) {
            System.err.println("No TUI screens registered.");
            return 1;
        }

        try {
            screen.startScreen();
            screen.setCursorPosition(null);

            boolean running = true;
            long lastRefreshTime = System.currentTimeMillis();
            long refreshIntervalMs = 2000;

            renderFrame(screen);

            while (running) {
                KeyStroke stroke = screen.pollInput();
                boolean needsRender = false;

                if (stroke != null) {
                    KeyType type = stroke.getKeyType();
                    Character ch = stroke.getCharacter();

                    boolean handled = screens.get(currentScreenIndex).handleInput(stroke);
                    if (handled) {
                        needsRender = true;
                    } else if (type == KeyType.Escape || type == KeyType.EOF ||
                            (type == KeyType.Character && (ch == 'q' || ch == 'Q'))) {
                        running = false;
                        break;
                    } else if (type == KeyType.Tab) {
                        currentScreenIndex = (currentScreenIndex + 1) % screens.size();
                        screens.get(currentScreenIndex).refresh();
                        needsRender = true;
                    } else if (type == KeyType.ReverseTab) {
                        currentScreenIndex = (currentScreenIndex - 1 + screens.size()) % screens.size();
                        screens.get(currentScreenIndex).refresh();
                        needsRender = true;
                    } else if (type == KeyType.Character && ch >= '1' && ch <= ('0' + screens.size())) {
                        currentScreenIndex = (ch - '1');
                        screens.get(currentScreenIndex).refresh();
                        needsRender = true;
                    } else if (type == KeyType.Character && (ch == 'r' || ch == 'R')) {
                        screens.get(currentScreenIndex).refresh();
                        needsRender = true;
                    }
                }

                TerminalSize newSize = screen.doResizeIfNecessary();
                if (newSize != null) {
                    needsRender = true;
                }

                long now = System.currentTimeMillis();
                if (now - lastRefreshTime >= refreshIntervalMs) {
                    screens.get(currentScreenIndex).refresh();
                    lastRefreshTime = now;
                    needsRender = true;
                }

                if (needsRender) {
                    renderFrame(screen);
                }

                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    running = false;
                }
            }

            return 0;
        } catch (IOException e) {
            log.error("TUI I/O error during execution: {}", e.getMessage(), e);
            return 1;
        } finally {
            try {
                screen.stopScreen();
            } catch (Exception ignored) {}
        }
    }

    /**
     * Renders header, active tab screen, and footer status bar.
     */
    public void renderFrame(Screen screen) {
        TerminalSize size = screen.getTerminalSize();
        int width = size.getColumns();
        int height = size.getRows();

        TextGraphics tg = screen.newTextGraphics();
        tg.fill(' ');

        // Row 0: Top Header
        tg.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
        tg.enableModifiers(SGR.BOLD);
        tg.putString(2, 0, "Nexus Host Management Agent  ::  Interactive Dashboard");
        tg.clearModifiers();

        String timeStr = LocalTime.now().format(TIME_FMT);
        if (width > 60) {
            tg.setForegroundColor(TextColor.ANSI.CYAN_BRIGHT);
            tg.putString(width - timeStr.length() - 3, 0, timeStr);
        }

        // Row 1: Tabs
        int tabX = 2;
        for (int i = 0; i < screens.size(); i++) {
            NexusTuiScreen s = screens.get(i);
            boolean active = (i == currentScreenIndex);
            String tabText = " [" + s.getTitle() + "] ";

            if (tabX + tabText.length() >= width - 2) break;

            if (active) {
                tg.setForegroundColor(TextColor.ANSI.BLACK);
                tg.setBackgroundColor(TextColor.ANSI.CYAN_BRIGHT);
                tg.enableModifiers(SGR.BOLD);
                tg.putString(tabX, 1, tabText);
                tg.clearModifiers();
                tg.setBackgroundColor(TextColor.ANSI.DEFAULT);
            } else {
                tg.setForegroundColor(TextColor.ANSI.WHITE);
                tg.putString(tabX, 1, tabText);
            }

            tabX += tabText.length() + 1;
        }

        // Row 2: Divider
        tg.setForegroundColor(TextColor.ANSI.BLACK_BRIGHT);
        tg.putString(0, 2, "─".repeat(Math.max(0, width)));

        // Screen Body: Row 3 to (height - 3)
        int bodyStart = 3;
        int availableBodyRows = Math.max(0, height - 4);
        if (availableBodyRows > 0 && currentScreenIndex < screens.size()) {
            screens.get(currentScreenIndex).render(tg, size, bodyStart, availableBodyRows);
        }

        // Row (height - 1): Footer
        tg.setForegroundColor(TextColor.ANSI.BLACK_BRIGHT);
        tg.putString(0, height - 2, "─".repeat(Math.max(0, width)));

        String shortcuts = (currentScreenIndex < screens.size()) ? screens.get(currentScreenIndex).getActionShortcuts() : "";
        int col = 2;
        if (shortcuts != null && !shortcuts.isBlank() && width > 70) {
            tg.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
            tg.putString(col, height - 1, shortcuts);
            col += shortcuts.length() + 1;
            tg.setForegroundColor(TextColor.ANSI.BLACK_BRIGHT);
            tg.putString(col, height - 1, " │ ");
            col += 3;
        }

        tg.setForegroundColor(TextColor.ANSI.CYAN_BRIGHT);
        tg.enableModifiers(SGR.BOLD);
        tg.putString(col, height - 1, "[Tab / 1-4]");
        tg.clearModifiers();
        tg.setForegroundColor(TextColor.ANSI.WHITE);
        tg.putString(col + 12, height - 1, "Screen  │");
        col += 22;

        if (width >= col + 24) {
            tg.setForegroundColor(TextColor.ANSI.CYAN_BRIGHT);
            tg.enableModifiers(SGR.BOLD);
            tg.putString(col, height - 1, "[r]");
            tg.clearModifiers();
            tg.setForegroundColor(TextColor.ANSI.WHITE);
            tg.putString(col + 4, height - 1, "Refresh  │");
            col += 14;
        }

        tg.setForegroundColor(TextColor.ANSI.CYAN_BRIGHT);
        tg.enableModifiers(SGR.BOLD);
        tg.putString(col, height - 1, "[q / ESC]");
        tg.clearModifiers();
        tg.setForegroundColor(TextColor.ANSI.WHITE);
        tg.putString(col + 10, height - 1, "Quit");

        try {
            screen.refresh();
        } catch (IOException ignored) {}
    }
}
