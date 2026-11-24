package com.nexus.adapters.cli.commands;

import com.nexus.adapters.tui.NexusTuiLauncher;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.util.concurrent.Callable;

/**
 * Top-level CLI command to launch the interactive Terminal User Interface (TUI).
 */
@Component
@Command(
    name = "tui",
    aliases = {"ui", "dashboard"},
    description = "Launch interactive terminal dashboard (TUI) for host, timers, and reminders",
    mixinStandardHelpOptions = true
)
public class TuiCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    private final NexusTuiLauncher launcher;

    public TuiCommand(NexusTuiLauncher launcher) {
        this.launcher = launcher;
    }

    @Override
    public Integer call() {
        return launcher.launch();
    }
}
