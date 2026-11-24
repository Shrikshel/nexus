package com.nexus.adapters.cli.commands;

import com.nexus.adapters.cli.NexusCliRootCommand;
import com.nexus.adapters.tui.NexusTuiLauncher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TuiCommandTest {

    @Test
    @DisplayName("TuiCommand delegates to NexusTuiLauncher")
    void testTuiCommandExecution() {
        NexusTuiLauncher mockLauncher = Mockito.mock(NexusTuiLauncher.class);
        when(mockLauncher.launch()).thenReturn(0);

        TuiCommand cmd = new TuiCommand(mockLauncher);
        CommandLine commandLine = new CommandLine(cmd);
        int exitCode = commandLine.execute();

        assertThat(exitCode).isEqualTo(0);
        verify(mockLauncher).launch();
    }

    @Test
    @DisplayName("TuiCommand is registered in NexusCliRootCommand subcommands annotation")
    void testCommandRegistration() {
        CommandLine.Command cmdAnnotation = NexusCliRootCommand.class.getAnnotation(CommandLine.Command.class);
        assertThat(cmdAnnotation).isNotNull();
        assertThat(cmdAnnotation.subcommands()).contains(TuiCommand.class);

        CommandLine.Command tuiAnnotation = TuiCommand.class.getAnnotation(CommandLine.Command.class);
        assertThat(tuiAnnotation).isNotNull();
        assertThat(tuiAnnotation.name()).isEqualTo("tui");
        assertThat(tuiAnnotation.aliases()).contains("ui", "dashboard");
    }
}
