package com.nexus.adapters.cli.commands;

import com.nexus.NexusApplication;
import com.nexus.adapters.cli.NexusCliRootCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = NexusApplication.class)
class CompletionCommandTest {

    @Autowired
    private IFactory factory;

    @Autowired
    private NexusCliRootCommand rootCommand;

    private CommandLine commandLine;
    private StringWriter outWriter;
    private StringWriter errWriter;

    @BeforeEach
    void setup() {
        commandLine = new CommandLine(rootCommand, factory);
        outWriter = new StringWriter();
        errWriter = new StringWriter();
        commandLine.setOut(new PrintWriter(outWriter));
        commandLine.setErr(new PrintWriter(errWriter));
    }

    @Test
    @DisplayName("Generates Bash completion script with 'nexus' and 'nx' bindings")
    void testBashCompletion() {
        int exitCode = commandLine.execute("completion", "bash");
        assertThat(exitCode).isEqualTo(0);
        String output = outWriter.toString();
        assertThat(output).contains("_complete_nexus");
        assertThat(output).contains("complete -F _complete_nexus -o default nx");
        assertThat(output).contains("reminder");
        assertThat(output).contains("daemon");
        assertThat(output).contains("system");
    }

    @Test
    @DisplayName("Generates default completion (Bash) when no shell specified")
    void testDefaultCompletion() {
        int exitCode = commandLine.execute("completion");
        assertThat(exitCode).isEqualTo(0);
        String output = outWriter.toString();
        assertThat(output).contains("_complete_nexus");
        assertThat(output).contains("complete -F _complete_nexus -o default nx");
    }

    @Test
    @DisplayName("Generates Zsh completion script with #compdef and bashcompinit bridge")
    void testZshCompletion() {
        int exitCode = commandLine.execute("completion", "zsh");
        assertThat(exitCode).isEqualTo(0);
        String output = outWriter.toString();
        assertThat(output).contains("#compdef nexus nx");
        assertThat(output).contains("bashcompinit");
        assertThat(output).contains("complete -F _complete_nexus -o default nx");
    }

    @Test
    @DisplayName("Generates Fish completion script with complete -c nexus and complete -c nx")
    void testFishCompletion() {
        int exitCode = commandLine.execute("completion", "fish");
        assertThat(exitCode).isEqualTo(0);
        String output = outWriter.toString();
        assertThat(output).contains("complete -c nexus -f");
        assertThat(output).contains("complete -c nx -f");
        assertThat(output).contains("complete -c nexus -n \"__fish_use_subcommand\" -a \"reminder\"");
        assertThat(output).contains("complete -c nx -n \"__fish_use_subcommand\" -a \"reminder\"");
    }
}
