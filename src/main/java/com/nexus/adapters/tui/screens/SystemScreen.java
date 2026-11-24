package com.nexus.adapters.tui.screens;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.nexus.adapters.tui.NexusTuiScreen;
import com.nexus.adapters.tui.TuiDrawingHelper;
import com.nexus.capabilities.system.dto.SystemInfoReport;
import com.nexus.capabilities.system.dto.SystemUptimeReport;
import com.nexus.platform.SystemCollector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * Screen 4: Host System Audit & Storage Screen (Incremental foundation).
 */
@Component
public class SystemScreen implements NexusTuiScreen {

    private final SystemCollector systemCollector;
    private SystemInfoReport systemInfo;
    private SystemUptimeReport systemUptime;

    @Autowired
    public SystemScreen(@Autowired(required = false) SystemCollector systemCollector) {
        this.systemCollector = systemCollector;
        refresh();
    }

    @Override
    public String getTitle() {
        return "4: System";
    }

    @Override
    public synchronized void refresh() {
        if (systemCollector != null) {
            try {
                this.systemInfo = systemCollector.collectSystemInfo();
                this.systemUptime = systemCollector.collectSystemUptime();
            } catch (Exception ignored) {}
        }
    }

    @Override
    public synchronized void render(TextGraphics tg, TerminalSize size, int startRow, int availableRows) {
        int width = size.getColumns();
        int boxWidth = Math.max(30, width - 4);
        int boxHeight = Math.max(10, availableRows - 1);

        TuiDrawingHelper.drawBox(tg, 2, startRow, boxWidth, boxHeight, "Host Architecture & Storage Telemetry", TextColor.ANSI.CYAN, TextColor.ANSI.CYAN_BRIGHT);

        int y = startRow + 2;
        tg.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
        tg.enableModifiers(SGR.BOLD);
        tg.putString(4, y, "System Specifications");
        tg.clearModifiers();

        y += 2;
        String host = systemInfo != null ? systemInfo.hostname() : "localhost";
        String os = systemInfo != null ? systemInfo.osName() + " (" + systemInfo.osVersion() + ")" : System.getProperty("os.name");
        String arch = systemInfo != null ? systemInfo.osArch() : System.getProperty("os.arch");
        String jvm = systemInfo != null ? systemInfo.javaVendor() + " " + systemInfo.javaVersion() : System.getProperty("java.version");
        String runtime = systemInfo != null ? systemInfo.runtimeType() : "JVM";

        tg.putString(4, y, "Hostname:       " + host);
        tg.putString(4, y + 1, "Operating Sys:  " + os);
        tg.putString(4, y + 2, "Architecture:   " + arch);
        tg.putString(4, y + 3, "Java Runtime:   " + jvm + " [" + runtime + "]");

        y += 5;
        tg.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
        tg.enableModifiers(SGR.BOLD);
        tg.putString(4, y, "Primary Storage Mounts");
        tg.clearModifiers();

        y += 2;
        tg.setForegroundColor(TextColor.ANSI.CYAN_BRIGHT);
        tg.putString(4, y, TuiDrawingHelper.padRight("Mount Point", 18) + TuiDrawingHelper.padRight("Total", 14) + TuiDrawingHelper.padRight("Free", 14) + "Usage");
        y++;

        File[] roots = File.listRoots();
        if (roots != null) {
            for (File root : roots) {
                if (y >= startRow + boxHeight - 3) break;
                long total = root.getTotalSpace();
                long free = root.getFreeSpace();
                long used = total - free;
                double pct = total > 0 ? (used * 100.0) / total : 0.0;

                String mount = root.getAbsolutePath();
                String totalStr = total > 0 ? TuiDrawingHelper.formatBytes(total) : "N/A";
                String freeStr = total > 0 ? TuiDrawingHelper.formatBytes(free) : "N/A";
                String pctStr = String.format("%.1f%%", pct);

                tg.setForegroundColor(TextColor.ANSI.WHITE);
                tg.putString(4, y, TuiDrawingHelper.padRight(mount, 18)
                        + TuiDrawingHelper.padRight(totalStr, 14)
                        + TuiDrawingHelper.padRight(freeStr, 14)
                        + pctStr);
                y++;
            }
        }

        tg.setForegroundColor(TextColor.ANSI.BLACK_BRIGHT);
        tg.putString(4, startRow + boxHeight - 2, "Deep disk telemetry, ZFS inspection, and process monitor arriving in Step 4.");
    }

    @Override
    public boolean handleInput(KeyStroke key) {
        return false;
    }
}
