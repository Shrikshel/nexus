package com.nexus.adapters.tui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;

/**
 * Interface representing an individual interactive screen in the Nexus TUI.
 */
public interface NexusTuiScreen {

    /**
     * Display title for the tab or header.
     */
    String getTitle();

    /**
     * Renders the screen contents within the provided terminal size bounds.
     */
    void render(TextGraphics tg, TerminalSize size, int startRow, int availableRows);

    /**
     * Handles keyboard input events targeted at this screen.
     * @return true if the event was consumed, false to allow default global handling
     */
    boolean handleInput(KeyStroke key);

    /**
     * Re-fetches or refreshes underlying state from services.
     */
    default void refresh() {}

    /**
     * Contextual hotkey shortcuts summary displayed in the persistent bottom status bar.
     */
    default String getActionShortcuts() {
        return "";
    }
}
