package com.nexus.capabilities.process;

import com.nexus.capabilities.process.dto.ProcessInfo;
import com.nexus.capabilities.process.dto.ProcessListReport;
import com.nexus.core.capability.*;
import com.nexus.core.security.Permission;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Capability providing a listing of active operating system processes.
 */
@Component
public class ProcessListCapability implements Capability<Void, ProcessListReport> {

    public static final String ID = "proc.list";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "Process List";
    }

    @Override
    public String getDescription() {
        return "Returns currently running processes with PID, user, and command";
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
    public Class<ProcessListReport> getOutputType() {
        return ProcessListReport.class;
    }

    @Override
    public CapabilityResult<ProcessListReport> execute(ExecutionContext context, Void input) {
        Instant start = Instant.now();
        try {
            List<ProcessHandle> handles = ProcessHandle.allProcesses().collect(Collectors.toList());
            int total = handles.size();

            List<ProcessInfo> list = handles.stream()
                    .map(h -> {
                        long pid = h.pid();
                        String user = h.info().user().orElse("unknown");
                        String cmd = h.info().command().orElse(h.info().commandLine().orElse("[kernel/unknown]"));
                        String cpu = h.info().totalCpuDuration().map(Duration::toString).orElse("0s");
                        return new ProcessInfo(pid, user, cmd, cpu, h.isAlive());
                    })
                    .sorted(Comparator.comparing(ProcessInfo::pid))
                    .collect(Collectors.toList());

            ProcessListReport report = new ProcessListReport(list, total, Instant.now());
            return CapabilityResult.success(ID, report, start);
        } catch (Exception e) {
            return CapabilityResult.failure(ID, ErrorCode.INTERNAL_ERROR, "Failed to inspect processes: " + e.getMessage(), start);
        }
    }
}
