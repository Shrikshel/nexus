package com.nexus.adapters.tui.screens;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import com.nexus.capabilities.reminder.dispatch.ReminderDispatchService;
import com.nexus.capabilities.reminder.dto.*;
import com.nexus.capabilities.reminder.storage.ReminderStorageManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RemindersScreenTest {

    private ReminderStorageManager storageManager;
    private ReminderDispatchService dispatchService;
    private RemindersScreen screen;

    @BeforeEach
    void setup() {
        storageManager = Mockito.mock(ReminderStorageManager.class);
        dispatchService = Mockito.mock(ReminderDispatchService.class);

        ReminderStage s1 = new ReminderStage("7d", "7d", Instant.now(), null, Instant.now(), ReminderStageStatus.DELIVERED);
        ReminderStage s2 = new ReminderStage("1d", "1d", Instant.now().plusSeconds(3600), null, null, ReminderStageStatus.PENDING);
        ReminderEventState state1 = new ReminderEventState("2026", "ACTIVE", "7d", "1d", Instant.now().plusSeconds(3600));

        ReminderEvent r1 = new ReminderEvent(
                "uuid-1", "EVT-001", "Mom's Birthday", ReminderType.BIRTHDAY, "Family",
                LocalDate.of(2026, 11, 25), LocalTime.of(9, 0), ZoneId.of("UTC"),
                ReminderRecurrence.YEARLY, Collections.emptyMap(),
                List.of(s1, s2), state1, Collections.emptyList()
        );

        ReminderEvent r2 = new ReminderEvent(
                "uuid-2", "EVT-002", "AWS Hosting Bill", ReminderType.BILL, "Cloud",
                LocalDate.of(2026, 12, 1), LocalTime.of(10, 0), ZoneId.of("UTC"),
                ReminderRecurrence.MONTHLY, Collections.emptyMap(),
                List.of(s2), state1, Collections.emptyList()
        );

        when(storageManager.loadAll()).thenReturn(List.of(r1, r2));
        screen = new RemindersScreen(storageManager, dispatchService);
    }

    @Test
    @DisplayName("Screen reports correct title and populates initial reminders")
    void testInitialState() {
        assertThat(screen.getTitle()).isEqualTo("3: Reminders");
        assertThat(screen.getFilteredReminders()).hasSize(2);
        assertThat(screen.getSelectedIndex()).isEqualTo(0);
        assertThat(screen.getCurrentFilter()).isEqualTo(RemindersScreen.FilterMode.ALL);
    }

    @Test
    @DisplayName("Arrow keys navigate through reminder rows and clamp at boundaries")
    void testCursorNavigation() {
        boolean handled = screen.handleInput(new KeyStroke(KeyType.ArrowDown));
        assertThat(handled).isTrue();
        assertThat(screen.getSelectedIndex()).isEqualTo(1);

        handled = screen.handleInput(new KeyStroke('j', false, false));
        assertThat(handled).isTrue();
        assertThat(screen.getSelectedIndex()).isEqualTo(1); // clamp

        handled = screen.handleInput(new KeyStroke(KeyType.ArrowUp));
        assertThat(handled).isTrue();
        assertThat(screen.getSelectedIndex()).isEqualTo(0);
    }

    @Test
    @DisplayName("Pressing 'f' cycles filter modes and updates filtered list")
    void testFilterCycling() {
        assertThat(screen.getCurrentFilter()).isEqualTo(RemindersScreen.FilterMode.ALL);
        assertThat(screen.getFilteredReminders()).hasSize(2);

        // Cycle to ACTIVE
        screen.handleInput(new KeyStroke('f', false, false));
        assertThat(screen.getCurrentFilter()).isEqualTo(RemindersScreen.FilterMode.ACTIVE);
        assertThat(screen.getFilteredReminders()).hasSize(2);

        // Cycle to BIRTHDAYS
        screen.handleInput(new KeyStroke('f', false, false));
        assertThat(screen.getCurrentFilter()).isEqualTo(RemindersScreen.FilterMode.BIRTHDAYS);
        assertThat(screen.getFilteredReminders()).hasSize(1);
        assertThat(screen.getFilteredReminders().get(0).id()).isEqualTo("EVT-001");

        // Cycle to BILLS
        screen.handleInput(new KeyStroke('f', false, false));
        assertThat(screen.getCurrentFilter()).isEqualTo(RemindersScreen.FilterMode.BILLS);
        assertThat(screen.getFilteredReminders()).hasSize(1);
        assertThat(screen.getFilteredReminders().get(0).id()).isEqualTo("EVT-002");
    }

    @Test
    @DisplayName("Pressing 't' sends a test alert without advancing stage")
    void testTestAlert() {
        when(dispatchService.testDispatch(eq("EVT-001"))).thenReturn(true);

        boolean handled = screen.handleInput(new KeyStroke('t', false, false));
        assertThat(handled).isTrue();

        verify(dispatchService).testDispatch("EVT-001");
        assertThat(screen.getToastMessage()).contains("Test alert sent for EVT-001");
    }

    @Test
    @DisplayName("Pressing 'x' or Enter dispatches next stage")
    void testDispatchNextStage() {
        when(dispatchService.dispatch(eq("EVT-001"))).thenReturn(true);

        boolean handled = screen.handleInput(new KeyStroke('x', false, false));
        assertThat(handled).isTrue();

        verify(dispatchService).dispatch("EVT-001");
        assertThat(screen.getToastMessage()).contains("Dispatched stage for EVT-001");
    }

    @Test
    @DisplayName("Pressing 'd' prompts delete confirmation, and 'y' deletes")
    void testDeleteReminder() {
        when(storageManager.deleteByIdOrUuid(eq("EVT-001"))).thenReturn(true);

        screen.handleInput(new KeyStroke('d', false, false));
        assertThat(screen.isConfirmingDelete()).isTrue();

        screen.handleInput(new KeyStroke('y', false, false));
        assertThat(screen.isConfirmingDelete()).isFalse();

        verify(storageManager).deleteByIdOrUuid("EVT-001");
        assertThat(screen.getToastMessage()).contains("Deleted reminder EVT-001");
    }

    @Test
    @DisplayName("Pressing 'd' prompts delete confirmation, and 'n' cancels")
    void testDeleteCancel() {
        screen.handleInput(new KeyStroke('d', false, false));
        assertThat(screen.isConfirmingDelete()).isTrue();

        screen.handleInput(new KeyStroke('n', false, false));
        assertThat(screen.isConfirmingDelete()).isFalse();
        assertThat(screen.getToastMessage()).contains("cancelled");
    }

    @Test
    @DisplayName("Renders reminder table and inspector on virtual terminal without error")
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
