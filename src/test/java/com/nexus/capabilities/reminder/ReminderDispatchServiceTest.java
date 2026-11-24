package com.nexus.capabilities.reminder;

import com.nexus.adapters.telegram.TelegramClient;
import com.nexus.capabilities.reminder.dispatch.ReminderDispatchService;
import com.nexus.capabilities.reminder.dto.*;
import com.nexus.capabilities.reminder.scheduler.ReminderSchedulerProvider;
import com.nexus.capabilities.reminder.storage.ReminderStorageManager;
import com.nexus.config.NexusProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ReminderDispatchServiceTest {

    @TempDir
    Path tempDir;

    private ReminderStorageManager storageManager;
    private ReminderSchedulerProvider schedulerProvider;
    private TelegramClient telegramClient;
    private NexusProperties properties;
    private ReminderDispatchService dispatchService;

    @BeforeEach
    void setUp() {
        storageManager = new ReminderStorageManager();
        storageManager.setCustomStoragePath(tempDir.resolve("reminders.json"));

        schedulerProvider = mock(ReminderSchedulerProvider.class);
        telegramClient = mock(TelegramClient.class);
        when(telegramClient.sendMessage(anyString(), anyLong(), anyString())).thenReturn(true);

        properties = new NexusProperties();
        NexusProperties.TelegramConfig tg = properties.getTelegram();
        tg.setEnabled(true);
        tg.setBotToken("mock-token-123");
        tg.setAllowedChatId(999L);

        dispatchService = new ReminderDispatchService(storageManager, schedulerProvider, telegramClient, properties);
    }

    @Test
    @DisplayName("Delivers stage 1, progresses to stage 2, and rearms systemd timer")
    void testStageProgression() {
        ReminderCreateRequest req = new ReminderCreateRequest(
                "EVT-001", "Team Standup", ReminderType.MEETING, "WORK",
                LocalDate.of(2026, 12, 1), LocalTime.of(10, 0), ZoneId.of("Asia/Kolkata"),
                ReminderRecurrence.NONE, List.of("1h", "0m"), Collections.emptyMap(), Collections.emptyMap()
        );
        ReminderEvent event = storageManager.createReminder(req);

        assertThat(event.state().nextStage()).isEqualTo("1h");

        // Dispatch stage 1 (1h)
        boolean result = dispatchService.dispatch("EVT-001");
        assertThat(result).isTrue();

        ReminderEvent updated = storageManager.findByIdOrUuid("EVT-001").orElseThrow();
        assertThat(updated.stages().get(0).status()).isEqualTo(ReminderStageStatus.DELIVERED);
        assertThat(updated.stages().get(1).status()).isEqualTo(ReminderStageStatus.PENDING);
        assertThat(updated.state().lastDispatchedStage()).isEqualTo("1h");
        assertThat(updated.state().nextStage()).isEqualTo("0m");
        assertThat(updated.state().status()).isEqualTo("ACTIVE");

        verify(schedulerProvider, times(1)).armTimer(any(ReminderEvent.class));
        verify(telegramClient, times(1)).sendMessage(eq("mock-token-123"), eq(999L), contains("Team Standup"));
    }

    @Test
    @DisplayName("Synthesizes birthday age celebration message on 0m stage")
    void testBirthdayCelebrationMessage() {
        ReminderCreateRequest req = new ReminderCreateRequest(
                "EVT-BDAY", "Alice's Birthday", ReminderType.BIRTHDAY, "FAMILY",
                LocalDate.of(2026, 11, 25), LocalTime.of(9, 0), ZoneId.of("Asia/Kolkata"),
                ReminderRecurrence.YEARLY, List.of("0m"), Collections.emptyMap(),
                Map.of("birthYear", 1990)
        );
        storageManager.createReminder(req);

        dispatchService.dispatch("EVT-BDAY");

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(eq("mock-token-123"), eq(999L), msgCaptor.capture());

        String message = msgCaptor.getValue();
        assertThat(message).contains("Happy Birthday!");
        assertThat(message).contains("Alice's Birthday");
        assertThat(message).contains("Celebrating <b>36 years</b> today!");
    }

    @Test
    @DisplayName("Completes one-off event and disarms timers after final stage")
    void testOneOffEventCompletion() {
        ReminderCreateRequest req = new ReminderCreateRequest(
                "EVT-001", "One-time Conference", ReminderType.EVENT, "WORK",
                LocalDate.of(2026, 12, 1), LocalTime.of(10, 0), ZoneId.of("Asia/Kolkata"),
                ReminderRecurrence.NONE, List.of("0m"), Collections.emptyMap(), Collections.emptyMap()
        );
        storageManager.createReminder(req);

        dispatchService.dispatch("EVT-001");

        ReminderEvent completed = storageManager.findByIdOrUuid("EVT-001").orElseThrow();
        assertThat(completed.state().status()).isEqualTo("COMPLETED");
        assertThat(completed.history()).hasSize(1);
        assertThat(completed.history().get(0).cycle()).isEqualTo("2026");

        verify(schedulerProvider, times(1)).disarmTimer("EVT-001");
    }

    @Test
    @DisplayName("Rolls over recurring event across recurrence interval (MONTHLY)")
    void testMonthlyRecurrenceRollover() {
        ReminderCreateRequest req = new ReminderCreateRequest(
                "EVT-BILL", "Monthly Rent", ReminderType.BILL, "FINANCE",
                LocalDate.of(2026, 11, 1), LocalTime.of(9, 0), ZoneId.of("Asia/Kolkata"),
                ReminderRecurrence.MONTHLY, List.of("0m"), Collections.emptyMap(), Collections.emptyMap()
        );
        storageManager.createReminder(req);

        dispatchService.dispatch("EVT-BILL");

        ReminderEvent rolledOver = storageManager.findByIdOrUuid("EVT-BILL").orElseThrow();
        assertThat(rolledOver.targetDate()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(rolledOver.state().status()).isEqualTo("ACTIVE");
        assertThat(rolledOver.history()).hasSize(1);
        assertThat(rolledOver.history().get(0).targetDate()).isEqualTo(LocalDate.of(2026, 11, 1));

        // Timers rearms for new target date
        verify(schedulerProvider, times(1)).armTimer(any(ReminderEvent.class));
    }

    @Test
    @DisplayName("Auto-skips overdue stages before the arrived stage during dispatch")
    void testAutoSkipsOverdueStagesOnDispatch() {
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now();
        ReminderStage stage7h = new ReminderStage("7h", "-PT7H", now.minusHours(2).toInstant(), null, null, ReminderStageStatus.PENDING);
        ReminderStage stage6h = new ReminderStage("6h", "-PT6H", now.minusHours(1).toInstant(), null, null, ReminderStageStatus.PENDING);
        ReminderStage stage5h = new ReminderStage("5h", "-PT5H", now.toInstant(), null, null, ReminderStageStatus.PENDING);
        ReminderStage stage4h = new ReminderStage("4h", "-PT4H", now.plusHours(1).toInstant(), null, null, ReminderStageStatus.PENDING);

        ReminderEventState state = new ReminderEventState("2026", "ACTIVE", null, "7h", stage7h.triggerAt());
        ReminderEvent event = new ReminderEvent(
                "evt-overdue-uuid", "EVT-OVERDUE", "Overdue Catchup Test", ReminderType.EVENT, "GENERAL",
                now.toLocalDate(), now.toLocalTime(), now.getZone(), ReminderRecurrence.NONE,
                Collections.emptyMap(), List.of(stage7h, stage6h, stage5h, stage4h), state, Collections.emptyList()
        );
        storageManager.saveEvent(event);

        // Dispatch fires at `now`
        boolean dispatched = dispatchService.dispatch("EVT-OVERDUE");
        assertThat(dispatched).isTrue();

        ReminderEvent updated = storageManager.findByIdOrUuid("EVT-OVERDUE").orElseThrow();
        // 7h and 6h were skipped
        assertThat(updated.stages().get(0).stage()).isEqualTo("7h");
        assertThat(updated.stages().get(0).status()).isEqualTo(ReminderStageStatus.SKIPPED);

        assertThat(updated.stages().get(1).stage()).isEqualTo("6h");
        assertThat(updated.stages().get(1).status()).isEqualTo(ReminderStageStatus.SKIPPED);

        // 5h was delivered
        assertThat(updated.stages().get(2).stage()).isEqualTo("5h");
        assertThat(updated.stages().get(2).status()).isEqualTo(ReminderStageStatus.DELIVERED);

        // Next pending is 4h
        assertThat(updated.stages().get(3).stage()).isEqualTo("4h");
        assertThat(updated.stages().get(3).status()).isEqualTo(ReminderStageStatus.PENDING);
        assertThat(updated.state().nextStage()).isEqualTo("4h");
        assertThat(updated.state().lastDispatchedStage()).isEqualTo("5h");
    }

    @Test
    @DisplayName("Rolls over sub-day recurring event advancing both date and clock (EVERY 16h)")
    void testSubDayRecurrenceRollover() {
        ReminderCreateRequest req = new ReminderCreateRequest(
                "EVT-SHIFT", "Shift Handover", ReminderType.EVENT, "WORK",
                LocalDate.of(2026, 11, 24), LocalTime.of(18, 0), ZoneId.of("Asia/Kolkata"),
                ReminderRecurrence.fromString("every 16h"), List.of("0m"), Collections.emptyMap(), Collections.emptyMap()
        );
        storageManager.createReminder(req);

        dispatchService.dispatch("EVT-SHIFT");

        ReminderEvent rolledOver = storageManager.findByIdOrUuid("EVT-SHIFT").orElseThrow();
        assertThat(rolledOver.targetDate()).isEqualTo(LocalDate.of(2026, 11, 25));
        assertThat(rolledOver.targetTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(rolledOver.state().status()).isEqualTo("ACTIVE");
        assertThat(rolledOver.state().activeCycle()).isEqualTo("2026-11-25T10:00");
        assertThat(rolledOver.history()).hasSize(1);
        assertThat(rolledOver.history().get(0).targetDate()).isEqualTo(LocalDate.of(2026, 11, 24));

        verify(schedulerProvider, times(1)).armTimer(any(ReminderEvent.class));
    }

    @Test
    @DisplayName("Test dispatch sends preview notification without advancing event state")
    void testTestDispatch() {
        ReminderCreateRequest req = new ReminderCreateRequest(
                "EVT-001", "Dentist Appointment", ReminderType.EVENT, "HEALTH",
                LocalDate.of(2026, 12, 15), LocalTime.of(14, 0), ZoneId.of("Asia/Kolkata"),
                ReminderRecurrence.NONE, List.of("1d", "0m"), Collections.emptyMap(), Collections.emptyMap()
        );
        storageManager.createReminder(req);

        boolean testResult = dispatchService.testDispatch("EVT-001");
        assertThat(testResult).isTrue();

        ReminderEvent event = storageManager.findByIdOrUuid("EVT-001").orElseThrow();
        assertThat(event.stages().get(0).status()).isEqualTo(ReminderStageStatus.PENDING);
        assertThat(event.stages().get(1).status()).isEqualTo(ReminderStageStatus.PENDING);

        verify(telegramClient).sendMessage(eq("mock-token-123"), eq(999L), contains("[TEST / PREVIEW NOTIFICATION]"));
    }
}
