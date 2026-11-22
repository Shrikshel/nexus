package com.nexus.adapters.cli.commands;

import com.nexus.adapters.cli.CliExecutionBridge;
import com.nexus.adapters.cli.NexusCliRootCommand;
import com.nexus.capabilities.network.NetworkInfoCapability;
import com.nexus.capabilities.process.ProcessListCapability;
import com.nexus.capabilities.resource.CpuCapability;
import com.nexus.capabilities.resource.MemoryCapability;
import com.nexus.capabilities.service.ServiceListCapability;
import com.nexus.capabilities.storage.DiskListCapability;
import com.nexus.capabilities.storage.DiskSmartCapability;
import com.nexus.capabilities.system.audit.SystemAuditCapability;
import com.nexus.capabilities.system.SystemInfoCapability;
import com.nexus.capabilities.system.SystemPoweroffCapability;
import com.nexus.capabilities.system.SystemRebootCapability;
import com.nexus.capabilities.system.SystemUptimeCapability;
import com.nexus.capabilities.system.dto.PowerOperationRequest;
import com.nexus.capabilities.system.package_mgr.SystemPackageUpdateCapability;
import com.nexus.capabilities.system.package_mgr.SystemPackageUpdateUpgradeCapability;
import com.nexus.capabilities.system.package_mgr.SystemPackageUpgradeCapability;
import com.nexus.capabilities.system.package_mgr.dto.PackageOperationRequest;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.util.Scanner;
import java.util.concurrent.Callable;

/**
 * CLI command group for host system package management, telemetry, hardware diagnostics,
 * audits, and power operations.
 */
