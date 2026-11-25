package com.nexus.adapters.tui.screens;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import com.nexus.capabilities.scheduler.timer.dto.TimerDeleteRequest;
import com.nexus.capabilities.scheduler.timer.dto.TimerDeleteResult;
import com.nexus.capabilities.scheduler.timer.dto.TimerInfo;
import com.nexus.capabilities.scheduler.timer.dto.TimerRunRequest;
import com.nexus.capabilities.scheduler.timer.dto.TimerRunResult;
import com.nexus.capabilities.scheduler.timer.dto.TimerScope;
import com.nexus.platform.scheduler.ScheduledTaskManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TimersScreenTest {

    private ScheduledTaskManager taskManager;
    private TimersScreen screen;

    @BeforeEach
    void setup() {
        taskManager = Mockito.mock(ScheduledTaskManager.class);

        TimerInfo t1 = new TimerInfo("NX001", "backup-db", "nexus-nx001-backup-db", "daily", "daily at 3am",
                "Backup database", TimerScope.USER, "/tmp/s1.sh", "/tmp/u1.timer", "/tmp/u1.service",
                true, "in 3 hours", "yesterday", "SUCCESS (0)", Instant.now());
        TimerInfo t2 = new TimerInfo("NX002", "cleanup-tmp", "nexus-nx002-cleanup-tmp", "weekly", "weekly on sun",
                "Clean temp files", TimerScope.SYSTEM, "/tmp/s2.sh", "/tmp/u2.timer", "/tmp/u2.service",
                false, "in 4 days", "never", "N/A", Instant.now());

        when(taskManager.listTimers(any())).thenReturn(List.of(t1, t2));
        screen = new TimersScreen(taskManager, null);
    }

    @Test
    @DisplayName("Screen reports correct title and populates initial timers")
    void testInitialState() {
        assertThat(screen.getTitle()).isEqualTo("2: Timers");
        assertThat(screen.getCachedTimers()).hasSize(2);
        assertThat(screen.getSelectedIndex()).isEqualTo(0);
        assertThat(screen.getCurrentScope()).isEqualTo(TimerScope.ALL);
    }

    @Test
    @DisplayName("Arrow keys navigate through timer rows and clamp at boundaries")
    void testCursorNavigation() {
        // Move down
        boolean handled = screen.handleInput(new KeyStroke(KeyType.ArrowDown));
        assertThat(handled).isTrue();
        assertThat(screen.getSelectedIndex()).isEqualTo(1);

        // Move down again (should clamp at 1)
        handled = screen.handleInput(new KeyStroke('j', false, false));
        assertThat(handled).isTrue();
        assertThat(screen.getSelectedIndex()).isEqualTo(1);

        // Move up
        handled = screen.handleInput(new KeyStroke(KeyType.ArrowUp));
        assertThat(handled).isTrue();
        assertThat(screen.getSelectedIndex()).isEqualTo(0);

        // Move up again (should clamp at 0)
        handled = screen.handleInput(new KeyStroke('k', false, false));
        assertThat(handled).isTrue();
        assertThat(screen.getSelectedIndex()).isEqualTo(0);
    }

    @Test
    @DisplayName("Pressing 's' cycles timer scope (ALL -> USER -> SYSTEM -> ALL)")
    void testScopeCycling() {
        assertThat(screen.getCurrentScope()).isEqualTo(TimerScope.ALL);

        screen.handleInput(new KeyStroke('s', false, false));
        assertThat(screen.getCurrentScope()).isEqualTo(TimerScope.USER);

        screen.handleInput(new KeyStroke('s', false, false));
        assertThat(screen.getCurrentScope()).isEqualTo(TimerScope.SYSTEM);

        screen.handleInput(new KeyStroke('s', false, false));
        assertThat(screen.getCurrentScope()).isEqualTo(TimerScope.ALL);
    }

    @Test
    @DisplayName("Pressing 'x' triggers timer run on selected timer")
    void testRunTimer() {
        when(taskManager.runTimer(any(TimerRunRequest.class)))
                .thenReturn(new TimerRunResult(true, "NX001", 0, "OK", 150L, "Success"));

        boolean handled = screen.handleInput(new KeyStroke('x', false, false));
        assertThat(handled).isTrue();

        verify(taskManager).runTimer(any(TimerRunRequest.class));
        assertThat(screen.getToastMessage()).contains("Successfully ran NX001");
    }

    @Test
    @DisplayName("Pressing 'd' prompts confirmation, and 'y' executes delete")
    void testDeleteTimerWithConfirmation() {
        when(taskManager.deleteTimer(any(TimerDeleteRequest.class)))
                .thenReturn(new TimerDeleteResult(true, "NX001", "nexus-nx001-backup-db", true, true, true, "Deleted"));

        // Trigger delete prompt
        screen.handleInput(new KeyStroke('d', false, false));
        assertThat(screen.isConfirmingDelete()).isTrue();

        // Confirm delete with 'y'
        screen.handleInput(new KeyStroke('y', false, false));
        assertThat(screen.isConfirmingDelete()).isFalse();

        verify(taskManager).deleteTimer(any(TimerDeleteRequest.class));
        assertThat(screen.getToastMessage()).contains("Deleted timer NX001");
    }

    @Test
    @DisplayName("Pressing 'd' prompts confirmation, and 'n' or ESC cancels")
    void testDeleteTimerCancel() {
        screen.handleInput(new KeyStroke('d', false, false));
        assertThat(screen.isConfirmingDelete()).isTrue();

        screen.handleInput(new KeyStroke('n', false, false));
        assertThat(screen.isConfirmingDelete()).isFalse();
        assertThat(screen.getToastMessage()).contains("cancelled");
    }

    @Test
    @DisplayName("Pressing 'c' opens create timer modal and submitting creates timer")
    void testCreateTimerModal() {
        TimerInfo created = new TimerInfo("NX003", "nightly-sync", "nexus-nx003-nightly-sync", "hourly", "hourly",
                "Desc", TimerScope.USER, "/tmp/s.sh", "/tmp/u.timer", "/tmp/u.service", true, "in 1h", "never", "N/A", Instant.now());
        when(taskManager.createTimer(any(com.nexus.capabilities.scheduler.timer.dto.TimerCreateRequest.class)))
                .thenReturn(new com.nexus.capabilities.scheduler.timer.dto.TimerCreateResult(true, created, "/tmp/s.sh", "/tmp/u.timer", "/tmp/u.service", "enable", "Success"));

        // Press 'c' to open modal
        screen.handleInput(new KeyStroke('c', false, false));
        assertThat(screen.getActiveModal()).isNotNull();

        // Fill modal fields and submit
        screen.getActiveModal().setValue("name", "nightly-sync");
        screen.getActiveModal().setValue("schedule", "hourly");
        screen.handleInput(new KeyStroke(KeyType.Enter));

        assertThat(screen.getActiveModal()).isNull();
        verify(taskManager).createTimer(any(com.nexus.capabilities.scheduler.timer.dto.TimerCreateRequest.class));
        assertThat(screen.getToastMessage()).contains("Created timer NX003");
    }

    @Test
    @DisplayName("Modal cancellation closes modal cleanly without clearing action shortcuts")
    void testModalCancellationKeepsActions() {
        screen.handleInput(new KeyStroke('c', false, false));
        assertThat(screen.getActiveModal()).isNotNull();

        screen.handleInput(new KeyStroke(KeyType.Escape));
        assertThat(screen.getActiveModal()).isNull();
        assertThat(screen.getActionShortcuts()).contains("[c] Create");
    }

    @Test
    @DisplayName("Renders timer table and inspector on virtual terminal without error")
    void testRender() throws Exception {
        DefaultVirtualTerminal terminal = new DefaultVirtualTerminal(new TerminalSize(100, 30));
        TerminalScreen terminalScreen = new TerminalScreen(terminal);
        terminalScreen.startScreen();
        TextGraphics tg = terminalScreen.newTextGraphics();

        screen.render(tg, new TerminalSize(100, 30), 3, 24);

        terminalScreen.refresh();
        terminalScreen.stopScreen();
    }
}
