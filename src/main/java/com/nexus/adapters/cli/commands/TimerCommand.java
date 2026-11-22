package com.nexus.adapters.cli.commands;

import com.nexus.adapters.cli.CliExecutionBridge;
import com.nexus.adapters.cli.NexusCliRootCommand;
import com.nexus.capabilities.scheduler.timer.*;
import com.nexus.capabilities.scheduler.timer.dto.*;
import com.nexus.platform.scheduler.ScheduledTaskManager;
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
import java.util.List;
import java.util.concurrent.Callable;

/**
 * CLI command group for creating, inspecting, editing, running, and deleting
 * recurring scheduled timers (systemd timers, launchd agents, and Task Scheduler).
 */
@Component
@Command(
    name = "timer",
    aliases = {"timers", "cron", "schedule"},
    description = "Manage recurring timers and automation scripts (systemd, launchd, Task Scheduler)",
    subcommands = {
        TimerCommand.ListSubcommand.class,
        TimerCommand.CreateSubcommand.class,
        TimerCommand.StatusSubcommand.class,
        TimerCommand.EditSubcommand.class,
        TimerCommand.RunSubcommand.class,
        TimerCommand.DeleteSubcommand.class
    },
    mixinStandardHelpOptions = true
)
public class TimerCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    private final CliExecutionBridge bridge;
    private final ScheduledTaskManager taskManager;

    public TimerCommand(CliExecutionBridge bridge, ScheduledTaskManager taskManager) {
        this.bridge = bridge;
        this.taskManager = taskManager;
    }

    public CliExecutionBridge getBridge() {
        return bridge;
    }

    public ScheduledTaskManager getTaskManager() {
        return taskManager;
    }

    @Option(names = {"--system"}, description = "Target system-wide scope instead of user scope")
    private boolean system;

    @Override
    public Integer call() {
        boolean json = isJsonRequested(spec);
        PrintWriter out = resolveOut(spec);
        PrintWriter err = resolveErr(spec);

        TimerScope scope = system ? TimerScope.SYSTEM : TimerScope.USER;
        return bridge.execute(TimerListCapability.ID, new TimerListRequest(scope), json, out, err);
    }

    @Component
    @Command(
        name = "list",
        aliases = {"ls"},
        description = "List all configured recurring timers and their live states",
        mixinStandardHelpOptions = true
    )
    public static class ListSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @ParentCommand
        private TimerCommand parent;

        @Option(names = {"--system"}, description = "Target system-wide scope instead of user scope")
        private boolean system;

        @Override
        public Integer call() {
            boolean json = isJsonRequested(spec);
            PrintWriter out = resolveOut(spec);
            PrintWriter err = resolveErr(spec);

            TimerScope scope = system ? TimerScope.SYSTEM : TimerScope.USER;
            return parent.getBridge().execute(TimerListCapability.ID, new TimerListRequest(scope), json, out, err);
        }
    }

    @Component
    @Command(
        name = "create",
        aliases = {"add", "new"},
        description = "Create a new scheduled timer and scaffold its executable automation script stub",
        mixinStandardHelpOptions = true
    )
    public static class CreateSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @ParentCommand
        private TimerCommand parent;

        @Parameters(index = "0", description = "Name of the timer (e.g. backup-db, sync-files, cert-renew)")
        private String name;

        @Option(
            names = {"-s", "--schedule"},
            description = "Schedule preset ('hourly', 'daily', 'midnight', 'weekly', 'twice-weekly', 'monthly', 'boot') or monotonic interval ('every 15m', '2h', '30s') or calendar spec",
            defaultValue = "daily"
        )
        private String schedule;

        @Option(names = {"-d", "--desc", "--description"}, description = "Human-readable description of this task")
        private String description;

        @Option(names = {"--content"}, description = "Initial script content (if not provided, default scaffold is used)")
        private String scriptContent;

        @Option(names = {"-e", "--edit"}, description = "Open executable script in editor immediately after creation")
        private boolean openEditor;

        @Option(names = {"-p", "--print"}, description = "Print script path and editor instructions")
        private boolean printPath;

        @Option(names = {"--system"}, description = "Create as system-wide daemon timer instead of user timer")
        private boolean system;

        @Option(names = {"--editor"}, description = "Custom editor executable (e.g. nano, vim, code)")
        private String editor;

        @Override
        public Integer call() {
            boolean json = isJsonRequested(spec);
            PrintWriter out = resolveOut(spec);
            PrintWriter err = resolveErr(spec);

            TimerScope scope = system ? TimerScope.SYSTEM : TimerScope.USER;
            TimerCreateRequest req = new TimerCreateRequest(name, schedule, description, scope, scriptContent, openEditor);

            int exitCode = parent.getBridge().execute(TimerCreateCapability.ID, req, json, out, err);
            if (exitCode != 0) {
                return exitCode;
            }

            if (openEditor && !json) {
                Path scriptPath = parent.getTaskManager().resolveScriptPath(name, scope);
                if (Files.exists(scriptPath)) {
                    launchEditor(scriptPath.toFile(), editor, out, err);
                }
            }

            return 0;
        }
    }

    @Component
    @Command(
        name = "status",
        aliases = {"info", "show"},
        description = "Inspect live status, next activation time, and recent execution logs of a timer",
        mixinStandardHelpOptions = true
    )
    public static class StatusSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @ParentCommand
        private TimerCommand parent;

        @Parameters(index = "0", description = "Timer ID or name (e.g. NX001, backup-db, NX001-backup-db)")
        private String nameOrId;

        @Option(names = {"--system"}, description = "Target system-wide scope instead of user scope")
        private boolean system;

        @Override
        public Integer call() {
            boolean json = isJsonRequested(spec);
            PrintWriter out = resolveOut(spec);
            PrintWriter err = resolveErr(spec);

            TimerScope scope = system ? TimerScope.SYSTEM : TimerScope.USER;
            TimerStatusRequest req = new TimerStatusRequest(nameOrId, scope);
            return parent.getBridge().execute(TimerStatusCapability.ID, req, json, out, err);
        }
    }

    @Component
    @Command(
        name = "edit",
        description = "Edit a timer's script stub, schedule, or configuration files",
        mixinStandardHelpOptions = true
    )
    public static class EditSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @ParentCommand
        private TimerCommand parent;

        @Parameters(index = "0", description = "Timer ID or name (e.g. NX001, backup-db)")
        private String nameOrId;

        @Option(names = {"-s", "--schedule"}, description = "New schedule expression to update")
        private String schedule;

        @Option(names = {"-d", "--desc", "--description"}, description = "New description")
        private String description;

        @Option(names = {"--timer"}, description = "Open systemd .timer unit file instead of script")
        private boolean editTimerUnit;

        @Option(names = {"--service"}, description = "Open systemd .service unit file instead of script")
        private boolean editServiceUnit;

        @Option(names = {"--script"}, description = "Open executable script in editor (default)")
        private boolean editScript;

        @Option(names = {"-e", "--editor"}, description = "Custom editor executable (e.g. nano, vim, code)")
        private String editor;

        @Option(names = {"-p", "--print"}, description = "Print file path and command rather than launching editor")
        private boolean printOnly;

        @Option(names = {"--system"}, description = "Target system-wide scope instead of user scope")
        private boolean system;

        @Override
        public Integer call() {
            boolean json = isJsonRequested(spec);
            PrintWriter out = resolveOut(spec);
            PrintWriter err = resolveErr(spec);

            TimerScope scope = system ? TimerScope.SYSTEM : TimerScope.USER;

            // If updating schedule or description via flags
            if (schedule != null || description != null) {
                TimerUpdateRequest req = new TimerUpdateRequest(nameOrId, schedule, description, scope);
                return parent.getBridge().execute(TimerUpdateCapability.ID, req, json, out, err);
            }

            // Otherwise, interactive file editing
            Path targetPath;
            if (editTimerUnit || editServiceUnit) {
                var statusRes = parent.getTaskManager().getTimerStatus(nameOrId, scope);
                if (!statusRes.success() || statusRes.timer() == null) {
                    err.println("Error: Could not locate timer " + nameOrId);
                    return 1;
                }
                String pathStr = editTimerUnit ? statusRes.timer().unitPath() : statusRes.timer().serviceUnitPath();
                targetPath = Path.of(pathStr);
            } else {
                targetPath = parent.getTaskManager().resolveScriptPath(nameOrId, scope);
            }

            File targetFile = targetPath.toFile();
            if (!targetFile.exists()) {
                err.println("Error: File does not exist at " + targetFile.getAbsolutePath());
                return 1;
            }

            String chosenEditor = resolveEditor(editor);

            boolean hasInteractiveConsole = System.console() != null;
            if (printOnly || (!hasInteractiveConsole && editor == null)) {
                out.println("=== Target Automation File ===");
                out.println("Path: " + targetFile.getAbsolutePath());
                out.println("\nTo edit this file, run:");
                out.println("  " + chosenEditor + " " + targetFile.getAbsolutePath());
                out.println("or:");
                out.println("  ${EDITOR:-" + chosenEditor + "} " + targetFile.getAbsolutePath());
                return 0;
            }

            return launchEditor(targetFile, chosenEditor, out, err);
        }
    }

    @Component
    @Command(
        name = "run",
        aliases = {"exec", "trigger"},
        description = "Immediately trigger a one-off execution of a timer's automation script",
        mixinStandardHelpOptions = true
    )
    public static class RunSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @ParentCommand
        private TimerCommand parent;

        @Parameters(index = "0", description = "Timer ID or name (e.g. NX001, backup-db)")
        private String nameOrId;

        @Option(names = {"--system"}, description = "Target system-wide scope instead of user scope")
        private boolean system;

        @Override
        public Integer call() {
            boolean json = isJsonRequested(spec);
            PrintWriter out = resolveOut(spec);
            PrintWriter err = resolveErr(spec);

            TimerScope scope = system ? TimerScope.SYSTEM : TimerScope.USER;
            TimerRunRequest req = new TimerRunRequest(nameOrId, scope);
            return parent.getBridge().execute(TimerRunCapability.ID, req, json, out, err);
        }
    }

    @Component
    @Command(
        name = "delete",
        aliases = {"rm", "remove"},
        description = "Delete and unregister a scheduled timer",
        mixinStandardHelpOptions = true
    )
    public static class DeleteSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @ParentCommand
        private TimerCommand parent;

        @Parameters(index = "0", description = "Timer ID or name (e.g. NX001, backup-db)")
        private String nameOrId;

        @Option(names = {"--purge-script"}, description = "Also delete the associated executable script file")
        private boolean purgeScript;

        @Option(names = {"-y", "--yes"}, description = "Confirm deletion without interactive prompt")
        private boolean confirm;

        @Option(names = {"--system"}, description = "Target system-wide scope instead of user scope")
        private boolean system;

        @Override
        public Integer call() {
            boolean json = isJsonRequested(spec);
            PrintWriter out = resolveOut(spec);
            PrintWriter err = resolveErr(spec);

            TimerScope scope = system ? TimerScope.SYSTEM : TimerScope.USER;
            TimerDeleteRequest req = new TimerDeleteRequest(nameOrId, purgeScript, scope);
            return parent.getBridge().execute(TimerDeleteCapability.ID, req, json, out, err);
        }
    }

    private static int launchEditor(File file, String explicitEditor, PrintWriter out, PrintWriter err) {
        String chosenEditor = resolveEditor(explicitEditor);
        try {
            out.println("Opening " + file.getName() + " in " + chosenEditor + "...");
            ProcessBuilder pb = new ProcessBuilder(chosenEditor, file.getAbsolutePath());
            pb.inheritIO();
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                out.println("✓ Editor closed.");
            } else {
                err.println("Editor process exited with code " + exitCode);
            }
            return exitCode;
        } catch (Exception e) {
            out.println("Could not launch interactive editor '" + chosenEditor + "': " + e.getMessage());
            out.println("Edit manually with: " + chosenEditor + " " + file.getAbsolutePath());
            return 0;
        }
    }

    private static String resolveEditor(String explicitEditor) {
        if (explicitEditor != null && !explicitEditor.isBlank()) {
            return explicitEditor;
        }
        String envVisual = System.getenv("VISUAL");
        if (envVisual != null && !envVisual.isBlank()) {
            return envVisual;
        }
        String envEditor = System.getenv("EDITOR");
        if (envEditor != null && !envEditor.isBlank()) {
            return envEditor;
        }
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return "notepad";
        }
        for (String candidate : new String[]{"nano", "vim", "vi"}) {
            if (isCommandAvailable(candidate)) {
                return candidate;
            }
        }
        return "nano";
    }

    private static boolean isJsonRequested(CommandSpec spec) {
        if (spec != null && spec.root() != null && spec.root().commandLine().getCommand() instanceof NexusCliRootCommand root) {
            return root.isJson();
        }
        return false;
    }

    private static PrintWriter resolveOut(CommandSpec spec) {
        if (spec != null && spec.root() != null) {
            return spec.root().commandLine().getOut();
        }
        if (spec != null) {
            return spec.commandLine().getOut();
        }
        return new PrintWriter(System.out, true);
    }

    private static PrintWriter resolveErr(CommandSpec spec) {
        if (spec != null && spec.root() != null) {
            return spec.root().commandLine().getErr();
        }
        if (spec != null) {
            return spec.commandLine().getErr();
        }
        return new PrintWriter(System.err, true);
    }

    private static boolean isCommandAvailable(String cmd) {
        try {
            Process p = new ProcessBuilder("which", cmd).start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