@Component
@Command(
    name = "system",
    aliases = {"sys"},
    description = "Manage host system packages, telemetry, hardware, and power",
    subcommands = {
        SystemCommand.UpdateSubcommand.class,
        SystemCommand.UpgradeSubcommand.class,
        SystemCommand.UpdateUpgradeSubcommand.class,
        SystemCommand.InfoSubcommand.class,
        SystemCommand.UptimeSubcommand.class,
        SystemCommand.CpuSubcommand.class,
        SystemCommand.MemorySubcommand.class,
        SystemCommand.DiskSubcommand.class,
        SystemCommand.SmartSubcommand.class,
        SystemCommand.NetworkSubcommand.class,
        SystemCommand.ProcessSubcommand.class,
        SystemCommand.ServiceSubcommand.class,
        SystemCommand.AuditSubcommand.class,
        SystemCommand.RebootSubcommand.class,
        SystemCommand.PoweroffSubcommand.class
    },
    mixinStandardHelpOptions = true
)
public class SystemCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    private final CliExecutionBridge bridge;

    public SystemCommand(CliExecutionBridge bridge) {
        this.bridge = bridge;
    }

    public CliExecutionBridge getBridge() {
        return bridge;
    }

    @Override
    public Integer call() {
        if (spec != null) {
            spec.commandLine().usage(resolveOut(spec));
        }
        return 0;
    }

    // 1. Update Packages
    @Component
    @Command(
        name = "update",
        description = "Update System Package repositories",
        mixinStandardHelpOptions = true
    )
    public static class UpdateSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @ParentCommand
        private SystemCommand parent;

        @Override
        public Integer call() {
            boolean json = isJsonRequested(spec);
            PrintWriter out = resolveOut(spec);
            PrintWriter err = resolveErr(spec);
            return parent.getBridge().execute(SystemPackageUpdateCapability.ID, PackageOperationRequest.defaults(), json, out, err);
        }
    }

    // 2. Upgrade Packages
    @Component
    @Command(
        name = "upgrade",
        description = "Upgrade System Packages",
        mixinStandardHelpOptions = true
    )
    public static class UpgradeSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @ParentCommand
        private SystemCommand parent;

        @Option(names = {"-y", "--yes"}, description = "Automatic confirmation of prompts", defaultValue = "true")
        private boolean assumeYes;

        @Option(names = {"--dry-run"}, description = "Simulate package upgrade without modifying system")
        private boolean dryRun;

        @Override
        public Integer call() {
            boolean json = isJsonRequested(spec);
            PrintWriter out = resolveOut(spec);
            PrintWriter err = resolveErr(spec);
            PackageOperationRequest req = new PackageOperationRequest(assumeYes, dryRun);
            return parent.getBridge().execute(SystemPackageUpgradeCapability.ID, req, json, out, err);
        }
    }

    // 3. Update & Upgrade Packages
    @Component
    @Command(
        name = "update-upgrade",
        aliases = {"full-upgrade"},
        description = "Update & Upgrade System Packages in sequence",
        mixinStandardHelpOptions = true
    )
    public static class UpdateUpgradeSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @ParentCommand
        private SystemCommand parent;

        @Option(names = {"-y", "--yes"}, description = "Automatic confirmation of prompts", defaultValue = "true")
        private boolean assumeYes;

        @Option(names = {"--dry-run"}, description = "Simulate package operations without modifying system")
        private boolean dryRun;

        @Override
        public Integer call() {
            boolean json = isJsonRequested(spec);
            PrintWriter out = resolveOut(spec);
            PrintWriter err = resolveErr(spec);
            PackageOperationRequest req = new PackageOperationRequest(assumeYes, dryRun);
            return parent.getBridge().execute(SystemPackageUpdateUpgradeCapability.ID, req, json, out, err);
        }
    }

    // 4. Info
    @Component
    @Command(
        name = "info",
        description = "Show system info (hardware, OS, kernel, and memory summary)",
        mixinStandardHelpOptions = true
    )
    public static class InfoSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @ParentCommand
        private SystemCommand parent;

        @Override
        public Integer call() {
            boolean json = isJsonRequested(spec);
            PrintWriter out = resolveOut(spec);
            PrintWriter err = resolveErr(spec);
            return parent.getBridge().execute(SystemInfoCapability.ID, null, json, out, err);
        }
    }

    // 5. Uptime
    @Component
    @Command(
        name = "uptime",
        description = "Show host uptime and daemon boot timestamps",
        mixinStandardHelpOptions = true
    )
    public static class UptimeSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @ParentCommand
        private SystemCommand parent;

        @Override
        public Integer call() {
            boolean json = isJsonRequested(spec);
            PrintWriter out = resolveOut(spec);
            PrintWriter err = resolveErr(spec);
            return parent.getBridge().execute(SystemUptimeCapability.ID, null, json, out, err);
        }
    }

    // 6. CPU
    @Component
    @Command(
        name = "cpu",
        description = "Show CPU cores, load averages, and utilization",
        mixinStandardHelpOptions = true
    )
    public static class CpuSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @ParentCommand
        private SystemCommand parent;

        @Override
        public Integer call() {
            boolean json = isJsonRequested(spec);
            PrintWriter out = resolveOut(spec);
            PrintWriter err = resolveErr(spec);
            return parent.getBridge().execute(CpuCapability.ID, null, json, out, err);
        }
    }

    // 7. Memory
    @Component
    @Command(
        name = "memory",
        aliases = {"mem"},
        description = "Show physical RAM and Swap space utilization",
        mixinStandardHelpOptions = true
    )
    public static class MemorySubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @ParentCommand
        private SystemCommand parent;

        @Override
        public Integer call() {
            boolean json = isJsonRequested(spec);
            PrintWriter out = resolveOut(spec);
            PrintWriter err = resolveErr(spec);
            return parent.getBridge().execute(MemoryCapability.ID, null, json, out, err);
        }
    }

    // 8. Disk
    @Component
    @Command(
        name = "disk",
        description = "Show disk usage stats (mounted filesystems and capacity)",
        mixinStandardHelpOptions = true
    )
    public static class DiskSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @ParentCommand
        private SystemCommand parent;

        @Override
        public Integer call() {
            boolean json = isJsonRequested(spec);
            PrintWriter out = resolveOut(spec);
            PrintWriter err = resolveErr(spec);
            return parent.getBridge().execute(DiskListCapability.ID, null, json, out, err);
        }
    }

    // 9. SMART
    @Component
    @Command(
        name = "smart",
        description = "Show SSD/HDD SMART stats and drive health",
        mixinStandardHelpOptions = true
    )
    public static class SmartSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @ParentCommand
        private SystemCommand parent;

        @Parameters(index = "0", arity = "0..1", paramLabel = "[device]", description = "Target disk device (e.g., /dev/sda, /dev/nvme0n1)")
        private String targetDevice;

        @Override
        public Integer call() {
            boolean json = isJsonRequested(spec);
            PrintWriter out = resolveOut(spec);
            PrintWriter err = resolveErr(spec);
            return parent.getBridge().execute(DiskSmartCapability.ID, targetDevice, json, out, err);
        }
    }

    // 10. Network
    @Component
    @Command(
        name = "network",
        aliases = {"net"},
        description = "Show network interfaces, IPs, MAC, and MTU",
        mixinStandardHelpOptions = true
    )
    public static class NetworkSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @ParentCommand
        private SystemCommand parent;

        @Override
        public Integer call() {
            boolean json = isJsonRequested(spec);
            PrintWriter out = resolveOut(spec);
            PrintWriter err = resolveErr(spec);
            return parent.getBridge().execute(NetworkInfoCapability.ID, null, json, out, err);
        }
    }

    // 11. Process
    @Component
    @Command(
        name = "process",
        aliases = {"proc", "ps", "procs"},
        description = "List and inspect running system processes",
        mixinStandardHelpOptions = true
    )
    public static class ProcessSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @ParentCommand
        private SystemCommand parent;

        @Override
        public Integer call() {
            boolean json = isJsonRequested(spec);
            PrintWriter out = resolveOut(spec);
            PrintWriter err = resolveErr(spec);
            return parent.getBridge().execute(ProcessListCapability.ID, null, json, out, err);
        }
    }

    // 12. Service
    @Component
    @Command(
        name = "service",
        aliases = {"services", "svc"},
        description = "Inspect installed system services and runtime states",
        mixinStandardHelpOptions = true
    )
    public static class ServiceSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @ParentCommand
        private SystemCommand parent;

        @Override
        public Integer call() {
            boolean json = isJsonRequested(spec);
            PrintWriter out = resolveOut(spec);
            PrintWriter err = resolveErr(spec);
            return parent.getBridge().execute(ServiceListCapability.ID, null, json, out, err);
        }
    }

    // 13. Audit
    @Component
    @Command(
        name = "audit",
        description = "Run self audit to check for host issues (disk thresholds, memory, failed services, network)",
        mixinStandardHelpOptions = true
    )
    public static class AuditSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @ParentCommand
        private SystemCommand parent;

        @Override
        public Integer call() {
            boolean json = isJsonRequested(spec);
            PrintWriter out = resolveOut(spec);
            PrintWriter err = resolveErr(spec);
            return parent.getBridge().execute(SystemAuditCapability.ID, null, json, out, err);
        }
    }

    // 14. Reboot
    @Component
    @Command(
        name = "reboot",
        aliases = {"restart"},
        description = "Initiates a privileged restart / reboot of the host machine (requires sudo/admin)",
        mixinStandardHelpOptions = true
    )
    public static class RebootSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @ParentCommand
        private SystemCommand parent;

        @Option(names = {"-y", "--yes"}, description = "Automatic confirmation without interactive prompt")
        private boolean confirmed;

        @Option(names = {"-d", "--delay"}, description = "Delay in seconds before initiating restart", defaultValue = "0")
        private int delaySeconds;

        @Option(names = {"-r", "--reason"}, description = "Reason message logged with the reboot", defaultValue = "Nexus CLI reboot")
        private String reason;

        @Override
        public Integer call() {
            boolean json = isJsonRequested(spec);
            PrintWriter out = resolveOut(spec);
            PrintWriter err = resolveErr(spec);

            if (!confirmed && !json) {
                out.print("\u001B[1;33mAre you sure you want to REBOOT this host? [y/N]: \u001B[0m");
                out.flush();
                Scanner scanner = new Scanner(System.in);
                String line = scanner.hasNextLine() ? scanner.nextLine().trim() : "";
                if (!line.equalsIgnoreCase("y") && !line.equalsIgnoreCase("yes")) {
                    out.println("Reboot aborted.");
                    return 0;
                }
            }

            PowerOperationRequest request = new PowerOperationRequest(true, delaySeconds, reason);
            return parent.getBridge().execute(SystemRebootCapability.ID, request, json, out, err);
        }
    }

    // 15. Poweroff
    @Component
    @Command(
        name = "poweroff",
        aliases = {"shutdown", "halt"},
        description = "Initiates a privileged shutdown / poweroff of the host machine (requires sudo/admin)",
        mixinStandardHelpOptions = true
    )
    public static class PoweroffSubcommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @ParentCommand
        private SystemCommand parent;

        @Option(names = {"-y", "--yes"}, description = "Automatic confirmation without interactive prompt")
        private boolean confirmed;

        @Option(names = {"-d", "--delay"}, description = "Delay in seconds before initiating shutdown", defaultValue = "0")
        private int delaySeconds;

        @Option(names = {"-r", "--reason"}, description = "Reason message logged with the poweroff", defaultValue = "Nexus CLI poweroff")
        private String reason;

        @Override
        public Integer call() {
            boolean json = isJsonRequested(spec);
            PrintWriter out = resolveOut(spec);
            PrintWriter err = resolveErr(spec);

            if (!confirmed && !json) {
                out.print("\u001B[1;31mAre you sure you want to POWER OFF this host? [y/N]: \u001B[0m");
                out.flush();
                Scanner scanner = new Scanner(System.in);
                String line = scanner.hasNextLine() ? scanner.nextLine().trim() : "";
                if (!line.equalsIgnoreCase("y") && !line.equalsIgnoreCase("yes")) {
                    out.println("Poweroff aborted.");
                    return 0;
                }
            }

            PowerOperationRequest request = new PowerOperationRequest(true, delaySeconds, reason);
            return parent.getBridge().execute(SystemPoweroffCapability.ID, request, json, out, err);
        }
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
}
