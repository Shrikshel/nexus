package com.nexus.adapters.tui.screens;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import com.nexus.capabilities.process.ProcessListCapability;
import com.nexus.capabilities.process.dto.ProcessInfo;
import com.nexus.capabilities.process.dto.ProcessListReport;
import com.nexus.capabilities.storage.dto.DiskListReport;
import com.nexus.capabilities.storage.dto.DiskPartitionReport;
import com.nexus.capabilities.system.audit.SystemAuditCapability;
import com.nexus.capabilities.system.audit.dto.AuditCheckItem;
import com.nexus.capabilities.system.audit.dto.AuditSeverity;
import com.nexus.capabilities.system.audit.dto.SystemAuditReport;
import com.nexus.capabilities.system.dto.SystemInfoReport;
import com.nexus.core.capability.CapabilityResult;
import com.nexus.platform.StorageCollector;
import com.nexus.platform.SystemCollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class SystemScreenTest {

    private SystemCollector systemCollector;
    private StorageCollector storageCollector;
    private SystemAuditCapability auditCapability;
    private ProcessListCapability processListCapability;
    private SystemScreen screen;

    @BeforeEach
    void setup() {
        systemCollector = Mockito.mock(SystemCollector.class);
        storageCollector = Mockito.mock(StorageCollector.class);
        auditCapability = Mockito.mock(SystemAuditCapability.class);
        processListCapability = Mockito.mock(ProcessListCapability.class);

        SystemInfoReport hostInfo = new SystemInfoReport(
                "testhost", "Linux", "6.8.0", "x86_64", "6.8.0-generic",
                8, 16_000_000_000L, 8_000_000_000L,
                "Oracle", "17.0.2", "JVM", "UTC", Instant.now()
        );
        when(systemCollector.collectSystemInfo()).thenReturn(hostInfo);

        DiskPartitionReport p1 = new DiskPartitionReport("/", "rootfs", "ext4", 500_000_000_000L, 350_000_000_000L, 150_000_000_000L, 30.0);
        DiskPartitionReport p2 = new DiskPartitionReport("/home", "homefs", "ext4", 1_000_000_000_000L, 800_000_000_000L, 200_000_000_000L, 20.0);
        DiskListReport diskReport = new DiskListReport(List.of(p1, p2), 1_500_000_000_000L, 1_150_000_000_000L, 23.3, Instant.now());
        when(storageCollector.collectDiskList()).thenReturn(diskReport);

        AuditCheckItem c1 = new AuditCheckItem("DISK", "Root filesystem space", AuditSeverity.PASS, "30% used", null);
        AuditCheckItem c2 = new AuditCheckItem("NET", "Tailscale mesh", AuditSeverity.PASS, "Online", null);
        SystemAuditReport auditReport = new SystemAuditReport("PASSED", 2, 0, 0, List.of(c1, c2), Instant.now());
        when(auditCapability.execute(any(), any())).thenReturn(CapabilityResult.success("sys.audit", auditReport, Instant.now()));

        ProcessInfo pr1 = new ProcessInfo(1L, "root", "/sbin/init", "00:00:15", true);
        ProcessInfo pr2 = new ProcessInfo(2000L, "toy", "nexus tui", "00:00:02", true);
        ProcessListReport procReport = new ProcessListReport(List.of(pr1, pr2), 2, Instant.now());
        when(processListCapability.execute(any(), any())).thenReturn(CapabilityResult.success("proc.list", procReport, Instant.now()));

        screen = new SystemScreen(systemCollector, storageCollector, auditCapability, processListCapability, null);
    }

    @Test
    @DisplayName("Screen reports correct title and populates initial storage mounts")
    void testInitialState() {
        assertThat(screen.getTitle()).isEqualTo("4: System");
        assertThat(screen.getActiveView()).isEqualTo(SystemScreen.ViewMode.DISKS);
        assertThat(screen.getPartitions()).hasSize(2);
        assertThat(screen.getSelectedIndex()).isEqualTo(0);
    }

    @Test
    @DisplayName("Arrow keys navigate through disk rows and clamp at boundaries")
    void testCursorNavigation() {
        boolean handled = screen.handleInput(new KeyStroke(KeyType.ArrowDown));
        assertThat(handled).isTrue();
        assertThat(screen.getSelectedIndex()).isEqualTo(1);

        handled = screen.handleInput(new KeyStroke('j', false, false));
        assertThat(handled).isTrue();
        assertThat(screen.getSelectedIndex()).isEqualTo(1); // clamp

        handled = screen.handleInput(new KeyStroke(KeyType.ArrowUp));
        assertThat(handled).isTrue();
        assertThat(screen.getSelectedIndex()).isEqualTo(0);
    }

    @Test
    @DisplayName("Pressing 'v' cycles views (DISKS -> AUDIT -> PROCESSES -> DISKS)")
    void testViewCycling() {
        assertThat(screen.getActiveView()).isEqualTo(SystemScreen.ViewMode.DISKS);

        screen.handleInput(new KeyStroke('v', false, false));
        assertThat(screen.getActiveView()).isEqualTo(SystemScreen.ViewMode.AUDIT);

        screen.handleInput(new KeyStroke('v', false, false));
        assertThat(screen.getActiveView()).isEqualTo(SystemScreen.ViewMode.PROCESSES);

        screen.handleInput(new KeyStroke('v', false, false));
        assertThat(screen.getActiveView()).isEqualTo(SystemScreen.ViewMode.DISKS);
    }

    @Test
    @DisplayName("Pressing 'a' triggers deep audit and switches to AUDIT view")
    void testTriggerAudit() {
        boolean handled = screen.handleInput(new KeyStroke('a', false, false));
        assertThat(handled).isTrue();
        assertThat(screen.getActiveView()).isEqualTo(SystemScreen.ViewMode.AUDIT);
        assertThat(screen.getToastMessage()).contains("health audit");
    }

    @Test
    @DisplayName("Renders each sub-view on virtual terminal without error")
    void testRenderAllViews() throws Exception {
        DefaultVirtualTerminal terminal = new DefaultVirtualTerminal(new TerminalSize(100, 30));
        TerminalScreen terminalScreen = new TerminalScreen(terminal);
        terminalScreen.startScreen();
        TextGraphics tg = terminalScreen.newTextGraphics();

        // 1. Disks view
        screen.setActiveView(SystemScreen.ViewMode.DISKS);
        screen.render(tg, new TerminalSize(100, 30), 3, 24);

        // 2. Audit view
        screen.setActiveView(SystemScreen.ViewMode.AUDIT);
        screen.render(tg, new TerminalSize(100, 30), 3, 24);

        // 3. Processes view
        screen.setActiveView(SystemScreen.ViewMode.PROCESSES);
        screen.render(tg, new TerminalSize(100, 30), 3, 24);

        terminalScreen.refresh();
        terminalScreen.stopScreen();
    }
}
