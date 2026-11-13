package com.nexus.capabilities.terminal.tmux;

import com.nexus.capabilities.terminal.tmux.dto.TmuxKillSessionRequest;
import com.nexus.capabilities.terminal.tmux.dto.TmuxOperationResult;
import com.nexus.core.capability.*;
import com.nexus.core.security.Permission;
import com.nexus.platform.TmuxLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Capability to terminate a tmux session or kill the tmux server.
 */
@Component
public class TmuxKillSessionCapability implements Capability<TmuxKillSessionRequest, TmuxOperationResult> {

    public static final String ID = "tmux.session.kill";
    private static final Logger log = LoggerFactory.getLogger(TmuxKillSessionCapability.class);
    private static final Pattern SAFE_SESSION_NAME = Pattern.compile("^[a-zA-Z0-9._-]+$");

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "Kill Tmux Session";
    }

    @Override
    public String getDescription() {
        return "Terminates a specific tmux terminal session or shuts down the tmux server";
    }

    @Override
    public CapabilityCategory getCategory() {
        return CapabilityCategory.PROCESS;
    }

    @Override
    public Set<Permission> getRequiredPermissions() {
        return Set.of(Permission.WRITE_PROCESSES);
    }

    @Override
    public Class<TmuxKillSessionRequest> getInputType() {
        return TmuxKillSessionRequest.class;
    }

    @Override
    public Class<TmuxOperationResult> getOutputType() {
        return TmuxOperationResult.class;
    }

    @Override
    public CapabilityResult<TmuxOperationResult> execute(ExecutionContext context, TmuxKillSessionRequest input) {
        Instant start = Instant.now();

        if (!TmuxLocator.isTmuxInstalled()) {
            return CapabilityResult.failure(ID, ErrorCode.PLATFORM_UNSUPPORTED, "tmux is not installed on this host", start);
        }

        if (input == null || (!input.killAll() && (input.sessionName() == null || input.sessionName().isBlank()))) {
            return CapabilityResult.failure(ID, ErrorCode.INVALID_INPUT, "Session name must be specified or killAll must be true", start);
        }

        String binary = TmuxLocator.findTmuxBinary();
        List<String> cmd = new ArrayList<>();
        cmd.add(binary);

        String targetName = input.sessionName() != null ? input.sessionName().trim() : "all";
        if (input.killAll()) {
            cmd.add("kill-server");
        } else {
            if (!SAFE_SESSION_NAME.matcher(targetName).matches()) {
                return CapabilityResult.failure(ID, ErrorCode.INVALID_INPUT,
                        "Invalid session name: must contain only alphanumeric characters, '.', '-', or '_'", start);
            }
            cmd.add("kill-session");
            cmd.add("-t");
            cmd.add(targetName);
        }

        try {
            log.info("Terminating tmux session '{}' triggered by {}", targetName, context.caller().username());
            Process process = new ProcessBuilder(cmd).start();

            StringBuilder stderr = new StringBuilder();
            try (BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = errReader.readLine()) != null) {
                    stderr.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return CapabilityResult.failure(ID, ErrorCode.TIMEOUT, "tmux kill timed out", start);
            }

            int exitCode = process.exitValue();
            boolean success = exitCode == 0;
            String err = stderr.toString().trim();
            String msg = success
                    ? (input.killAll() ? "All tmux sessions terminated (server killed)" : "Session '" + targetName + "' killed successfully")
                    : "Failed to kill session: " + (err.isBlank() ? "exit code " + exitCode : err);

            TmuxOperationResult result = new TmuxOperationResult(
                    success,
                    "KILL",
                    targetName,
                    msg,
                    exitCode,
                    Instant.now()
            );

            if (success) {
                return CapabilityResult.success(ID, result, start);
            } else {
                return CapabilityResult.failure(ID, ErrorCode.INTERNAL_ERROR, msg, start);
            }
        } catch (Exception e) {
            log.error("Failed to kill tmux session: {}", e.getMessage());
            return CapabilityResult.failure(ID, ErrorCode.INTERNAL_ERROR, "Failed to kill tmux session: " + e.getMessage(), start);
        }
    }
}
