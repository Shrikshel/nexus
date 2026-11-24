package com.nexus.adapters.cli.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nexus.adapters.cli.NexusCliRootCommand;
import com.nexus.capabilities.reminder.dispatch.ReminderDispatchService;
import com.nexus.capabilities.reminder.dto.*;
import com.nexus.capabilities.reminder.scheduler.ReminderSchedulerProvider;
import com.nexus.capabilities.reminder.storage.ReminderStorageManager;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * Enterprise CLI command group for the Nexus Event & Reminder Engine.
 */
@Component
@Command(
    name = "reminder",
    aliases = {"rem", "reminders"},
    description = "Manage multi-stage birthday, bill, and event reminders with zero-overhead systemd timers",
    subcommands = {
        ReminderCommand.ListSubcommand.class,
        ReminderCommand.NextSubcommand.class,
        ReminderCommand.ShowSubcommand.class,
        ReminderCommand.AddSubcommand.class,
        ReminderCommand.EditSubcommand.class,
        ReminderCommand.DeleteSubcommand.class,
        ReminderCommand.TestSubcommand.class,
        ReminderCommand.TemplateSubcommand.class,
        ReminderCommand.ExportSubcommand.class,
        ReminderCommand.ImportSubcommand.class,
        ReminderCommand.SyncSubcommand.class,
        ReminderCommand.DispatchSubcommand.class
    },
    mixinStandardHelpOptions = true
)
public class ReminderCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @ParentCommand
    private NexusCliRootCommand root;

    private final ReminderStorageManager storageManager;
    private final ReminderSchedulerProvider schedulerProvider;
    private final ReminderDispatchService dispatchService;

    public ReminderCommand(
            ReminderStorageManager storageManager,
            ReminderSchedulerProvider schedulerProvider,
            ReminderDispatchService dispatchService) {
        this.storageManager = storageManager;
        this.schedulerProvider = schedulerProvider;
        this.dispatchService = dispatchService;
    }

    public ReminderStorageManager getStorageManager() {
        return storageManager;
    }

    public ReminderSchedulerProvider getSchedulerProvider() {
        return schedulerProvider;
    }

    public ReminderDispatchService getDispatchService() {
        return dispatchService;
    }

    public NexusCliRootCommand getRoot() {
        return root;
    }

    public static boolean isRoot() {
        return "root".equalsIgnoreCase(System.getProperty("user.name"));
    }

    @Override
    public Integer call() {
        if (spec != null) {
            spec.commandLine().usage(spec.commandLine().getOut());
        }
        return 0;
    }

    // 1. LIST
    @Component
    @Command(name = "list", aliases = {"ls"}, description = "List all active reminders and upcoming notification stages", mixinStandardHelpOptions = true)
    public static class ListSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;
        @ParentCommand
        private ReminderCommand parent;
        @Option(names = {"--type"}, description = "Filter by type (BIRTHDAY, BILL, ANNIVERSARY, MEETING, EVENT)")
        private String typeFilter;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            boolean json = isJsonRequested(spec, parent);
            List<ReminderEvent> all = parent.getStorageManager().loadAll();

            if (typeFilter != null && !typeFilter.isBlank()) {
                ReminderType t = ReminderType.fromString(typeFilter);
                all = all.stream().filter(e -> e.type() == t).toList();
            }

            if (json) {
                out.println(toJson(all));
                return 0;
            }

            out.println("\u001B[1;36m=== Nexus Scheduled Reminders & Events ===\u001B[0m");
            if (all.isEmpty()) {
                out.println("  ℹ No reminders currently configured.");
                out.println("  👉 Add your first reminder with: sudo nexus reminder add --title \"Mom's Birthday\" --date 2026-11-25 --type BIRTHDAY");
                return 0;
            }

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            out.printf("  %-10s  %-24s  %-10s  %-12s  %-20s  %-6s  %-10s%n",
                    "ID", "TITLE", "TYPE", "EVENT DATE", "NEXT ALERT", "STAGE", "STATUS");
            out.println("  --------------------------------------------------------------------------------------------------");

            Instant now = Instant.now();
            for (ReminderEvent e : all) {
                String nextAlertStr = "-";
                String nextStageStr = "-";
                if (e.state().nextTriggerAt() != null) {
                    ZonedDateTime zdt = e.state().nextTriggerAt().atZone(e.timezone());
                    nextAlertStr = zdt.format(dtf);
                    nextStageStr = e.state().nextStage() != null ? e.state().nextStage() : "-";
                }

                String dateRel = formatRelativeDays(e.targetDate(), e.timezone());
                String statusColor = "ACTIVE".equalsIgnoreCase(e.state().status()) ? "\u001B[32mACTIVE\u001B[0m" : "\u001B[33m" + e.state().status() + "\u001B[0m";

                out.printf("  \u001B[1;33m%-10s\u001B[0m  %-24s  \u001B[36m%-10s\u001B[0m  %-12s  %-20s  \u001B[1;35m%-6s\u001B[0m  %s%n",
                        truncate(e.id(), 10),
                        truncate(e.title(), 24),
                        truncate(e.type().name(), 10),
                        e.targetDate().toString() + " (" + dateRel + ")",
                        nextAlertStr,
                        nextStageStr,
                        statusColor);
            }
            out.println();
            return 0;
        }
    }

    // 2. NEXT
    @Component
    @Command(name = "next", description = "Display the immediate next upcoming reminder and pending alert stage", mixinStandardHelpOptions = true)
    public static class NextSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;
        @ParentCommand
        private ReminderCommand parent;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            boolean json = isJsonRequested(spec, parent);
            List<ReminderEvent> all = parent.getStorageManager().loadAll();

            ReminderEvent nextEvent = null;
            Instant earliest = null;

            for (ReminderEvent e : all) {
                if ("ACTIVE".equalsIgnoreCase(e.state().status()) && e.state().nextTriggerAt() != null) {
                    if (earliest == null || e.state().nextTriggerAt().isBefore(earliest)) {
                        earliest = e.state().nextTriggerAt();
                        nextEvent = e;
                    }
                }
            }

            if (json) {
                out.println(nextEvent != null ? toJson(nextEvent) : "{}");
                return 0;
            }

            out.println("\u001B[1;36m=== Next Upcoming Reminder ===\u001B[0m");
            if (nextEvent == null) {
                out.println("  ℹ No active pending reminder alerts found.");
                return 0;
            }

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy HH:mm:ss (z)");
            ZonedDateTime eventZdt = ZonedDateTime.of(nextEvent.targetDate(), nextEvent.targetTime(), nextEvent.timezone());
            ZonedDateTime alertZdt = nextEvent.state().nextTriggerAt().atZone(nextEvent.timezone());

            out.println("  • ID          : \u001B[1;33m" + nextEvent.id() + "\u001B[0m");
            out.println("  • Title       : \u001B[1;37m" + nextEvent.title() + "\u001B[0m");
            out.println("  • Type        : \u001B[36m" + nextEvent.type() + "\u001B[0m (" + nextEvent.category() + ")");
            out.println("  • Event Date  : " + eventZdt.format(dtf) + " [" + formatRelativeDays(nextEvent.targetDate(), nextEvent.timezone()) + "]");
            out.println("  • Next Alert  : \u001B[1;32m" + alertZdt.format(dtf) + "\u001B[0m");
            out.println("  • Stage       : \u001B[1;35m" + nextEvent.state().nextStage() + "\u001B[0m");
            out.println("  • Recurrence  : " + nextEvent.recurrence());
            return 0;
        }
    }

    // 3. SHOW
    @Component
    @Command(name = "show", description = "Inspect detailed reminder configuration, stage progression, and history", mixinStandardHelpOptions = true)
    public static class ShowSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;
        @ParentCommand
        private ReminderCommand parent;
        @Parameters(index = "0", description = "Reminder ID (EVT-001) or UUID")
        private String identifier;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            PrintWriter err = spec.commandLine().getErr();
            boolean json = isJsonRequested(spec, parent);

            Optional<ReminderEvent> eventOpt = parent.getStorageManager().findByIdOrUuid(identifier);
            if (eventOpt.isEmpty()) {
                err.println("\u001B[31mError: Reminder '" + identifier + "' not found.\u001B[0m");
                return 1;
            }

            ReminderEvent e = eventOpt.get();
            if (json) {
                out.println(toJson(e));
                return 0;
            }

            out.println("\u001B[1;36m=== Reminder Details: " + e.title() + " ===\u001B[0m");
            out.println("  • Friendly ID  : \u001B[1;33m" + e.id() + "\u001B[0m");
            out.println("  • System UUID  : \u001B[90m" + e.uuid() + "\u001B[0m");
            out.println("  • Type         : \u001B[36m" + e.type() + "\u001B[0m (Category: " + e.category() + ")");
            out.println("  • Target Date  : " + e.targetDate() + " at " + e.targetTime() + " (" + e.timezone() + ")");
            out.println("  • Recurrence   : " + e.recurrence());
            out.println("  • Status       : " + e.state().status() + " (Cycle: " + e.state().activeCycle() + ")");
            if (!e.metadata().isEmpty()) {
                out.println("  • Metadata     : " + e.metadata());
            }

            out.println("\n  \u001B[1;33m--- Configured Notification Stages ---\u001B[0m");
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            out.printf("  %-6s  %-20s  %-12s  %s%n", "STAGE", "TRIGGER TIME", "STATUS", "CUSTOM MESSAGE");
            out.println("  --------------------------------------------------------------------------------");
            for (ReminderStage s : e.stages()) {
                String timeStr = s.triggerAt() != null ? s.triggerAt().atZone(e.timezone()).format(dtf) : "-";
                String statusStr = s.status() == ReminderStageStatus.DELIVERED ? "\u001B[32mDELIVERED\u001B[0m" : "\u001B[33mPENDING\u001B[0m";
                String customStr = s.customMessage() != null ? "\"" + s.customMessage() + "\"" : "-";
                out.printf("  \u001B[1;35m%-6s\u001B[0m  %-20s  %-12s  %s%n", s.stage(), timeStr, statusStr, customStr);
            }

            if (!e.history().isEmpty()) {
                out.println("\n  \u001B[1;33m--- Completed Cycle History ---\u001B[0m");
                for (ReminderHistoryEntry h : e.history()) {
                    out.println("  • Cycle " + h.cycle() + " (" + h.targetDate() + "): Completed at " + h.completedAt() + " with stages " + h.stagesDelivered());
                }
            }
            return 0;
        }
    }

    // 4. ADD
    @Component
    @Command(name = "add", description = "Create and schedule a new multi-stage reminder event (requires root/sudo)", mixinStandardHelpOptions = true)
    public static class AddSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;
        @ParentCommand
        private ReminderCommand parent;

        @Option(names = {"--example", "-e"}, help = true, description = "Print a copy-pasteable command template with all parameters and exit")
        private boolean showExample;

        @Option(names = {"--title", "-t"}, required = true, description = "Event title (e.g. \"Mom's Birthday\")")
        private String title;

        @Option(names = {"--date", "-d"}, required = true, description = "Target event date (YYYY-MM-DD)")
        private String dateStr;

        @Option(names = {"--time"}, description = "Target event time (HH:mm:ss, default: 09:00:00)", defaultValue = "09:00:00")
        private String timeStr;

        @Option(names = {"--type"}, description = "Reminder type: BIRTHDAY, BILL, ANNIVERSARY, MEETING, EVENT", defaultValue = "EVENT")
        private String typeStr;

        @Option(names = {"--category"}, description = "Category classification (e.g. FAMILY, FINANCE)", defaultValue = "GENERAL")
        private String category;

        @Option(names = {"--recurrence", "-r"}, description = "Recurrence policy: NONE, DAILY, WEEKLY, MONTHLY, YEARLY, or intervals (e.g. 'every 5m', '16h', '2d', '3mo', '1y')", defaultValue = "NONE")
        private String recurrenceStr;

        @Option(names = {"--stages"}, description = "Comma-separated alert stages (e.g. 7d,3d,1d,1h,0m)")
        private String stagesStr;

        @Option(names = {"--birth-year"}, description = "Year of birth for milestone age calculation (e.g. 1966)")
        private Integer birthYear;

        @Option(names = {"--notes"}, description = "Optional notes or gift ideas")
        private String notes;

        @Option(names = {"--id"}, description = "Custom friendly ID (e.g. EVT-MOM-BDAY)")
        private String customId;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            PrintWriter err = spec.commandLine().getErr();

            if (showExample) {
                printTemplate(out);
                return 0;
            }

            if (!isRoot()) {
                err.println("\u001B[31m✗ Error: Creating reminders requires root privileges so systemd timers run 24/7 as root.\u001B[0m");
                err.println("  👉 Please run with sudo:");
                err.println("     sudo nexus reminder add --title \"" + title + "\" --date " + dateStr);
                err.println("  💡 Tip: Run 'nx reminder template' or 'nx reminder add -e' to view full parameter options.");
                return 1;
            }

            LocalDate targetDate;
            try {
                targetDate = LocalDate.parse(dateStr.trim());
            } catch (Exception e) {
                err.println("\u001B[31mInvalid date format: " + dateStr + " (must be YYYY-MM-DD)\u001B[0m");
                err.println("  💡 Tip: Run 'nx reminder template' or 'nx reminder add -e' to view full parameter options.");
                return 1;
            }

            LocalTime targetTime;
            try {
                targetTime = LocalTime.parse(timeStr.trim());
            } catch (Exception e) {
                err.println("\u001B[31mInvalid time format: " + timeStr + " (must be HH:mm:ss)\u001B[0m");
                err.println("  💡 Tip: Run 'nx reminder template' or 'nx reminder add -e' to view full parameter options.");
                return 1;
            }

            ReminderType type = ReminderType.fromString(typeStr);
            ReminderRecurrence recurrence = ReminderRecurrence.fromString(recurrenceStr);
            if (type == ReminderType.BIRTHDAY || type == ReminderType.ANNIVERSARY) {
                if (recurrence == ReminderRecurrence.NONE && recurrenceStr.equalsIgnoreCase("NONE")) {
                    recurrence = ReminderRecurrence.YEARLY; // Default birthdays to yearly recurrence
                }
            }

            List<String> stageTokens = null;
            if (stagesStr != null && !stagesStr.isBlank()) {
                stageTokens = Arrays.stream(stagesStr.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .toList();
            }

            Map<String, Object> meta = new LinkedHashMap<>();
            if (birthYear != null) meta.put("birthYear", birthYear);
            if (notes != null) meta.put("notes", notes);

            ReminderCreateRequest req = new ReminderCreateRequest(
                    customId, title, type, category, targetDate, targetTime,
                    ZoneId.systemDefault(), recurrence, stageTokens, Collections.emptyMap(), meta
            );

            ReminderEvent created = parent.getStorageManager().createReminder(req);
            parent.getSchedulerProvider().armTimer(created);

            out.println("\u001B[32m✓ Created reminder '" + created.title() + "' successfully!\u001B[0m");
            out.println("  • Friendly ID : \u001B[1;33m" + created.id() + "\u001B[0m");
            out.println("  • System UUID : \u001B[90m" + created.uuid() + "\u001B[0m");
            out.println("  • Recurrence  : " + created.recurrence());
            out.println("  • Next Alert  : " + (created.state().nextTriggerAt() != null ? created.state().nextTriggerAt().atZone(created.timezone()) : "-"));
            out.println("  • Armed Timer : " + parent.getSchedulerProvider().resolveUnitBaseName(created.id()) + ".timer (Systemd)");
            return 0;
        }
    }

    // 5. EDIT
    @Component
    @Command(name = "edit", description = "Modify an existing reminder event and recalculate timers (requires root/sudo)", mixinStandardHelpOptions = true)
    public static class EditSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;
        @ParentCommand
        private ReminderCommand parent;
        @Parameters(index = "0", description = "Reminder ID (EVT-001) or UUID")
        private String identifier;

        @Option(names = {"--title", "-t"}, description = "Updated title")
        private String newTitle;

        @Option(names = {"--date", "-d"}, description = "Updated date (YYYY-MM-DD)")
        private String newDateStr;

        @Option(names = {"--time"}, description = "Updated time (HH:mm:ss)")
        private String newTimeStr;

        @Option(names = {"--type"}, description = "Updated type")
        private String newTypeStr;

        @Option(names = {"--category"}, description = "Updated category")
        private String newCategory;

        @Option(names = {"--recurrence", "-r"}, description = "Updated recurrence policy: NONE, DAILY, WEEKLY, MONTHLY, YEARLY, or intervals (e.g. 'every 5m', '16h', '2d', '3mo', '1y')")
        private String newRecurrenceStr;

        @Option(names = {"--stages"}, description = "Updated stages (comma-separated)")
        private String newStagesStr;

        @Option(names = {"--stage-msg"}, description = "Custom stage message in format: <stage>=<message>")
        private String stageMsg;

        @Option(names = {"--notes"}, description = "Updated notes")
        private String newNotes;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            PrintWriter err = spec.commandLine().getErr();

            if (!isRoot()) {
                err.println("\u001B[31m✗ Error: Editing reminders requires root privileges.\u001B[0m");
                err.println("  👉 Please run with sudo: sudo nexus reminder edit " + identifier);
                return 1;
            }

            LocalDate newDate = newDateStr != null ? LocalDate.parse(newDateStr.trim()) : null;
            LocalTime newTime = newTimeStr != null ? LocalTime.parse(newTimeStr.trim()) : null;
            ReminderType newType = newTypeStr != null ? ReminderType.fromString(newTypeStr) : null;
            ReminderRecurrence newRecurrence = newRecurrenceStr != null ? ReminderRecurrence.fromString(newRecurrenceStr) : null;

            List<String> newStages = null;
            if (newStagesStr != null && !newStagesStr.isBlank()) {
                newStages = Arrays.stream(newStagesStr.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .toList();
            }

            Map<String, String> stageMessages = null;
            if (stageMsg != null && stageMsg.contains("=")) {
                stageMessages = new HashMap<>();
                int eq = stageMsg.indexOf('=');
                stageMessages.put(stageMsg.substring(0, eq).trim().toLowerCase(Locale.ROOT), stageMsg.substring(eq + 1).trim());
            }

            Map<String, Object> meta = null;
            if (newNotes != null) {
                meta = new HashMap<>();
                meta.put("notes", newNotes);
            }

            ReminderUpdateRequest req = new ReminderUpdateRequest(
                    identifier, newTitle, newType, newCategory, newDate, newTime,
                    null, newRecurrence, newStages, stageMessages, meta
            );

            Optional<ReminderEvent> updatedOpt = parent.getStorageManager().updateReminder(req);
            if (updatedOpt.isEmpty()) {
                err.println("\u001B[31mError: Reminder '" + identifier + "' not found.\u001B[0m");
                return 1;
            }

            ReminderEvent updated = updatedOpt.get();
            parent.getSchedulerProvider().armTimer(updated);

            out.println("\u001B[32m✓ Successfully updated reminder '" + updated.title() + "' (" + updated.id() + ")!\u001B[0m");
            out.println("  • Target Date : " + updated.targetDate() + " at " + updated.targetTime());
            out.println("  • Next Alert  : " + (updated.state().nextTriggerAt() != null ? updated.state().nextTriggerAt().atZone(updated.timezone()) : "-"));
            return 0;
        }
    }

    // 6. DELETE
    @Component
    @Command(name = "delete", aliases = {"rm"}, description = "Delete a reminder and remove its systemd timer (requires root/sudo)", mixinStandardHelpOptions = true)
    public static class DeleteSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;
        @ParentCommand
        private ReminderCommand parent;
        @Parameters(index = "0", description = "Reminder ID (EVT-001) or UUID")
        private String identifier;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            PrintWriter err = spec.commandLine().getErr();

            if (!isRoot()) {
                err.println("\u001B[31m✗ Error: Deleting reminders requires root privileges.\u001B[0m");
                err.println("  👉 Please run with sudo: sudo nexus reminder delete " + identifier);
                return 1;
            }

            Optional<ReminderEvent> match = parent.getStorageManager().findByIdOrUuid(identifier);
            if (match.isEmpty()) {
                err.println("\u001B[31mError: Reminder '" + identifier + "' not found.\u001B[0m");
                return 1;
            }

            ReminderEvent event = match.get();
            parent.getSchedulerProvider().disarmTimer(event.id());
            parent.getStorageManager().deleteByIdOrUuid(event.uuid());

            out.println("\u001B[32m✓ Deleted reminder '" + event.title() + "' (" + event.id() + ") and disarmed systemd timers.\u001B[0m");
            return 0;
        }
    }

    // 7. TEST
    @Component
    @Command(name = "test", description = "Dispatch a mock notification to Telegram for verification without changing state", mixinStandardHelpOptions = true)
    public static class TestSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;
        @ParentCommand
        private ReminderCommand parent;
        @Parameters(index = "0", description = "Reminder ID (EVT-001) or UUID")
        private String identifier;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            PrintWriter err = spec.commandLine().getErr();

            boolean ok = parent.getDispatchService().testDispatch(identifier);
            if (ok) {
                out.println("\u001B[32m✓ Sent test notification to Telegram successfully!\u001B[0m");
                return 0;
            } else {
                err.println("\u001B[31mFailed to send test notification. Verify Telegram bot credentials in config.yml.\u001B[0m");
                return 1;
            }
        }
    }

    // 7b. TEMPLATE
    @Component
    @Command(
        name = "template",
        aliases = {"example", "samples"},
        description = "Display copy-pasteable command templates with all available parameters",
        mixinStandardHelpOptions = true
    )
    public static class TemplateSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            printTemplate(out);
            return 0;
        }
    }

    // 8. EXPORT
    @Component
    @Command(name = "export", description = "Export all reminders to a JSON backup file or stdout", mixinStandardHelpOptions = true)
    public static class ExportSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;
        @ParentCommand
        private ReminderCommand parent;

        @Option(names = {"-o", "--output"}, description = "Target file path (defaults to stdout)")
        private File outputFile;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            PrintWriter err = spec.commandLine().getErr();

            List<ReminderEvent> all = parent.getStorageManager().loadAll();
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("version", 1);
            root.put("exportedAt", Instant.now().toString());
            root.put("totalEvents", all.size());
            root.put("events", all);

            try {
                ObjectMapper mapper = new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .enable(SerializationFeature.INDENT_OUTPUT);

                String json = mapper.writeValueAsString(root);
                if (outputFile != null) {
                    Files.writeString(outputFile.toPath(), json);
                    out.println("\u001B[32m✓ Exported " + all.size() + " reminders to: " + outputFile.getAbsolutePath() + "\u001B[0m");
                } else {
                    out.println(json);
                }
                return 0;
            } catch (Exception e) {
                err.println("\u001B[31mExport failed: " + e.getMessage() + "\u001B[0m");
                return 1;
            }
        }
    }

    // 9. IMPORT
    @Component
    @Command(name = "import", description = "Import reminders from a JSON backup file and reconcile systemd timers (requires root/sudo)", mixinStandardHelpOptions = true)
    public static class ImportSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;
        @ParentCommand
        private ReminderCommand parent;
        @Parameters(index = "0", description = "Path to JSON backup file")
        private File inputFile;

        @Option(names = {"--mode"}, description = "Import mode: merge (default, preserves local) or replace", defaultValue = "merge")
        private String mode;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            PrintWriter err = spec.commandLine().getErr();

            if (!isRoot()) {
                err.println("\u001B[31m✗ Error: Importing reminders requires root privileges.\u001B[0m");
                err.println("  👉 Please run with sudo: sudo nexus reminder import " + inputFile.getPath());
                return 1;
            }

            if (!inputFile.exists() || !inputFile.isFile()) {
                err.println("\u001B[31mError: File not found: " + inputFile.getAbsolutePath() + "\u001B[0m");
                return 1;
            }

            try {
                ObjectMapper mapper = new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

                com.fasterxml.jackson.databind.JsonNode rootNode = mapper.readTree(inputFile);
                com.fasterxml.jackson.databind.JsonNode eventsNode = rootNode.path("events");
                if (!eventsNode.isArray()) {
                    err.println("\u001B[31mError: Invalid JSON format - missing 'events' array.\u001B[0m");
                    return 1;
                }

                List<ReminderEvent> incoming = new ArrayList<>();
                for (com.fasterxml.jackson.databind.JsonNode item : eventsNode) {
                    ReminderEvent e = mapper.treeToValue(item, ReminderEvent.class);
                    if (e != null) incoming.add(e);
                }

                boolean replace = "replace".equalsIgnoreCase(mode);
                ReminderImportResult res = parent.getStorageManager().importEvents(incoming, replace);

                // Reconcile timers
                for (ReminderEvent e : parent.getStorageManager().loadAll()) {
                    if ("ACTIVE".equalsIgnoreCase(e.state().status())) {
                        parent.getSchedulerProvider().armTimer(e);
                    }
                }

                out.println("\u001B[32m✓ " + res.message() + "\u001B[0m");
                out.println("  • Total active events in storage: " + res.totalEvents());
                return 0;
            } catch (Exception e) {
                err.println("\u001B[31mImport failed: " + e.getMessage() + "\u001B[0m");
                return 1;
            }
        }
    }

    // 10. SYNC
    @Component
    @Command(name = "sync", description = "Rebuild and arm all systemd timers directly from reminders.json (requires root/sudo)", mixinStandardHelpOptions = true)
    public static class SyncSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;
        @ParentCommand
        private ReminderCommand parent;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            PrintWriter err = spec.commandLine().getErr();

            if (!isRoot()) {
                err.println("\u001B[31m✗ Error: Syncing systemd timers requires root privileges.\u001B[0m");
                err.println("  👉 Please run with sudo: sudo nexus reminder sync");
                return 1;
            }

            List<ReminderEvent> all = parent.getStorageManager().loadAll();
            int armed = 0;
            Instant now = Instant.now();
            for (ReminderEvent e : all) {
                if ("ACTIVE".equalsIgnoreCase(e.state().status())) {
                    ReminderEvent reconciled = parent.getStorageManager().reconcileEvent(e, now);
                    parent.getSchedulerProvider().armTimer(reconciled);
                    armed++;
                } else {
                    parent.getSchedulerProvider().disarmTimer(e.id());
                }
            }

            out.println("\u001B[32m✓ Reconciled all timers: " + armed + " active timers armed in systemd.\u001B[0m");
            return 0;
        }
    }

    // 11. DISPATCH (Internal systemd entrypoint)
    @Component
    @Command(name = "dispatch", hidden = true, description = "Internal dispatcher triggered by systemd timer service")
    public static class DispatchSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;
        @ParentCommand
        private ReminderCommand parent;
        @Parameters(index = "0", description = "Reminder ID or UUID")
        private String identifier;

        @Override
        public Integer call() {
            boolean ok = parent.getDispatchService().dispatch(identifier);
            return ok ? 0 : 1;
        }
    }

    // Helper functions
    private static boolean isJsonRequested(CommandSpec spec, ReminderCommand parent) {
        if (parent != null && parent.getRoot() != null) {
            return parent.getRoot().isJson();
        }
        return false;
    }

    private static String toJson(Object obj) {
        try {
            return new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .enable(SerializationFeature.INDENT_OUTPUT)
                    .writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen - 1) + "…";
    }

    public static void printTemplate(PrintWriter out) {
        out.println("\u001B[1;36m================================================================================\u001B[0m");
        out.println("\u001B[1;33m📋 Nexus Reminder - Complete Copy-Paste Command Template\u001B[0m");
        out.println("\u001B[1;36m================================================================================\u001B[0m\n");

        out.println("\u001B[1;32m# 1. FULL COMMAND (All Parameters Specified):\u001B[0m");
        out.println("sudo nexus reminder add \\");
        out.println("  --title \"Quarterly Infrastructure Audit\" \\");
        out.println("  --date 2026-12-01 \\");
        out.println("  --time 10:00:00 \\");
        out.println("  --type EVENT \\");
        out.println("  --category INFRA \\");
        out.println("  --recurrence \"every 3mo\" \\");
        out.println("  --stages 7d,3d,1d,2h,0m \\");
        out.println("  --notes \"Review NVMe SMART metrics, ZFS scrubs & SSL cert renewals\" \\");
        out.println("  --id EVT-INFRA-REV\n");

        out.println("\u001B[1;33mPARAMETER REFERENCE:\u001B[0m");
        out.println("  \u001B[1;37m--title, -t\u001B[0m       \u001B[31m[Required]\u001B[0m Event title (e.g. \"Mom's Birthday\", \"AWS Hosting Bill\")");
        out.println("  \u001B[1;37m--date, -d\u001B[0m        \u001B[31m[Required]\u001B[0m Target event date in YYYY-MM-DD (e.g. 2026-12-01)");
        out.println("  \u001B[1;37m--time\u001B[0m            \u001B[36m[Optional]\u001B[0m Target time in HH:mm:ss (default: 09:00:00)");
        out.println("  \u001B[1;37m--type\u001B[0m            \u001B[36m[Optional]\u001B[0m BIRTHDAY, BILL, ANNIVERSARY, MEETING, EVENT, CUSTOM (default: EVENT)");
        out.println("  \u001B[1;37m--category\u001B[0m        \u001B[36m[Optional]\u001B[0m Category tag (e.g. FAMILY, FINANCE, WORK, INFRA, GENERAL)");
        out.println("  \u001B[1;37m--recurrence, -r\u001B[0m  \u001B[36m[Optional]\u001B[0m Recurrence schedule (default: NONE)");
        out.println("                               • Presets:   NONE, DAILY, WEEKLY, MONTHLY, YEARLY / ANNUAL");
        out.println("                               • Intervals: 'every 45m', 'every 16h', 'every 2d', 'every 3mo', 'every 1y'");
        out.println("                               • Units:     m (mins), h (hours), d (days), w (weeks), mo (months), y (years)");
        out.println("  \u001B[1;37m--stages\u001B[0m          \u001B[36m[Optional]\u001B[0m Alert milestones before event (e.g. 14d,7d,3d,1d,2h,30m,0m)");
        out.println("                               (0m = at target event time; auto-defaults by event type if omitted)");
        out.println("  \u001B[1;37m--birth-year\u001B[0m      \u001B[36m[Optional]\u001B[0m Birth year (e.g. 1966) to auto-calculate milestone age in messages");
        out.println("  \u001B[1;37m--notes\u001B[0m           \u001B[36m[Optional]\u001B[0m Freeform text, gift ideas, payment links, or instructions");
        out.println("  \u001B[1;37m--id\u001B[0m              \u001B[36m[Optional]\u001B[0m Custom human-friendly ID (e.g. EVT-MOM-BDAY, default: auto-generated)\n");

        out.println("\u001B[1;33mPOPULAR COPY-PASTE RECIPES:\u001B[0m");
        out.println("  \u001B[1;32m# Birthday (Annual calendar recurrence + age calculation):\u001B[0m");
        out.println("  sudo nx rem add --title \"Mom's Birthday\" --date 2026-11-25 --type BIRTHDAY --birth-year 1966\n");

        out.println("  \u001B[1;32m# Monthly Bill (Recurs on the 5th of every month, calendar-anchored):\u001B[0m");
        out.println("  sudo nx rem add --title \"AWS Hosting\" --date 2026-12-05 --time 09:00:00 --type BILL --recurrence \"1mo\" --stages 3d,1d,0m\n");

        out.println("  \u001B[1;32m# Micro-Routine (Recurs every 45 minutes):\u001B[0m");
        out.println("  sudo nx rem add --title \"Hydration Break\" --date 2026-11-24 --time 10:00:00 --recurrence \"every 45m\" --stages 0m\n");

        out.println("  \u001B[1;32m# Shift Handover (Recurs every 16 hours):\u001B[0m");
        out.println("  sudo nx rem add --title \"Shift Handover\" --date 2026-11-24 --time 18:00:00 --recurrence \"16h\" --stages 30m,0m\n");

        out.println("  \u001B[1;32m# Multi-Day Maintenance (Recurs every 2 days):\u001B[0m");
        out.println("  sudo nx rem add --title \"ZFS Scrub Check\" --date 2026-11-25 --time 03:00:00 --recurrence \"every 2d\"\n");

        out.println("\u001B[1;36m================================================================================\u001B[0m");
    }

    private static String formatRelativeDays(LocalDate targetDate, ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        long days = ChronoUnit.DAYS.between(today, targetDate);
        if (days == 0) return "today";
        if (days == 1) return "tomorrow";
        if (days == -1) return "yesterday";
        if (days > 1) return "in " + days + "d";
        return Math.abs(days) + "d ago";
    }
}
