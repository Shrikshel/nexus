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
}
