package com.nexus.adapters.tui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TuiDrawingHelperTest {

    @Test
    @DisplayName("formatBytes formats B, KB, MB, GB properly")
    void testFormatBytes() {
        assertThat(TuiDrawingHelper.formatBytes(500)).isEqualTo("500 B");
        assertThat(TuiDrawingHelper.formatBytes(1024)).contains("KB");
        assertThat(TuiDrawingHelper.formatBytes(1024 * 1024)).contains("MB");
        assertThat(TuiDrawingHelper.formatBytes(1024L * 1024 * 1024 * 16)).contains("GB");
    }

    @Test
    @DisplayName("truncate truncates long strings with ellipsis")
    void testTruncate() {
        assertThat(TuiDrawingHelper.truncate("Hello World", 5)).isEqualTo("He...");
        assertThat(TuiDrawingHelper.truncate("Hi", 5)).isEqualTo("Hi");
        assertThat(TuiDrawingHelper.truncate(null, 5)).isEqualTo("");
    }

    @Test
    @DisplayName("padRight adds trailing spaces up to requested length")
    void testPadRight() {
        assertThat(TuiDrawingHelper.padRight("abc", 6)).isEqualTo("abc   ");
        assertThat(TuiDrawingHelper.padRight("abcdef", 4)).isEqualTo("abcdef");
    }

    @Test
    @DisplayName("drawBox and drawProgressBar render on TextGraphics without error")
    void testDrawingMethods() throws Exception {
        DefaultVirtualTerminal terminal = new DefaultVirtualTerminal(new TerminalSize(80, 24));
        TerminalScreen screen = new TerminalScreen(terminal);
        screen.startScreen();
        TextGraphics tg = screen.newTextGraphics();

        TuiDrawingHelper.drawBox(tg, 2, 2, 40, 10, "Test Box", TextColor.ANSI.CYAN, TextColor.ANSI.WHITE);
        TuiDrawingHelper.drawProgressBar(tg, 4, 4, 20, 50.0, TextColor.ANSI.GREEN, TextColor.ANSI.BLACK_BRIGHT);

        screen.refresh();
        screen.stopScreen();
    }
}
