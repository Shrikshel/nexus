package com.nexus.capabilities.reminder;

import com.nexus.capabilities.reminder.dto.*;
import com.nexus.capabilities.reminder.storage.ReminderStorageManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ReminderStorageManagerTest {

    @TempDir
    Path tempDir;

    private ReminderStorageManager storageManager;

    @BeforeEach
    void setUp() {
        storageManager = new ReminderStorageManager();
        storageManager.setCustomStoragePath(tempDir.resolve("reminders.json"));
    }

    @Test
    @DisplayName("Creates reminder with auto-incremented friendly ID and system UUID")
    void testCreateReminderAutoId() {
        ReminderCreateRequest req1 = new ReminderCreateRequest(
                null, "Mom's Birthday", ReminderType.BIRTHDAY, "FAMILY",
                LocalDate.of(2026, 11, 25), LocalTime.of(9, 0), ZoneId.of("Asia/Kolkata"),
                ReminderRecurrence.YEARLY, List.of("7d", "1d", "0m"), Collections.emptyMap(),
                Map.of("birthYear", 1966)
        );

        ReminderEvent e1 = storageManager.createReminder(req1);
        assertThat(e1.id()).isEqualTo("EVT-001");
        assertThat(e1.uuid()).isNotBlank();
        assertThat(e1.title()).isEqualTo("Mom's Birthday");
        assertThat(e1.stages()).hasSize(3);
        assertThat(e1.state().status()).isEqualTo("ACTIVE");

        ReminderCreateRequest req2 = new ReminderCreateRequest(
                null, "Server Hosting Bill", ReminderType.BILL, "FINANCE",
                LocalDate.of(2026, 12, 1), LocalTime.of(10, 0), ZoneId.of("Asia/Kolkata"),
                ReminderRecurrence.MONTHLY, List.of("3d", "0m"), Collections.emptyMap(),
                Collections.emptyMap()
        );

        ReminderEvent e2 = storageManager.createReminder(req2);
        assertThat(e2.id()).isEqualTo("EVT-002");
        assertThat(e2.uuid()).isNotEqualTo(e1.uuid());

        List<ReminderEvent> all = storageManager.loadAll();
        assertThat(all).hasSize(2);
    }

    @Test
    @DisplayName("Respects custom friendly ID if specified")
    void testCreateReminderCustomId() {
        ReminderCreateRequest req = new ReminderCreateRequest(
                "EVT-MOM-BDAY", "Mom's Birthday", ReminderType.BIRTHDAY, "FAMILY",
                LocalDate.of(2026, 11, 25), LocalTime.of(9, 0), ZoneId.of("Asia/Kolkata"),
                ReminderRecurrence.YEARLY, List.of("0m"), Collections.emptyMap(), Collections.emptyMap()
        );

        ReminderEvent event = storageManager.createReminder(req);
        assertThat(event.id()).isEqualTo("EVT-MOM-BDAY");
        assertThat(event.uuid()).isNotBlank();
    }

    @Test
    @DisplayName("Finds reminder by friendly ID, UUID, UUID prefix, and title")
    void testFindByIdOrUuid() {
        ReminderCreateRequest req = new ReminderCreateRequest(
                "EVT-001", "AWS Hosting Bill", ReminderType.BILL, "FINANCE",
                LocalDate.of(2026, 12, 1), LocalTime.of(10, 0), ZoneId.of("Asia/Kolkata"),
                ReminderRecurrence.MONTHLY, List.of("1d", "0m"), Collections.emptyMap(), Collections.emptyMap()
        );
        ReminderEvent created = storageManager.createReminder(req);

        // 1. By friendly ID
        Optional<ReminderEvent> byId = storageManager.findByIdOrUuid("EVT-001");
        assertThat(byId).isPresent();
        assertThat(byId.get().id()).isEqualTo("EVT-001");

        // 2. By exact UUID
        Optional<ReminderEvent> byUuid = storageManager.findByIdOrUuid(created.uuid());
        assertThat(byUuid).isPresent();
        assertThat(byUuid.get().uuid()).isEqualTo(created.uuid());

        // 3. By UUID prefix (at least 4 chars)
        String prefix = created.uuid().substring(0, 8);
        Optional<ReminderEvent> byPrefix = storageManager.findByIdOrUuid(prefix);
        assertThat(byPrefix).isPresent();
        assertThat(byPrefix.get().uuid()).isEqualTo(created.uuid());

        // 4. By title
        Optional<ReminderEvent> byTitle = storageManager.findByIdOrUuid("AWS Hosting Bill");
        assertThat(byTitle).isPresent();
        assertThat(byTitle.get().id()).isEqualTo("EVT-001");

        // 5. Non-existent
        Optional<ReminderEvent> notFound = storageManager.findByIdOrUuid("NON-EXISTENT");
        assertThat(notFound).isEmpty();
    }

    @Test
    @DisplayName("Updates existing reminder and recalculates stages")
    void testUpdateReminder() {
        ReminderCreateRequest req = new ReminderCreateRequest(
                "EVT-001", "Old Title", ReminderType.EVENT, "GENERAL",
                LocalDate.of(2026, 12, 1), LocalTime.of(10, 0), ZoneId.of("Asia/Kolkata"),
                ReminderRecurrence.NONE, List.of("1d", "0m"), Collections.emptyMap(), Collections.emptyMap()
        );
        storageManager.createReminder(req);

        ReminderUpdateRequest updateReq = new ReminderUpdateRequest(
                "EVT-001", "New Title", ReminderType.BILL, "FINANCE",
                LocalDate.of(2026, 12, 5), LocalTime.of(11, 0), ZoneId.of("Asia/Kolkata"),
                ReminderRecurrence.MONTHLY, List.of("3d", "1d", "0m"),
                Map.of("0m", "Pay immediately"), Map.of("notes", "New note")
        );

        Optional<ReminderEvent> updatedOpt = storageManager.updateReminder(updateReq);
        assertThat(updatedOpt).isPresent();

        ReminderEvent updated = updatedOpt.get();
        assertThat(updated.title()).isEqualTo("New Title");
        assertThat(updated.type()).isEqualTo(ReminderType.BILL);
        assertThat(updated.targetDate()).isEqualTo(LocalDate.of(2026, 12, 5));
        assertThat(updated.stages()).hasSize(3);
        assertThat(updated.metadata().get("notes")).isEqualTo("New note");
    }

    @Test
    @DisplayName("Deletes reminder by ID or UUID")
    void testDeleteReminder() {
        ReminderCreateRequest req = new ReminderCreateRequest(
                "EVT-001", "To Delete", ReminderType.EVENT, "GENERAL",
                LocalDate.of(2026, 12, 1), LocalTime.of(10, 0), ZoneId.of("Asia/Kolkata"),
                ReminderRecurrence.NONE, List.of("0m"), Collections.emptyMap(), Collections.emptyMap()
        );
        ReminderEvent created = storageManager.createReminder(req);
        assertThat(storageManager.loadAll()).hasSize(1);

        boolean deleted = storageManager.deleteByIdOrUuid(created.uuid());
        assertThat(deleted).isTrue();
        assertThat(storageManager.loadAll()).isEmpty();
    }

    @Test
    @DisplayName("Imports events in merge vs replace mode")
    void testImportEvents() {
        ReminderCreateRequest req1 = new ReminderCreateRequest(
                "EVT-001", "Original Event", ReminderType.EVENT, "GENERAL",
                LocalDate.of(2026, 12, 1), LocalTime.of(10, 0), ZoneId.of("Asia/Kolkata"),
                ReminderRecurrence.NONE, List.of("0m"), Collections.emptyMap(), Collections.emptyMap()
        );
        ReminderEvent original = storageManager.createReminder(req1);

        // Incoming event with same UUID but updated title
        ReminderEvent updatedIncoming = new ReminderEvent(
                original.uuid(), "EVT-001", "Updated from Backup", ReminderType.EVENT, "GENERAL",
                original.targetDate(), original.targetTime(), original.timezone(), original.recurrence(),
                original.metadata(), original.stages(), original.state(), Collections.emptyList()
        );

        // Incoming brand new event
        ReminderEvent newIncoming = new ReminderEvent(
                "new-uuid-12345", "EVT-002", "Imported Brand New", ReminderType.BILL, "FINANCE",
                LocalDate.of(2026, 12, 10), LocalTime.of(9, 0), ZoneId.of("Asia/Kolkata"), ReminderRecurrence.NONE,
                Collections.emptyMap(), Collections.emptyList(), ReminderEventState.initial("2026", null, null), Collections.emptyList()
        );

        // Test Merge Mode
        ReminderImportResult mergeResult = storageManager.importEvents(List.of(updatedIncoming, newIncoming), false);
        assertThat(mergeResult.success()).isTrue();
        assertThat(mergeResult.importedCount()).isEqualTo(1);
        assertThat(mergeResult.updatedCount()).isEqualTo(1);

        List<ReminderEvent> mergedList = storageManager.loadAll();
        assertThat(mergedList).hasSize(2);
        assertThat(storageManager.findByIdOrUuid("EVT-001").get().title()).isEqualTo("Updated from Backup");

        // Test Replace Mode
        ReminderImportResult replaceResult = storageManager.importEvents(List.of(newIncoming), true);
        assertThat(replaceResult.success()).isTrue();
        List<ReminderEvent> replacedList = storageManager.loadAll();
        assertThat(replacedList).hasSize(1);
        assertThat(replacedList.get(0).title()).isEqualTo("Imported Brand New");
    }

    @Test
    @DisplayName("Auto-skips stages that are already in the past when creating a reminder mid-day")
    void testPastStagesAutoSkippedOnCreation() {
        // Event today in 4 hours
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now();
        java.time.ZonedDateTime target = now.plusHours(4);

        // Stages: 6h before (in the past by 2 hours), 2h before (in the future by 2 hours), 0m (in the future by 4 hours)
        ReminderCreateRequest req = new ReminderCreateRequest(
                "EVT-TODAY", "Mid-Day Reminder", ReminderType.EVENT, "WORK",
                target.toLocalDate(), target.toLocalTime(), target.getZone(),
                ReminderRecurrence.NONE, List.of("6h", "2h", "0m"), Collections.emptyMap(), Collections.emptyMap()
        );

        ReminderEvent event = storageManager.createReminder(req);
        assertThat(event.stages()).hasSize(3);

        // 6h was in the past -> SKIPPED
        assertThat(event.stages().get(0).stage()).isEqualTo("6h");
        assertThat(event.stages().get(0).status()).isEqualTo(ReminderStageStatus.SKIPPED);

        // 2h is in the future -> PENDING
        assertThat(event.stages().get(1).stage()).isEqualTo("2h");
        assertThat(event.stages().get(1).status()).isEqualTo(ReminderStageStatus.PENDING);

        // 0m is in the future -> PENDING
        assertThat(event.stages().get(2).stage()).isEqualTo("0m");
        assertThat(event.stages().get(2).status()).isEqualTo(ReminderStageStatus.PENDING);

        // Initial state locks onto first upcoming stage (2h), not the skipped 6h
        assertThat(event.state().nextStage()).isEqualTo("2h");
        assertThat(event.state().nextTriggerAt()).isEqualTo(event.stages().get(1).triggerAt());
        assertThat(event.state().status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("Reconciles past pending stages to SKIPPED")
    void testReconcileEvent() {
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now();
        ReminderStage pastStage = new ReminderStage("7h", "-PT7H", now.minusHours(2).toInstant(), null, null, ReminderStageStatus.PENDING);
        ReminderStage futureStage = new ReminderStage("1h", "-PT1H", now.plusHours(1).toInstant(), null, null, ReminderStageStatus.PENDING);

        ReminderEventState state = new ReminderEventState("2026", "ACTIVE", null, "7h", pastStage.triggerAt());
        ReminderEvent event = new ReminderEvent(
                "reconcile-uuid", "EVT-REC", "Reconcile Test", ReminderType.EVENT, "GENERAL",
                now.toLocalDate(), now.toLocalTime(), now.getZone(), ReminderRecurrence.NONE,
                Collections.emptyMap(), List.of(pastStage, futureStage), state, Collections.emptyList()
        );
        storageManager.saveEvent(event);

        ReminderEvent reconciled = storageManager.reconcileEvent(event, now.toInstant());
        assertThat(reconciled.stages().get(0).status()).isEqualTo(ReminderStageStatus.SKIPPED);
        assertThat(reconciled.stages().get(1).status()).isEqualTo(ReminderStageStatus.PENDING);
        assertThat(reconciled.state().nextStage()).isEqualTo("1h");
    }

    @Test
    @DisplayName("Resolves default user storage path under user.home for non-root users")
    void testResolveStoragePathForUser() {
        ReminderStorageManager sm = new ReminderStorageManager();
        Path resolved = sm.resolveStoragePath();
        assertThat(resolved).isEqualTo(Path.of(System.getProperty("user.home"), ".nexus", "data", "reminders.json"));
    }

    @Test
    @DisplayName("Auto fast-forwards past-date birthday reminders and captures birthYear")
    void testCreateBirthdayWithPastDateAutoFastForwardsAndCapturesBirthYear() {
        ReminderCreateRequest req = new ReminderCreateRequest(
                "EVT-SUVARNA", "Suvarna", ReminderType.BIRTHDAY, "General",
                LocalDate.of(2000, 3, 5), LocalTime.of(0, 0), ZoneId.of("Asia/Kolkata"),
                ReminderRecurrence.YEARLY, List.of("7d", "3d", "1d", "0m"), Collections.emptyMap(),
                Collections.emptyMap()
        );

        ReminderEvent created = storageManager.createReminder(req);

        assertThat(created.title()).isEqualTo("Suvarna");
        assertThat(created.type()).isEqualTo(ReminderType.BIRTHDAY);
        assertThat(created.metadata().get("birthYear")).isEqualTo(2000);
        assertThat(created.metadata().get("anchorDate")).isEqualTo("2000-03-05");
        assertThat(created.metadata().get("anchorMonth")).isEqualTo(3);
        assertThat(created.metadata().get("anchorDayOfMonth")).isEqualTo(5);

        // Target date must be fast-forwarded to upcoming occurrence
        assertThat(created.targetDate().getYear()).isGreaterThanOrEqualTo(LocalDate.now().getYear());
        assertThat(created.targetDate().getMonthValue()).isEqualTo(3);
        assertThat(created.targetDate().getDayOfMonth()).isEqualTo(5);

        // Stages must be armed and pending
        assertThat(created.stages()).hasSize(4);
        assertThat(created.stages().stream().allMatch(s -> s.status() == ReminderStageStatus.PENDING)).isTrue();
        assertThat(created.state().status()).isEqualTo("ACTIVE");
        assertThat(created.state().nextStage()).isEqualTo("7d");
        assertThat(created.state().nextTriggerAt()).isNotNull();
        assertThat(created.state().nextTriggerAt()).isAfter(java.time.Instant.now());
        assertThat(created.state().activeCycle()).isEqualTo(String.valueOf(created.targetDate().getYear()));
    }

    @Test
    @DisplayName("Auto-heals existing stuck recurring reminders that have past cycle and skipped stages")
    void testAutoHealStuckPastRecurringReminder() {
        // Construct an unhealed event matching the user's exact reported bug
        List<ReminderStage> skippedStages = List.of(
                new ReminderStage("7d", "-P7D", java.time.Instant.parse("2000-02-27T00:00:00Z"), null, null, ReminderStageStatus.SKIPPED),
                new ReminderStage("3d", "-P3D", java.time.Instant.parse("2000-03-02T00:00:00Z"), null, null, ReminderStageStatus.SKIPPED),
                new ReminderStage("1d", "-P1D", java.time.Instant.parse("2000-03-04T00:00:00Z"), null, null, ReminderStageStatus.SKIPPED),
                new ReminderStage("0m", "PT0S", java.time.Instant.parse("2000-03-05T00:00:00Z"), null, null, ReminderStageStatus.SKIPPED)
        );
        ReminderEventState stuckState = new ReminderEventState("2000", "ACTIVE", null, null, null);
        ReminderEvent stuckEvent = new ReminderEvent(
                "stuck-uuid-1", "EVT-STUCK", "Suvarna", ReminderType.BIRTHDAY, "General",
                LocalDate.of(2000, 3, 5), LocalTime.of(0, 0), ZoneId.of("Asia/Kolkata"),
                ReminderRecurrence.YEARLY, Collections.emptyMap(),
                skippedStages, stuckState, Collections.emptyList()
        );
        storageManager.saveEvent(stuckEvent);

        int healedCount = storageManager.autoHealRecurringReminders();
        assertThat(healedCount).isEqualTo(1);

        ReminderEvent healed = storageManager.findByIdOrUuid("EVT-STUCK").orElseThrow();
        assertThat(healed.metadata().get("birthYear")).isEqualTo(2000);
        assertThat(healed.targetDate().getYear()).isGreaterThanOrEqualTo(LocalDate.now().getYear());
        assertThat(healed.state().status()).isEqualTo("ACTIVE");
        assertThat(healed.state().nextStage()).isEqualTo("7d");
        assertThat(healed.state().nextTriggerAt()).isNotNull();
        assertThat(healed.state().activeCycle()).isEqualTo(String.valueOf(healed.targetDate().getYear()));
        assertThat(healed.stages().stream().allMatch(s -> s.status() == ReminderStageStatus.PENDING)).isTrue();
    }

    @Test
    @DisplayName("Handles leap-year past dates preserving Feb 29 anchor")
    void testCreateLeapYearBirthdayPastDate() {
        ReminderCreateRequest req = new ReminderCreateRequest(
                "EVT-LEAP", "Leap Baby", ReminderType.BIRTHDAY, "General",
                LocalDate.of(2000, 2, 29), LocalTime.of(12, 0), ZoneId.of("Asia/Kolkata"),
                ReminderRecurrence.YEARLY, List.of("0m"), Collections.emptyMap(), Collections.emptyMap()
        );

        ReminderEvent created = storageManager.createReminder(req);
        assertThat(created.metadata().get("birthYear")).isEqualTo(2000);
        assertThat(created.metadata().get("anchorMonth")).isEqualTo(2);
        assertThat(created.metadata().get("anchorDayOfMonth")).isEqualTo(29);

        // Upcoming target must be either Feb 28 (non-leap) or Feb 29 (leap)
        assertThat(created.targetDate().getMonthValue()).isEqualTo(2);
        if (created.targetDate().isLeapYear()) {
            assertThat(created.targetDate().getDayOfMonth()).isEqualTo(29);
        } else {
            assertThat(created.targetDate().getDayOfMonth()).isEqualTo(28);
        }
    }
}
