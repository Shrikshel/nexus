package com.nexus.capabilities.terminal.tmux;

import com.nexus.capabilities.terminal.tmux.dto.TmuxNewSessionRequest;
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
 * Capability to create a new detached tmux session.
 */
@Component
public class TmuxNewSessionCapability implements Capability<TmuxNewSessionRequest, TmuxOperationResult> {

    public static final String ID = "tmux.session.new";
    private static final Logger log = LoggerFactory.getLogger(TmuxNewSessionCapability.class);
    private static final Pattern SAFE_SESSION_NAME = Pattern.compile("^[a-zA-Z0-9._-]+$");

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "Create New Tmux Session";
    }

    @Override
    public String getDescription() {
        return "Creates a new background/detached tmux terminal session";
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
    public Class<TmuxNewSessionRequest> getInputType() {
        return TmuxNewSessionRequest.class;
    }

    @Override
    public Class<TmuxOperationResult> getOutputType() {
        return TmuxOperationResult.class;
    }

    @Override
    public CapabilityResult<TmuxOperationResult> execute(ExecutionContext context, TmuxNewSessionRequest input) {
        Instant start = Instant.now();

        if (!TmuxLocator.isTmuxInstalled()) {
            return CapabilityResult.failure(ID, ErrorCode.PLATFORM_UNSUPPORTED, "tmux is not installed on this host", start);
        }

        String sessionName = (input != null && input.sessionName() != null && !input.sessionName().isBlank())
                ? input.sessionName().trim()
                : "nexus-" + (System.currentTimeMillis() % 10000);

        if (!SAFE_SESSION_NAME.matcher(sessionName).matches()) {
            return CapabilityResult.failure(ID, ErrorCode.INVALID_INPUT,
                    "Invalid session name: must contain only alphanumeric characters, '.', '-', or '_'", start);
        }

        String binary = TmuxLocator.findTmuxBinary();
        List<String> cmd = new ArrayList<>();
        cmd.add(binary);
        cmd.add("new-session");
        cmd.add("-d");
        cmd.add("-s");
        cmd.add(sessionName);

        if (input != null && input.startDirectory() != null && !input.startDirectory().isBlank()) {
            cmd.add("-c");
            cmd.add(input.startDirectory().trim());
        }

        if (input != null && input.windowName() != null && !input.windowName().isBlank()) {
            cmd.add("-n");
            cmd.add(input.windowName().trim());
        }

        if (input != null && input.command() != null && !input.command().isBlank()) {
            cmd.add(input.command().trim());
        }

        try {
            log.info("Creating new tmux session '{}' triggered by {}", sessionName, context.caller().username());
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
                return CapabilityResult.failure(ID, ErrorCode.TIMEOUT, "tmux new-session timed out", start);
            }

            int exitCode = process.exitValue();
            boolean success = exitCode == 0;
            String err = stderr.toString().trim();
            String msg = success ? "Session '" + sessionName + "' created successfully (detached)" : "Failed to create session: " + err;

            TmuxOperationResult result = new TmuxOperationResult(
                    success,
                    "NEW",
                    sessionName,
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
            log.error("Failed to create tmux session: {}", e.getMessage());
            return CapabilityResult.failure(ID, ErrorCode.INTERNAL_ERROR, "Failed to create tmux session: " + e.getMessage(), start);
        }
    }
}
