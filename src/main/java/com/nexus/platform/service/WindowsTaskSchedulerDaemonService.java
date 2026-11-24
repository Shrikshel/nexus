package com.nexus.platform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Windows implementation of daemon service management via Windows Task Scheduler (schtasks.exe).
 */
@Component
public class WindowsTaskSchedulerDaemonService implements DaemonServiceProvider {

    private static final Logger log = LoggerFactory.getLogger(WindowsTaskSchedulerDaemonService.class);
    private static final String TASK_NAME = "NexusDaemon";

    @Override
    public boolean isSupported() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    @Override
    public String getPlatformName() {
        return "Windows Task Scheduler";
    }

    @Override
    public boolean isServiceInstalled(boolean systemScope) {
        String query = runSchtasksCapture(List.of("/query", "/tn", TASK_NAME));
        return !query.contains("ERROR:") && query.contains(TASK_NAME);
    }

    @Override
    public boolean isServiceEnabled(boolean systemScope) {
        String query = runSchtasksCapture(List.of("/query", "/tn", TASK_NAME, "/v", "/fo", "list"));
        return query.contains("Enabled") || query.contains("Ready") || query.contains("Running");
    }

    @Override
    public boolean isServiceRunning(boolean systemScope) {
        String query = runSchtasksCapture(List.of("/query", "/tn", TASK_NAME, "/v", "/fo", "list"));
        return query.contains("Running");
    }

    @Override
    public ServiceOperationResult installService(boolean systemScope, boolean enable, String binaryPath) {
        String scheduleType = systemScope ? "onstart" : "onlogon";
        String runLevel = systemScope ? "highest" : "limited";

        List<String> args = List.of(
                "/create",
                "/tn", TASK_NAME,
                "/tr", "\"" + binaryPath + "\" daemon start --foreground",
                "/sc", scheduleType,
                "/rl", runLevel,
                "/f"
        );

        int code = runSchtasks(args);
        if (code != 0) {
            return new ServiceOperationResult(false, "INSTALL", TASK_NAME, "TaskScheduler:\\" + TASK_NAME, "Failed to create scheduled task.");
        }

        if (!enable) {
            runSchtasks(List.of("/change", "/tn", TASK_NAME, "/disable"));
        }

        return new ServiceOperationResult(true, "INSTALL", TASK_NAME, "TaskScheduler:\\" + TASK_NAME,
                "Windows scheduled task created successfully" + (enable ? " and enabled" : ""));
    }

    @Override
    public ServiceOperationResult enableService(boolean systemScope) {
        int code = runSchtasks(List.of("/change", "/tn", TASK_NAME, "/enable"));
        return new ServiceOperationResult(code == 0, "ENABLE", TASK_NAME, "TaskScheduler:\\" + TASK_NAME,
                code == 0 ? "Task enabled" : "Failed to enable task");
    }

    @Override
    public ServiceOperationResult disableService(boolean systemScope) {
        int code = runSchtasks(List.of("/change", "/tn", TASK_NAME, "/disable"));
        return new ServiceOperationResult(code == 0, "DISABLE", TASK_NAME, "TaskScheduler:\\" + TASK_NAME,
                code == 0 ? "Task disabled" : "Failed to disable task");
    }

    @Override
    public ServiceOperationResult startService(boolean systemScope) {
        int code = runSchtasks(List.of("/run", "/tn", TASK_NAME));
        return new ServiceOperationResult(code == 0, "START", TASK_NAME, "TaskScheduler:\\" + TASK_NAME,
                code == 0 ? "Nexus daemon task triggered" : "Failed to trigger task");
    }

    @Override
    public ServiceOperationResult stopService(boolean systemScope) {
        int code = runSchtasks(List.of("/end", "/tn", TASK_NAME));
        return new ServiceOperationResult(code == 0, "STOP", TASK_NAME, "TaskScheduler:\\" + TASK_NAME,
                code == 0 ? "Nexus daemon task stopped" : "Failed to stop task");
    }

    @Override
    public ServiceOperationResult restartService(boolean systemScope) {
        if (!isServiceInstalled(systemScope)) {
            return new ServiceOperationResult(false, "RESTART", TASK_NAME, "TaskScheduler:\\" + TASK_NAME, "Task is not installed. Run 'nexus daemon install' first.");
        }
        stopService(systemScope);
        try {
            Thread.sleep(500);
        } catch (InterruptedException ignored) {}
        ServiceOperationResult startRes = startService(systemScope);
        return new ServiceOperationResult(startRes.success(), "RESTART", TASK_NAME, "TaskScheduler:\\" + TASK_NAME,
                startRes.success() ? "Restarted Nexus daemon task" : "Failed to restart daemon task: " + startRes.message());
    }

    @Override
    public ServiceOperationResult uninstallService(boolean systemScope) {
        int code = runSchtasks(List.of("/delete", "/tn", TASK_NAME, "/f"));
        return new ServiceOperationResult(code == 0, "UNINSTALL", TASK_NAME, "TaskScheduler:\\" + TASK_NAME,
                code == 0 ? "Nexus daemon task deleted" : "Failed to delete task");
    }

    @Override
    public ServiceStatusDetails getServiceStatus(boolean systemScope) {
        boolean installed = isServiceInstalled(systemScope);
        boolean enabled = installed && isServiceEnabled(systemScope);
        boolean running = installed && isServiceRunning(systemScope);
        String state = running ? "Running" : (installed ? (enabled ? "Ready" : "Disabled") : "Not installed");

        return new ServiceStatusDetails(installed, enabled, running, TASK_NAME, "TaskScheduler:\\" + TASK_NAME,
                "Windows Task Scheduler", state);
    }

    @Override
    public String getLogGuidance(boolean systemScope) {
        return "Check Event Viewer -> Applications and Services Logs -> Microsoft -> Windows -> TaskScheduler";
    }

    private int runSchtasks(List<String> args) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("schtasks.exe");
            cmd.addAll(args);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            return pb.start().waitFor();
        } catch (Exception e) {
            return -1;
        }
    }

    private String runSchtasksCapture(List<String> args) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("schtasks.exe");
            cmd.addAll(args);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            return new String(pb.start().getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return "";
        }
    }
}
