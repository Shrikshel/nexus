package com.nexus.capabilities.terminal.tmux;

import com.nexus.capabilities.terminal.tmux.dto.TmuxSessionInfo;
import com.nexus.capabilities.terminal.tmux.dto.TmuxSessionListReport;
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

/**
 * Capability to query and list active tmux terminal sessions.
 */
@Component
public class TmuxListSessionsCapability implements Capability<Void, TmuxSessionListReport> {

    public static final String ID = "tmux.session.list";
    private static final Logger log = LoggerFactory.getLogger(TmuxListSessionsCapability.class);

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "List Tmux Sessions";
    }

    @Override
    public String getDescription() {
        return "Queries and lists running tmux terminal multiplexer sessions";
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
    public Class<Void> getInputType() {
        return Void.class;
    }

    @Override
    public Class<TmuxSessionListReport> getOutputType() {
        return TmuxSessionListReport.class;
    }

    @Override
    public CapabilityResult<TmuxSessionListReport> execute(ExecutionContext context, Void input) {
        Instant start = Instant.now();

        if (!TmuxLocator.isTmuxInstalled()) {
            return CapabilityResult.success(ID, TmuxSessionListReport.notInstalled(), start);
        }

        String binary = TmuxLocator.findTmuxBinary();
        try {
            Process process = new ProcessBuilder(
                    binary,
                    "list-sessions",
                    "-F",
                    "#{session_name}|#{session_windows}|#{session_created}|#{session_attached}|#{session_path}"
            ).start();

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            try (BufferedReader outReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = outReader.readLine()) != null) {
                    stdout.append(line).append("\n");
                }
                while ((line = errReader.readLine()) != null) {
                    stderr.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return CapabilityResult.failure(ID, ErrorCode.TIMEOUT, "tmux list-sessions timed out", start);
            }

            int exitCode = process.exitValue();
            String errStr = stderr.toString().trim();
            if (exitCode != 0) {
                if (errStr.contains("no server") || errStr.contains("error connecting") || errStr.contains("no sessions")) {
                    return CapabilityResult.success(ID, TmuxSessionListReport.empty("No active tmux sessions"), start);
                }
                return CapabilityResult.success(ID, TmuxSessionListReport.empty("tmux: " + (errStr.isBlank() ? "exit code " + exitCode : errStr)), start);
            }

            List<TmuxSessionInfo> sessions = parseSessionsOutput(stdout.toString());
            TmuxSessionListReport report = new TmuxSessionListReport(
                    true,
                    true,
                    sessions.size(),
                    sessions,
                    "Active tmux sessions: " + sessions.size(),
                    Instant.now()
            );

            return CapabilityResult.success(ID, report, start);
        } catch (Exception e) {
            log.error("Failed to list tmux sessions: {}", e.getMessage());
            return CapabilityResult.failure(ID, ErrorCode.INTERNAL_ERROR, "Failed to list tmux sessions: " + e.getMessage(), start);
        }
    }

    public List<TmuxSessionInfo> parseSessionsOutput(String output) {
        List<TmuxSessionInfo> list = new ArrayList<>();
        if (output == null || output.isBlank()) {
            return list;
        }

        for (String line : output.split("\n")) {
            line = line.trim();
            if (line.isBlank()) continue;

            String[] parts = line.split("\\|", -1);
            if (parts.length >= 4) {
                String name = parts[0];
                int windows = parseSafeInt(parts[1], 1);
                Instant created = parseSafeEpoch(parts[2]);
                boolean attached = "1".equals(parts[3]) || "true".equalsIgnoreCase(parts[3]);
                String dir = parts.length > 4 ? parts[4] : "";

                list.add(new TmuxSessionInfo(name, windows, created, attached, dir));
            }
        }
        return list;
    }

    private int parseSafeInt(String str, int fallback) {
        try {
            return Integer.parseInt(str.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private Instant parseSafeEpoch(String str) {
        try {
            long epoch = Long.parseLong(str.trim());
            return Instant.ofEpochSecond(epoch);
        } catch (Exception e) {
            return Instant.now();
        }
    }
}
