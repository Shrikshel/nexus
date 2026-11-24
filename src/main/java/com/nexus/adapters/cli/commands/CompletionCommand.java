package com.nexus.adapters.cli.commands;

import org.springframework.stereotype.Component;
import picocli.AutoComplete;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Generates shell auto-completion scripts for Bash, Zsh, and Fish.
 */
@Component
@Command(
    name = "completion",
    aliases = {"autocomplete", "completions"},
    description = "Generate shell auto-completion script for bash, zsh, or fish",
    mixinStandardHelpOptions = true
)
public class CompletionCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Parameters(
        index = "0",
        arity = "0..1",
        paramLabel = "<shell>",
        description = "Target shell: bash, zsh, fish (default: bash)"
    )
    private String shell = "bash";

    @Override
    public Integer call() {
        CommandLine root = spec.root().commandLine();
        PrintWriter out = spec.commandLine().getOut();

        String target = shell != null ? shell.trim().toLowerCase(Locale.ROOT) : "bash";
        switch (target) {
            case "zsh" -> generateZshCompletion(out, root);
            case "fish" -> generateFishCompletion(out, root);
            case "bash" -> generateBashCompletion(out, root);
            default -> {
                spec.commandLine().getErr().println("Unknown shell: '" + shell + "'. Supported shells: bash, zsh, fish");
                return 2;
            }
        }
        return 0;
    }

    private void generateBashCompletion(PrintWriter out, CommandLine root) {
        String script = AutoComplete.bash("nexus", root);
        out.print(script);
        out.println();
        out.println("# Register alias 'nx' with the same completion function");
        out.println("complete -F _complete_nexus -o default nx");
        out.flush();
    }

    private void generateZshCompletion(PrintWriter out, CommandLine root) {
        out.println("#compdef nexus nx");
        out.println();
        out.println("# Zsh completion script for Nexus (supports both 'nexus' and 'nx')");
        out.println("# Generated automatically by 'nexus completion zsh'");
        out.println("#");
        out.println("# Quick activation in current shell:");
        out.println("#   source <(nexus completion zsh)");
        out.println("#");
        out.println("# Permanent installation:");
        out.println("#   mkdir -p ~/.zsh/completion");
        out.println("#   nexus completion zsh > ~/.zsh/completion/_nexus");
        out.println("#   (Ensure 'fpath=(~/.zsh/completion $fpath)' is in ~/.zshrc before compinit)");
        out.println();
        out.println("if ! command -v compdef >/dev/null 2>&1; then");
        out.println("    autoload -U +X compinit && compinit");
        out.println("fi");
        out.println("autoload -U +X bashcompinit && bashcompinit");
        out.println();

        String script = AutoComplete.bash("nexus", root);
        out.print(script);
        out.println();
        out.println("# Register alias 'nx' with the same completion function");
        out.println("complete -F _complete_nexus -o default nx");
        out.flush();
    }

    private void generateFishCompletion(PrintWriter out, CommandLine root) {
        out.println("# Fish completion script for Nexus (supports both 'nexus' and 'nx')");
        out.println("# Generated automatically by 'nexus completion fish'");
        out.println("#");
        out.println("# Quick activation in current shell:");
        out.println("#   nexus completion fish | source");
        out.println("#");
        out.println("# Permanent installation:");
        out.println("#   mkdir -p ~/.config/fish/completions");
        out.println("#   nexus completion fish > ~/.config/fish/completions/nexus.fish");
        out.println("#   ln -sf ~/.config/fish/completions/nexus.fish ~/.config/fish/completions/nx.fish");
        out.println();
        out.println("# Disable file completions for nexus and nx");
        out.println("complete -c nexus -f");
        out.println("complete -c nx -f");
        out.println();

        for (String cmd : new String[]{"nexus", "nx"}) {
            // Global root options
            for (OptionSpec opt : root.getCommandSpec().options()) {
                String longest = opt.longestName();
                String shortest = opt.shortestName();
                String desc = opt.description() != null && opt.description().length > 0 ? opt.description()[0] : "";
                StringBuilder sb = new StringBuilder("complete -c ").append(cmd);
                if (shortest.startsWith("-") && !shortest.startsWith("--")) {
                    sb.append(" -s ").append(shortest.substring(1));
                }
                if (longest.startsWith("--")) {
                    sb.append(" -l ").append(longest.substring(2));
                }
                if (!desc.isBlank()) {
                    sb.append(" -d '").append(escapeFish(desc)).append("'");
                }
                out.println(sb);
            }

            // Subcommands
            for (Map.Entry<String, CommandLine> entry : root.getSubcommands().entrySet()) {
                String subName = entry.getKey();
                CommandLine subCmd = entry.getValue();
                String subDesc = subCmd.getCommandSpec().usageMessage().header().length > 0
                        ? subCmd.getCommandSpec().usageMessage().header()[0]
                        : (subCmd.getCommandSpec().usageMessage().description().length > 0
                        ? subCmd.getCommandSpec().usageMessage().description()[0] : "");

                out.println("complete -c " + cmd + " -n \"__fish_use_subcommand\" -a \"" + subName + "\" -d '" + escapeFish(subDesc) + "'");

                // Nested subcommands
                for (Map.Entry<String, CommandLine> nestedEntry : subCmd.getSubcommands().entrySet()) {
                    String nestedName = nestedEntry.getKey();
                    CommandLine nestedCmd = nestedEntry.getValue();
                    String nestedDesc = nestedCmd.getCommandSpec().usageMessage().description().length > 0
                            ? nestedCmd.getCommandSpec().usageMessage().description()[0] : "";
                    out.println("complete -c " + cmd + " -n \"__fish_seen_subcommand_from " + subName + "\" -a \"" + nestedName + "\" -d '" + escapeFish(nestedDesc) + "'");
                }
            }
        }
        out.flush();
    }

    private static String escapeFish(String str) {
        if (str == null) return "";
        return str.replace("'", "\\'");
    }
}
