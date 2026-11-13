package com.nexus.capabilities.terminal.tmux;

import com.nexus.capabilities.terminal.tmux.dto.TmuxAttachSessionRequest;
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
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Capability to verify and inspect attachability of a tmux session.
 */
@Component
public class TmuxAttachSessionCapability implements Capability<TmuxAttachSessionRequest, TmuxOperationResult> {

    public static final String ID = "tmux.session.attach";
    private static final Logger log = LoggerFactory.getLogger(TmuxAttachSessionCapability.class);
    private static final Pattern SAFE_SESSION_NAME = Pattern.compile("^[a-zA-Z0-9._-]+$");

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "Attach Tmux Session";
    }

    @Override
    public String getDescription() {
        return "Verifies and prepares attachment to an existing tmux session";
    }

    @Override
    public CapabilityCategory getCategory() {
        return CapabilityCategory.PROCESS;
    }

    @Override
    public Set<Permission> getRequiredPermissions() {
        return Set.of(Permission.READ_PROCESSES);
    }

    @Override
    public Class<TmuxAttachSessionRequest> getInputType() {
        return TmuxAttachSessionRequest.class;
    }

    @Override
    public Class<TmuxOperationResult> getOutputType() {
        return TmuxOperationResult.class;
    }

    @Override
    public CapabilityResult<TmuxOperationResult> execute(ExecutionContext context, TmuxAttachSessionRequest input) {
        Instant start = Instant.now();

        if (!TmuxLocator.isTmuxInstalled()) {
            return CapabilityResult.failure(ID, ErrorCode.PLATFORM_UNSUPPORTED, "tmux is not installed on this host", start);
        }

        if (input == null || input.sessionName() == null || input.sessionName().isBlank()) {
            return CapabilityResult.failure(ID, ErrorCode.INVALID_INPUT, "Session name must be provided", start);
        }

        String sessionName = input.sessionName().trim();
        if (!SAFE_SESSION_NAME.matcher(sessionName).matches()) {
            return CapabilityResult.failure(ID, ErrorCode.INVALID_INPUT,
                    "Invalid session name: must contain only alphanumeric characters, '.', '-', or '_'", start);
        }

        String binary = TmuxLocator.findTmuxBinary();
        try {
            Process process = new ProcessBuilder(binary, "has-session", "-t", sessionName).start();

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
                return CapabilityResult.failure(ID, ErrorCode.TIMEOUT, "tmux has-session check timed out", start);
            }

            int exitCode = process.exitValue();
            boolean exists = exitCode == 0;
            String msg = exists
                    ? "Session '" + sessionName + "' is active. In interactive terminal run: tmux attach -t " + sessionName
                    : "Session '" + sessionName + "' not found: " + stderr.toString().trim();

            TmuxOperationResult result = new TmuxOperationResult(
                    exists,
                    "ATTACH",
                    sessionName,
                    msg,
                    exitCode,
                    Instant.now()
            );

            if (exists) {
                return CapabilityResult.success(ID, result, start);
            } else {
                return CapabilityResult.failure(ID, ErrorCode.DEVICE_ERROR, msg, start);
            }
        } catch (Exception e) {
            log.error("Failed to check tmux session: {}", e.getMessage());
            return CapabilityResult.failure(ID, ErrorCode.INTERNAL_ERROR, "Failed to check tmux session: " + e.getMessage(), start);
        }
    }
}
