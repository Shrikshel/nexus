package com.nexus.adapters.tui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import com.nexus.adapters.tui.screens.OverviewScreen;
import com.nexus.adapters.tui.screens.RemindersScreen;
import com.nexus.adapters.tui.screens.SystemScreen;
import com.nexus.adapters.tui.screens.TimersScreen;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NexusTuiLauncherTest {

    @Test
    @DisplayName("NexusTuiLauncher starts, handles tabs, and quits cleanly on 'q'")
    void testLaunchWithQuit() throws Exception {
        OverviewScreen overview = new OverviewScreen(null, null, null, null);
        TimersScreen timers = new TimersScreen(null, null);
        RemindersScreen reminders = new RemindersScreen(null);
        SystemScreen system = new SystemScreen(null);

        NexusTuiLauncher launcher = new NexusTuiLauncher(overview, timers, reminders, system);
        assertThat(launcher.getScreens()).hasSize(4);

        DefaultVirtualTerminal terminal = new DefaultVirtualTerminal(new TerminalSize(100, 30));
        // Add keystrokes: Switch to Tab 2 ('2'), then quit ('q')
        terminal.addInput(new KeyStroke('2', false, false));
        terminal.addInput(new KeyStroke('q', false, false));

        TerminalScreen screen = new TerminalScreen(terminal);

        int exitCode = launcher.launch(screen);
        assertThat(exitCode).isEqualTo(0);
        assertThat(launcher.getCurrentScreenIndex()).isEqualTo(1); // 0-indexed, so '2' sets index 1
    }

    @Test
    @DisplayName("NexusTuiLauncher cycles tabs using Tab key")
    void testLaunchWithTabCycle() throws Exception {
        OverviewScreen overview = new OverviewScreen(null, null, null, null);
        TimersScreen timers = new TimersScreen(null, null);
        RemindersScreen reminders = new RemindersScreen(null);
        SystemScreen system = new SystemScreen(null);

        NexusTuiLauncher launcher = new NexusTuiLauncher(overview, timers, reminders, system);

        DefaultVirtualTerminal terminal = new DefaultVirtualTerminal(new TerminalSize(100, 30));
        terminal.addInput(new KeyStroke(com.googlecode.lanterna.input.KeyType.Tab));
        terminal.addInput(new KeyStroke('q', false, false));

        TerminalScreen screen = new TerminalScreen(terminal);

        int exitCode = launcher.launch(screen);
        assertThat(exitCode).isEqualTo(0);
        assertThat(launcher.getCurrentScreenIndex()).isEqualTo(1);
    }
}
