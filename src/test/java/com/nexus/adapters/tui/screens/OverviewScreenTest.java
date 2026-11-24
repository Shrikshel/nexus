package com.nexus.adapters.tui.screens;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import com.nexus.capabilities.system.dto.SystemInfoReport;
import com.nexus.capabilities.system.dto.SystemUptimeReport;
import com.nexus.platform.SystemCollector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class OverviewScreenTest {

    @Test
    @DisplayName("OverviewScreen returns correct title and handles null dependencies safely")
    void testBasicAttributes() {
        OverviewScreen screen = new OverviewScreen(null, null, null, null);
        assertThat(screen.getTitle()).isEqualTo("1: Overview");
        screen.refresh();
    }

    @Test
    @DisplayName("OverviewScreen renders telemetry properly on virtual terminal")
    void testRenderWithTelemetry() throws Exception {
        SystemCollector collector = Mockito.mock(SystemCollector.class);
        SystemInfoReport info = new SystemInfoReport(
                "testhost", "Linux", "6.8.0", "x86_64", "6.8.0-generic",
                8, 16_000_000_000L, 8_000_000_000L,
                "Oracle", "17.0.2", "JVM", "UTC", Instant.now()
        );
        SystemUptimeReport uptime = new SystemUptimeReport(3600, "1h 0m", 1800, "30m", Instant.now(), Instant.now());

        when(collector.collectSystemInfo()).thenReturn(info);
        when(collector.collectSystemUptime()).thenReturn(uptime);

        OverviewScreen screen = new OverviewScreen(collector, null, null, null);

        DefaultVirtualTerminal terminal = new DefaultVirtualTerminal(new TerminalSize(100, 30));
        TerminalScreen terminalScreen = new TerminalScreen(terminal);
        terminalScreen.startScreen();
        TextGraphics tg = terminalScreen.newTextGraphics();

        screen.render(tg, new TerminalSize(100, 30), 3, 24);

        terminalScreen.refresh();
        terminalScreen.stopScreen();
    }
}
