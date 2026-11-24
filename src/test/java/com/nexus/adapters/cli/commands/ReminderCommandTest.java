package com.nexus.adapters.cli.commands;

import com.nexus.capabilities.reminder.dispatch.ReminderDispatchService;
import com.nexus.capabilities.reminder.dto.*;
import com.nexus.capabilities.reminder.scheduler.ReminderSchedulerProvider;
import com.nexus.capabilities.reminder.storage.ReminderStorageManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class ReminderCommandTest {

    @TempDir
    Path tempDir;

    private ReminderStorageManager storageManager;
    private ReminderSchedulerProvider schedulerProvider;
    private ReminderDispatchService dispatchService;
    private CommandLine commandLine;
    private StringWriter outWriter;
    private StringWriter errWriter;

    @BeforeEach
    void setUp() {
        storageManager = new ReminderStorageManager();
        storageManager.setCustomStoragePath(tempDir.resolve("reminders.json"));

        schedulerProvider = Mockito.mock(ReminderSchedulerProvider.class);
        dispatchService = Mockito.mock(ReminderDispatchService.class);

        ReminderCommand reminderCommand = new ReminderCommand(storageManager, schedulerProvider, dispatchService);
        commandLine = new CommandLine(reminderCommand);

        outWriter = new StringWriter();
        errWriter = new StringWriter();
        commandLine.setOut(new PrintWriter(outWriter));
        commandLine.setErr(new PrintWriter(errWriter));
    }

    @Test
    @DisplayName("Lists reminders cleanly when empty")
    void testListEmpty() {
        int exitCode = commandLine.execute("list");
        assertThat(exitCode).isEqualTo(0);
        assertThat(outWriter.toString()).contains("No reminders currently configured");
    }

    @Test
    @DisplayName("Lists active reminders when populated")
    void testListPopulated() {
        ReminderCreateRequest req = new ReminderCreateRequest(
                "EVT-001", "Mom's Birthday", ReminderType.BIRTHDAY, "FAMILY",
                LocalDate.of(2026, 11, 25), LocalTime.of(9, 0), ZoneId.of("Asia/Kolkata"),
                ReminderRecurrence.YEARLY, List.of("1d", "0m"), Collections.emptyMap(), Collections.emptyMap()
        );
        storageManager.createReminder(req);

        int exitCode = commandLine.execute("list");
        assertThat(exitCode).isEqualTo(0);
        assertThat(outWriter.toString()).contains("EVT-001");
        assertThat(outWriter.toString()).contains("Mom's Birthday");
        assertThat(outWriter.toString()).contains("BIRTHDAY");
    }

    @Test
    @DisplayName("Next subcommand displays immediate upcoming reminder")
    void testNextSubcommand() {
        ReminderCreateRequest req = new ReminderCreateRequest(
                "EVT-001", "Team Sync", ReminderType.MEETING, "WORK",
                LocalDate.of(2026, 12, 1), LocalTime.of(10, 0), ZoneId.of("Asia/Kolkata"),
                ReminderRecurrence.NONE, List.of("1h", "0m"), Collections.emptyMap(), Collections.emptyMap()
        );
        storageManager.createReminder(req);

        int exitCode = commandLine.execute("next");
        assertThat(exitCode).isEqualTo(0);
        assertThat(outWriter.toString()).contains("EVT-001");
        assertThat(outWriter.toString()).contains("Team Sync");
    }

    @Test
    @DisplayName("Show subcommand displays full details and stage schedule")
    void testShowSubcommand() {
        ReminderCreateRequest req = new ReminderCreateRequest(
                "EVT-001", "Server Bill", ReminderType.BILL, "FINANCE",
                LocalDate.of(2026, 12, 1), LocalTime.of(10, 0), ZoneId.of("Asia/Kolkata"),
                ReminderRecurrence.MONTHLY, List.of("3d", "0m"), Collections.emptyMap(), Collections.emptyMap()
        );
        storageManager.createReminder(req);

        int exitCode = commandLine.execute("show", "EVT-001");
        assertThat(exitCode).isEqualTo(0);
        assertThat(outWriter.toString()).contains("Server Bill");
        assertThat(outWriter.toString()).contains("Configured Notification Stages");
        assertThat(outWriter.toString()).contains("3d");
        assertThat(outWriter.toString()).contains("0m");
    }

    @Test
    @DisplayName("Export subcommand exports valid JSON to stdout")
    void testExportSubcommand() {
        ReminderCreateRequest req = new ReminderCreateRequest(
                "EVT-001", "Event To Export", ReminderType.EVENT, "GENERAL",
                LocalDate.of(2026, 12, 1), LocalTime.of(10, 0), ZoneId.of("Asia/Kolkata"),
                ReminderRecurrence.NONE, List.of("0m"), Collections.emptyMap(), Collections.emptyMap()
        );
        storageManager.createReminder(req);

        int exitCode = commandLine.execute("export");
        assertThat(exitCode).isEqualTo(0);
        assertThat(outWriter.toString()).contains("\"events\"");
        assertThat(outWriter.toString()).contains("Event To Export");
    }

    @Test
    @DisplayName("Test subcommand invokes test dispatch on ReminderDispatchService")
    void testTestSubcommand() {
        when(dispatchService.testDispatch(anyString())).thenReturn(true);

        int exitCode = commandLine.execute("test", "EVT-001");
        assertThat(exitCode).isEqualTo(0);
        assertThat(outWriter.toString()).contains("Sent test notification to Telegram successfully");
    }

    @Test
    @DisplayName("Add, edit, delete enforce root privileges in user space")
    void testRootPrivilegeEnforcement() {
        String origUser = System.getProperty("user.name");
        try {
            System.setProperty("user.name", "regular_user");

            int addExit = commandLine.execute("add", "--title", "Test", "--date", "2026-12-01");
            assertThat(addExit).isEqualTo(1);
            assertThat(errWriter.toString()).contains("requires root privileges");

            errWriter.getBuffer().setLength(0);
            int editExit = commandLine.execute("edit", "EVT-001", "--title", "New Title");
            assertThat(editExit).isEqualTo(1);
            assertThat(errWriter.toString()).contains("requires root privileges");

            errWriter.getBuffer().setLength(0);
            int delExit = commandLine.execute("delete", "EVT-001");
            assertThat(delExit).isEqualTo(1);
            assertThat(errWriter.toString()).contains("requires root privileges");
        } finally {
            if (origUser != null) {
                System.setProperty("user.name", origUser);
            }
        }
    }

    @Test
    @DisplayName("Add, edit, delete succeed when running as root")
    void testMutatingCommandsAsRoot() {
        String origUser = System.getProperty("user.name");
        try {
            System.setProperty("user.name", "root");

            int addExit = commandLine.execute("add", "--title", "Root Created", "--date", "2026-12-01", "--type", "EVENT");
            assertThat(addExit).isEqualTo(0);
            assertThat(outWriter.toString()).contains("Created reminder 'Root Created' successfully");

            outWriter.getBuffer().setLength(0);
            int editExit = commandLine.execute("edit", "EVT-001", "--title", "Updated Title");
            assertThat(editExit).isEqualTo(0);
            assertThat(outWriter.toString()).contains("Successfully updated reminder");

            outWriter.getBuffer().setLength(0);
            int delExit = commandLine.execute("delete", "EVT-001");
            assertThat(delExit).isEqualTo(0);
            assertThat(outWriter.toString()).contains("Deleted reminder");
        } finally {
            if (origUser != null) {
                System.setProperty("user.name", origUser);
            }
        }
    }

    @Test
    @DisplayName("Template subcommand outputs full copy-paste command guide")
    void testTemplateSubcommand() {
        int exitCode = commandLine.execute("template");
        assertThat(exitCode).isEqualTo(0);
        assertThat(outWriter.toString()).contains("Complete Copy-Paste Command Template");
        assertThat(outWriter.toString()).contains("PARAMETER REFERENCE");
        assertThat(outWriter.toString()).contains("POPULAR COPY-PASTE RECIPES");
    }

    @Test
    @DisplayName("Next subcommand displays all reminders when multiple share the same earliest trigger time")
    void testNextSubcommandMultipleSimultaneousEvents() {
        LocalDate date = LocalDate.of(2026, 12, 1);
        LocalTime time = LocalTime.of(10, 0);

        ReminderCreateRequest req1 = new ReminderCreateRequest(
                "EVT-001", "Team Sync A", ReminderType.MEETING, "WORK",
                date, time, ZoneId.of("Asia/Kolkata"),
                ReminderRecurrence.NONE, List.of("1h", "0m"), Collections.emptyMap(), Collections.emptyMap()
        );
        ReminderCreateRequest req2 = new ReminderCreateRequest(
                "EVT-002", "Team Sync B", ReminderType.MEETING, "WORK",
                date, time, ZoneId.of("Asia/Kolkata"),
                ReminderRecurrence.NONE, List.of("1h", "0m"), Collections.emptyMap(), Collections.emptyMap()
        );
        storageManager.createReminder(req1);
        storageManager.createReminder(req2);

        int exitCode = commandLine.execute("next");
        assertThat(exitCode).isEqualTo(0);
        assertThat(outWriter.toString()).contains("2 events scheduled at the same time");
        assertThat(outWriter.toString()).contains("Team Sync A");
        assertThat(outWriter.toString()).contains("Team Sync B");
    }

    @Test
    @DisplayName("List subcommand supports --all, --system, --user scope flags")
    void testListMultiScope() {
        ReminderCreateRequest req = new ReminderCreateRequest(
                "EVT-001", "System Event", ReminderType.EVENT, "GENERAL",
                LocalDate.of(2026, 12, 1), LocalTime.of(10, 0), ZoneId.of("Asia/Kolkata"),
                ReminderRecurrence.NONE, List.of("0m"), Collections.emptyMap(), Collections.emptyMap()
        );
        storageManager.createReminder(req);

        // --all shows SCOPE column
        int exitAll = commandLine.execute("list", "--all");
        assertThat(exitAll).isEqualTo(0);
        assertThat(outWriter.toString()).contains("SCOPE");
        assertThat(outWriter.toString()).contains("all scopes");

        outWriter.getBuffer().setLength(0);
        int exitSys = commandLine.execute("list", "--system");
        assertThat(exitSys).isEqualTo(0);
        assertThat(outWriter.toString()).contains("system scope");
    }
}
