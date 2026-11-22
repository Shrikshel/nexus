package com.nexus.adapters.cli.commands;

import com.nexus.adapters.cli.CliExecutionBridge;
import com.nexus.adapters.cli.NexusCliRootCommand;
import com.nexus.capabilities.network.telegram.TelegramSendCapability;
import com.nexus.capabilities.network.telegram.dto.TelegramSendRequest;
import com.nexus.capabilities.network.telegram.dto.TelegramSendType;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Picocli command group for dispatching Telegram messages, documents, photos, and stickers.
 */
@Component
@Command(
    name = "telegram",
    aliases = {"tg"},
    description = "Send notifications, messages, files, and media via Telegram",
    subcommands = {
        TelegramCommand.SendSubcommand.class
    },
    mixinStandardHelpOptions = true
)
public class TelegramCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    private final CliExecutionBridge bridge;

    public TelegramCommand(CliExecutionBridge bridge) {
        this.bridge = bridge;
    }

    public CliExecutionBridge getBridge() {
        return bridge;
    }

    @Override
    public Integer call() {
        if (spec != null) {
            spec.commandLine().usage(resolveOut(spec));
        }
        return 0;
    }

    @Component
    @Command(
        name = "send",
        aliases = {"msg", "notify"},
        description = "Send a text message, document, photo, or sticker to configured recipients",
        subcommands = {
            FileSubcommand.class,
            PhotoSubcommand.class,
            StickerSubcommand.class
        },
        mixinStandardHelpOptions = true
    )
    public static class SendSubcommand implements Callable<Integer> {

        @Spec
        private CommandSpec spec;

        @ParentCommand
        private TelegramCommand parent;

        private CliExecutionBridge bridge;

        public SendSubcommand() {}

        public SendSubcommand(CliExecutionBridge bridge) {
            this.bridge = bridge;
        }

        public void setBridge(CliExecutionBridge bridge) {
            this.bridge = bridge;
        }

        public CliExecutionBridge resolveBridge() {
            if (parent != null && parent.getBridge() != null) {
                return parent.getBridge();
            }
            return bridge;
        }

        @Parameters(index = "0", arity = "0..1", paramLabel = "<message>", description = "Message text to send, or '-' to read from stdin")
        private String message;

        @Option(names = {"-f", "--file"}, description = "Path to file or document to upload")
        private String file;

        @Option(names = {"-p", "--photo"}, description = "Path to photo or image to upload")
        private String photo;

        @Option(names = {"--sticker"}, description = "Sticker file path (.webp/.tgs) or Telegram sticker file_id")
        private String sticker;

        @Option(names = {"--caption"}, description = "Caption text for file or photo")
        private String caption;

        @Option(names = {"-r", "--recipient", "--chat-id"}, description = "Target Telegram recipient or @channel (repeatable)")
        private List<String> recipients;

        @Option(names = {"-s", "--silent"}, description = "Send silently without sound notification")
        private boolean silent;

        @Option(names = {"--format"}, description = "Parse mode: HTML, Markdown, or MarkdownV2 (default: HTML)")
        private String parseMode;

        @Option(names = {"--token"}, description = "Telegram Bot API token override")
        private String botToken;

        public TelegramCommand getParent() {
            return parent;
        }

        @Override
        public Integer call() {
            boolean json = isJsonRequested(spec);
            PrintWriter out = resolveOut(spec);
            PrintWriter err = resolveErr(spec);
            CliExecutionBridge execBridge = resolveBridge();

            if (execBridge == null) {
                err.println("\u001B[31m✖ Error: CliExecutionBridge is not available.\u001B[0m");
                return 1;
            }

            TelegramSendType type = TelegramSendType.AUTO;
            String filePath = null;

            if (sticker != null && !sticker.isBlank()) {
                type = TelegramSendType.STICKER;
                filePath = sticker;
            } else if (photo != null && !photo.isBlank()) {
                type = TelegramSendType.PHOTO;
                filePath = photo;
            } else if (file != null && !file.isBlank()) {
                type = TelegramSendType.DOCUMENT;
                filePath = file;
            }

            // Resolve message / stdin
            String resolvedMessage = message;
            if ("-".equals(resolvedMessage)) {
                resolvedMessage = readAllStandardInput();
            } else if ((resolvedMessage == null || resolvedMessage.isBlank()) && filePath == null) {
                try {
                    if (System.in.available() > 0) {
                        resolvedMessage = readAllStandardInput();
                    }
                } catch (Exception ignored) {
                }
            }

            if (filePath == null && (resolvedMessage == null || resolvedMessage.isBlank())) {
                err.println("\u001B[31m✖ Error: Message text, file (-f), photo (-p), or sticker (--sticker) must be specified.\u001B[0m");
                err.println("  Hint: Use '-' to pipe from standard input: cat file.txt | nexus telegram send -");
                err.println("  Run 'nexus telegram send --help' for details.");
                return 1;
            }

            TelegramSendRequest request = new TelegramSendRequest(
                    resolvedMessage,
                    filePath,
                    type,
                    caption,
                    recipients,
                    parseMode,
                    silent,
                    botToken
            );

            return execBridge.execute(TelegramSendCapability.ID, request, json, out, err);
        }
    }

    @Component
    @Command(
        name = "file",
        aliases = {"doc", "document"},
        description = "Send a file or document to Telegram recipients",
        mixinStandardHelpOptions = true
    )
    public static class FileSubcommand implements Callable<Integer> {

        @Spec
        private CommandSpec spec;

        @ParentCommand
        private SendSubcommand parent;

        private CliExecutionBridge bridge;

        public FileSubcommand() {}

        public FileSubcommand(CliExecutionBridge bridge) {
            this.bridge = bridge;
        }

        public void setBridge(CliExecutionBridge bridge) {
            this.bridge = bridge;
        }

        public CliExecutionBridge resolveBridge() {
            if (parent != null) {
                return parent.resolveBridge();
            }
            return bridge;
        }

        @Parameters(index = "0", arity = "1", paramLabel = "<path>", description = "Path to local file to upload")
        private String filePath;

        @Option(names = {"--caption"}, description = "Caption text for the file")
        private String caption;

        @Option(names = {"-r", "--recipient", "--chat-id"}, description = "Target Telegram recipient or @channel (repeatable)")
        private List<String> recipients;

        @Option(names = {"-s", "--silent"}, description = "Send silently without sound notification")
        private boolean silent;

        @Option(names = {"--format"}, description = "Parse mode: HTML, Markdown, or MarkdownV2 (default: HTML)")
        private String parseMode;

        @Option(names = {"--token"}, description = "Telegram Bot API token override")
        private String botToken;

        @Override
        public Integer call() {
            boolean json = isJsonRequested(spec);
            PrintWriter out = resolveOut(spec);
            PrintWriter err = resolveErr(spec);
            CliExecutionBridge execBridge = resolveBridge();

            if (execBridge == null) {
                err.println("\u001B[31m✖ Error: CliExecutionBridge is not available.\u001B[0m");
                return 1;
            }

            TelegramSendRequest request = new TelegramSendRequest(
                    null,
                    filePath,
                    TelegramSendType.DOCUMENT,
                    caption,
                    recipients,
                    parseMode,
                    silent,
                    botToken
            );

            return execBridge.execute(TelegramSendCapability.ID, request, json, out, err);
        }
    }

    @Component
    @Command(
        name = "photo",
        aliases = {"image", "img", "pic"},
        description = "Send a photo or image to Telegram recipients",
        mixinStandardHelpOptions = true
    )
    public static class PhotoSubcommand implements Callable<Integer> {

        @Spec
        private CommandSpec spec;

        @ParentCommand
        private SendSubcommand parent;

        private CliExecutionBridge bridge;

        public PhotoSubcommand() {}

        public PhotoSubcommand(CliExecutionBridge bridge) {
            this.bridge = bridge;
        }

        public void setBridge(CliExecutionBridge bridge) {
            this.bridge = bridge;
        }

        public CliExecutionBridge resolveBridge() {
            if (parent != null) {
                return parent.resolveBridge();
            }
            return bridge;
        }

        @Parameters(index = "0", arity = "1", paramLabel = "<path>", description = "Path to local photo to upload")
        private String photoPath;

        @Option(names = {"--caption"}, description = "Caption text for the photo")
        private String caption;

        @Option(names = {"-r", "--recipient", "--chat-id"}, description = "Target Telegram recipient or @channel (repeatable)")
        private List<String> recipients;

        @Option(names = {"-s", "--silent"}, description = "Send silently without sound notification")
        private boolean silent;

        @Option(names = {"--format"}, description = "Parse mode: HTML, Markdown, or MarkdownV2 (default: HTML)")
        private String parseMode;

        @Option(names = {"--token"}, description = "Telegram Bot API token override")
        private String botToken;

        @Override
        public Integer call() {
            boolean json = isJsonRequested(spec);
            PrintWriter out = resolveOut(spec);
            PrintWriter err = resolveErr(spec);
            CliExecutionBridge execBridge = resolveBridge();

            if (execBridge == null) {
                err.println("\u001B[31m✖ Error: CliExecutionBridge is not available.\u001B[0m");
                return 1;
            }

            TelegramSendRequest request = new TelegramSendRequest(
                    null,
                    photoPath,
                    TelegramSendType.PHOTO,
                    caption,
                    recipients,
                    parseMode,
                    silent,
                    botToken
            );

            return execBridge.execute(TelegramSendCapability.ID, request, json, out, err);
        }
    }

    @Component
    @Command(
        name = "sticker",
        description = "Send a sticker (.webp/.tgs) or sticker file_id to Telegram recipients",
        mixinStandardHelpOptions = true
    )
    public static class StickerSubcommand implements Callable<Integer> {

        @Spec
        private CommandSpec spec;

        @ParentCommand
        private SendSubcommand parent;

        private CliExecutionBridge bridge;

        public StickerSubcommand() {}

        public StickerSubcommand(CliExecutionBridge bridge) {
            this.bridge = bridge;
        }

        public void setBridge(CliExecutionBridge bridge) {
            this.bridge = bridge;
        }

        public CliExecutionBridge resolveBridge() {
            if (parent != null) {
                return parent.resolveBridge();
            }
            return bridge;
        }

        @Parameters(index = "0", arity = "1", paramLabel = "<sticker>", description = "Sticker file path or Telegram sticker file_id")
        private String stickerTarget;

        @Option(names = {"-r", "--recipient", "--chat-id"}, description = "Target Telegram recipient or @channel (repeatable)")
        private List<String> recipients;

        @Option(names = {"-s", "--silent"}, description = "Send silently without sound notification")
        private boolean silent;

        @Option(names = {"--token"}, description = "Telegram Bot API token override")
        private String botToken;

        @Override
        public Integer call() {
            boolean json = isJsonRequested(spec);
            PrintWriter out = resolveOut(spec);
            PrintWriter err = resolveErr(spec);
            CliExecutionBridge execBridge = resolveBridge();

            if (execBridge == null) {
                err.println("\u001B[31m✖ Error: CliExecutionBridge is not available.\u001B[0m");
                return 1;
            }

            TelegramSendRequest request = new TelegramSendRequest(
                    stickerTarget,
                    stickerTarget,
                    TelegramSendType.STICKER,
                    null,
                    recipients,
                    null,
                    silent,
                    botToken
            );

            return execBridge.execute(TelegramSendCapability.ID, request, json, out, err);
        }
    }

    private static String readAllStandardInput() {
        try {
            byte[] bytes = System.in.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static boolean isJsonRequested(CommandSpec spec) {
        if (spec != null && spec.root() != null && spec.root().commandLine().getCommand() instanceof NexusCliRootCommand root) {
            return root.isJson();
        }
        return false;
    }

    private static PrintWriter resolveOut(CommandSpec spec) {
        if (spec != null && spec.root() != null) {
            return spec.root().commandLine().getOut();
        }
        if (spec != null) {
            return spec.commandLine().getOut();
        }
        return new PrintWriter(System.out, true);
    }

    private static PrintWriter resolveErr(CommandSpec spec) {
        if (spec != null && spec.root() != null) {
            return spec.root().commandLine().getErr();
        }
        if (spec != null) {
            return spec.commandLine().getErr();
        }
        return new PrintWriter(System.err, true);
    }
}
