package com.nexus.adapters.cli.commands;

import com.nexus.adapters.cli.CliExecutionBridge;
import com.nexus.adapters.cli.NexusCliRootCommand;
import com.nexus.capabilities.system.SystemPoweroffCapability;
import com.nexus.capabilities.system.dto.PowerOperationRequest;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.util.Scanner;
import java.util.concurrent.Callable;

/**
 * CLI command to request host shutdown / poweroff.
 */
@Component
@Command(
    name = "poweroff",
    aliases = {"shutdown", "halt"},
    description = "Initiates a privileged shutdown / poweroff of the host machine (requires sudo/admin)",
    mixinStandardHelpOptions = true
)
public class PoweroffCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Option(names = {"-y", "--yes"}, description = "Automatic confirmation without interactive prompt")
    private boolean confirmed;

    @Option(names = {"-d", "--delay"}, description = "Delay in seconds before initiating poweroff", defaultValue = "0")
    private int delaySeconds;

    @Option(names = {"-r", "--reason"}, description = "Reason message logged with the poweroff", defaultValue = "Nexus CLI poweroff")
    private String reason;

    private final CliExecutionBridge bridge;

    public PoweroffCommand(CliExecutionBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public Integer call() {
        NexusCliRootCommand root = spec != null && spec.root() != null ? spec.root().commandLine().getCommand() : null;
        boolean json = root != null && root.isJson();
        PrintWriter out = spec != null ? spec.commandLine().getOut() : new PrintWriter(System.out, true);
        PrintWriter err = spec != null ? spec.commandLine().getErr() : new PrintWriter(System.err, true);

        if (!confirmed) {
            if (System.console() != null) {
                out.print("\u001B[31mAre you sure you want to SHUT DOWN / POWER OFF this host? [y/N]: \u001B[0m");
                out.flush();
                Scanner scanner = new Scanner(System.in);
                if (scanner.hasNextLine()) {
                    String input = scanner.nextLine().trim().toLowerCase();
                    if ("y".equals(input) || "yes".equals(input)) {
                        confirmed = true;
                    }
                }
            }
            if (!confirmed) {
                err.println("\u001B[31mOperation cancelled: Poweroff requires confirmation. Pass -y or --yes to proceed non-interactively.\u001B[0m");
                return 1;
            }
        }

        PowerOperationRequest request = new PowerOperationRequest(true, delaySeconds, reason);
        return bridge.execute(SystemPoweroffCapability.ID, request, json, out, err);
    }
}
