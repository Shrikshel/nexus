package com.nexus.platform.service;

/**
 * Platform provider interface for operating-system service supervision (systemd, launchd, Task Scheduler).
 */
public interface DaemonServiceProvider {

    boolean isSupported();

    String getPlatformName();

    boolean isServiceInstalled(boolean systemScope);

    boolean isServiceEnabled(boolean systemScope);

    boolean isServiceRunning(boolean systemScope);

    ServiceOperationResult installService(boolean systemScope, boolean enable, String binaryPath);

    ServiceOperationResult enableService(boolean systemScope);

    ServiceOperationResult disableService(boolean systemScope);

    ServiceOperationResult startService(boolean systemScope);

    ServiceOperationResult stopService(boolean systemScope);

    ServiceOperationResult uninstallService(boolean systemScope);

    ServiceStatusDetails getServiceStatus(boolean systemScope);

    String getLogGuidance(boolean systemScope);
}
