package com.nexus.capabilities.system;

import com.nexus.capabilities.system.dto.PowerOperationRequest;
import com.nexus.capabilities.system.dto.PowerOperationResult;
import com.nexus.core.capability.*;
import com.nexus.core.security.Permission;
import com.nexus.core.security.privilege.PrivilegeRunner;
import com.nexus.platform.PlatformDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Privileged capability to safely reboot the host operating system across Linux, macOS, and Windows.
 */
@Component
public class SystemRebootCapability implements Capability<PowerOperationRequest, PowerOperationResult> {

    public static final String ID = "sys.reboot";
    private static final Logger log = LoggerFactory.getLogger(SystemRebootCapability.class);

    private final PrivilegeRunner privilegeRunner;
    private final PlatformDetector platformDetector;

    public SystemRebootCapability(PrivilegeRunner privilegeRunner, PlatformDetector platformDetector) {
        this.privilegeRunner = privilegeRunner;
        this.platformDetector = platformDetector;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "System Host Reboot";
    }

    @Override
    public String getDescription() {
        return "Initiates a privileged restart / reboot sequence of the host machine (requires explicit confirmation)";
    }

    @Override
    public CapabilityCategory getCategory() {
        return CapabilityCategory.SYSTEM;
    }

    @Override
    public Set<Permission> getRequiredPermissions() {
        return Set.of(Permission.POWER_MANAGEMENT);
    }

    @Override
    public boolean isPrivileged() {
        return true;
    }

    @Override
    public Class<PowerOperationRequest> getInputType() {
        return PowerOperationRequest.class;
    }

    @Override
    public Class<PowerOperationResult> getOutputType() {
        return PowerOperationResult.class;
    }

    @Override
    public CapabilityResult<PowerOperationResult> execute(ExecutionContext context, PowerOperationRequest input) {
        Instant start = Instant.now();

        if (input == null || !input.confirm()) {
            return CapabilityResult.failure(ID, ErrorCode.FORBIDDEN,
                    "Reboot operation requires explicit confirmation (pass confirm=true or --yes)", start);
        }

        int delay = Math.max(0, input.delaySeconds());
        String reason = input.reason() != null && !input.reason().isBlank() ? input.reason() : "Nexus host reboot";
        log.warn("INITIATING PRIVILEGED SYSTEM REBOOT: delay={}s reason='{}' triggeredBy={}",
                delay, reason, context.caller().username());

        String binary;
        List<String> args = new ArrayList<>();

        if (platformDetector.isWindows()) {
            binary = "C:\\Windows\\System32\\shutdown.exe";
            args.add("/r");
            args.add("/t");
            args.add(String.valueOf(delay));
            args.add("/c");
            args.add(reason);
        } else if (platformDetector.isMac()) {
            binary = "/sbin/shutdown";
            args.add("-r");
            args.add(delay > 0 ? "+" + Math.max(1, delay / 60) : "now");
            args.add(reason);
        } else {
            // Linux
            if (new File("/bin/systemctl").exists() || new File("/usr/bin/systemctl").exists()) {
                binary = new File("/bin/systemctl").exists() ? "/bin/systemctl" : "/usr/bin/systemctl";
                args.add("reboot");
            } else if (new File("/sbin/shutdown").exists() || new File("/usr/sbin/shutdown").exists()) {
                binary = new File("/sbin/shutdown").exists() ? "/sbin/shutdown" : "/usr/sbin/shutdown";
                args.add("-r");
                args.add(delay > 0 ? "+" + Math.max(1, delay / 60) : "now");
                args.add(reason);
            } else {
                binary = new File("/sbin/reboot").exists() ? "/sbin/reboot" : "/usr/sbin/reboot";
            }
        }

        PrivilegeRunner.ExecutionOutput output = privilegeRunner.executeWhitelisted(binary, args, 15);
        boolean success = output.exitCode() == 0;
        String message = success
                ? String.format("Host reboot initiated successfully (delay=%ds). System will restart.", delay)
                : String.format("Failed to initiate reboot (exitCode=%d): %s", output.exitCode(), output.stdout().trim());

        PowerOperationResult result = new PowerOperationResult(
                success,
                "REBOOT",
                message,
                delay,
                Instant.now()
        );

        if (success) {
            return CapabilityResult.success(ID, result, start);
        } else {
            return CapabilityResult.failure(ID, ErrorCode.INTERNAL_ERROR, message, start);
        }
    }
}
