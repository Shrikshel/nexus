package com.nexus.adapters.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.capabilities.network.tailscale.dto.TailscaleOperationRequest;
import com.nexus.capabilities.scheduler.timer.dto.TimerListRequest;
import com.nexus.capabilities.scheduler.timer.dto.TimerScope;
import com.nexus.capabilities.scheduler.timer.dto.TimerStatusRequest;
import com.nexus.capabilities.storage.zfs.dto.ZfsDatasetDetailRequest;
import com.nexus.capabilities.system.dto.PowerOperationRequest;
import com.nexus.config.NexusConfigResolver;
import com.nexus.config.NexusProperties;
import com.nexus.core.capability.CapabilityResult;
import com.nexus.core.capability.ExecutionContext;
import com.nexus.core.capability.ExecutionEngine;
import com.nexus.core.capability.InvocationSource;
import com.nexus.core.security.Role;
import com.nexus.core.security.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Inbound Telegram bot adapter managing long-polling, authorization, rate limiting,
 * and command dispatching through the ExecutionEngine.
 */
@Component
public class TelegramBotAdapter implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotAdapter.class);

    private final NexusProperties properties;
    private final TelegramClient client;
    private final TelegramSecurityFilter securityFilter;
    private final TelegramFormatter formatter;
    private final ExecutionEngine executionEngine;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService pollerExecutor;
    private long lastUpdateId = 0;

    public TelegramBotAdapter(
            NexusProperties properties,
            TelegramClient client,
            TelegramSecurityFilter securityFilter,
            TelegramFormatter formatter,
            ExecutionEngine executionEngine) {
        this.properties = properties;
        this.client = client;
        this.securityFilter = securityFilter;
        this.formatter = formatter;
        this.executionEngine = executionEngine;
    }

    @Override
    public void start() {
        NexusProperties.TelegramConfig config = properties.getTelegram();
        if (config == null || !config.isEnabled()) {
            log.info("Telegram bot adapter is disabled (nexus.telegram.enabled is false)");
            return;
        }

        if (config.getBotToken() == null || config.getBotToken().isBlank()) {
            log.warn("Telegram bot adapter is enabled, but bot-token is missing or blank");
            return;
        }

        if (running.compareAndSet(false, true)) {
            log.info("Starting Telegram bot long-polling adapter (chatId={})...", config.getAllowedChatId());
            pollerExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "nexus-tg-poller");
                t.setDaemon(true);
                return t;
            });
            pollerExecutor.submit(this::pollLoop);
        }
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            log.info("Stopping Telegram bot adapter...");
            if (pollerExecutor != null) {
                pollerExecutor.shutdownNow();
            }
        }
    }

    /**
     * Reloads configuration dynamically, updating or restarting the poller as needed.
     */
    public synchronized void reload() {
        NexusProperties.TelegramConfig config = properties.getTelegram();
        if (config == null || !config.isEnabled() || config.getBotToken() == null || config.getBotToken().isBlank()) {
            if (running.get()) {
                log.info("Telegram configuration disabled or removed. Stopping poller...");
                stop();
            }
            return;
        }

        if (running.get()) {
            log.info("Restarting Telegram bot poller with updated configuration (chatId={})...", config.getAllowedChatId());
            stop();
        }
        start();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return false;
    }

    private void pollLoop() {
        NexusProperties.TelegramConfig config = properties.getTelegram();
        long backoffMs = 1000;
        log.info("Telegram bot poll loop initiated. Listening for inbound commands...");

        while (running.get()) {
            try {
                JsonNode root = client.getUpdates(config.getBotToken(), lastUpdateId + 1, 10);
                backoffMs = 1000; // Reset backoff on success

                JsonNode result = root.path("result");
                if (result.isArray()) {
                    for (JsonNode update : result) {
                        long updateId = update.path("update_id").asLong();
                        if (updateId > lastUpdateId) {
                            lastUpdateId = updateId;
                        }
                        processUpdate(update, config);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("Telegram polling error: {}. Backing off for {}ms", e.getMessage(), backoffMs);
                try {
                    Thread.sleep(backoffMs);
                    backoffMs = Math.min(backoffMs * 2, 60000); // Max 60s
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        log.info("Telegram bot poll loop terminated.");
    }

    public void processUpdate(JsonNode update, NexusProperties.TelegramConfig config) {
        JsonNode message = update.path("message");
        if (message.isMissingNode()) {
            return;
        }

        long chatId = message.path("chat").path("id").asLong();
        long senderId = message.path("from").path("id").asLong();
        String username = message.path("from").path("username").asText("unknown_user");
        String text = message.path("text").asText("").trim();

        if (text.isBlank()) {
            return;
        }

        log.info("Received Telegram message from senderId={} ({}) in chatId={}: '{}'", senderId, username, chatId, text);

        // 1. Security allowlist check
        if (!securityFilter.isChatAllowed(chatId, config.getAllowedChatId())) {
            log.warn("Unauthorized Telegram access attempt from chatId={} username={}", chatId, username);
            return;
        }

        // 2. Rate limit check
        if (securityFilter.isRateLimited(senderId, 30)) {
            log.warn("Rate limit exceeded for Telegram senderId={}", senderId);
            client.sendMessage(config.getBotToken(), chatId, "⚠️ <i>Rate limit exceeded. Please wait a moment.</i>");
            return;
        }

        // 3. Process Command
        handleCommand(chatId, senderId, username, text, config.getBotToken());
    }

    private void handleCommand(long chatId, long senderId, String username, String text, String botToken) {
        String command = text.split("\\s+")[0].toLowerCase();
        // Strip bot username mention if present, e.g. /info@nexus_bot -> /info
        if (command.contains("@")) {
            command = command.substring(0, command.indexOf('@'));
        }

        log.info("Executing Telegram command '{}' for user '{}' (chatId={})", command, username, chatId);

        Subject subject = Subject.of(String.valueOf(senderId), username, Role.OPERATOR);
        ExecutionContext context = ExecutionContext.create(InvocationSource.TELEGRAM, subject, Duration.ofSeconds(30));

        switch (command) {
            case "/start":
            case "/help":
                sendHelpMessage(chatId, botToken);
                break;

            case "/info":
            case "/system":
                executeAndReply("sys.info", context, null, chatId, botToken);
                break;

            case "/uptime":
                executeAndReply("sys.uptime", context, null, chatId, botToken);
                break;

            case "/cpu":
                executeAndReply("sys.cpu", context, null, chatId, botToken);
                break;

            case "/memory":
            case "/mem":
                executeAndReply("sys.memory", context, null, chatId, botToken);
                break;

            case "/disk":
            case "/disks":
            case "/storage":
                executeAndReply("disk.list", context, null, chatId, botToken);
                break;

            case "/smart":
                executeAndReply("disk.smart", context, null, chatId, botToken);
                break;

            case "/proc":
            case "/process":
            case "/processes":
                executeAndReply("proc.list", context, null, chatId, botToken);
                break;

            case "/net":
            case "/network":
                executeAndReply("net.info", context, null, chatId, botToken);
                break;

            case "/services":
            case "/service":
            case "/svc":
                executeAndReply("svc.list", context, null, chatId, botToken);
                break;

            case "/capabilities":
            case "/caps":
                executeAndReply("cap.list", context, null, chatId, botToken);
                break;

            case "/tailscale":
            case "/tailscale_status":
            case "/vpn":
                executeAndReply("net.tailscale.status", context, null, chatId, botToken);
                break;

            case "/tailscale_up":
                executeAndReply("net.tailscale.up", context, parseTailscaleUpArgs(text), chatId, botToken);
                break;

            case "/tailscale_down":
                executeAndReply("net.tailscale.down", context, null, chatId, botToken);
                break;

            case "/reboot":
            case "/restart":
                handleRebootCommand(text, username, context, chatId, botToken);
                break;

            case "/poweroff":
            case "/shutdown":
                handlePoweroffCommand(text, username, context, chatId, botToken);
                break;

            // ZFS storage
            case "/zfs":
            case "/zpool":
            case "/zfs_pools":
            case "/zfs_list":
                executeAndReply("zfs.pool.list", context, null, chatId, botToken);
                break;

            case "/zfs_status":
                handleZfsStatusCommand(text, context, chatId, botToken);
                break;

            case "/zfs_datasets":
            case "/zfs_ds":
                executeAndReply("zfs.dataset.list", context, null, chatId, botToken);
                break;

            case "/zfs_snapshots":
            case "/zfs_snaps":
                handleZfsSnapshotsCommand(text, context, chatId, botToken);
                break;

            case "/zfs_get":
                handleZfsGetCommand(text, context, chatId, botToken);
                break;

            case "/reload":
            case "/config_reload":
                handleConfigReloadCommand(chatId, botToken);
                break;

            case "/timers":
            case "/timer":
            case "/schedule":
                handleTimerCommand(text, context, chatId, botToken);
                break;

            default:
                if (command.startsWith("/")) {
                    // Try mapping command directly to capability ID (e.g. /sys_info -> sys.info)
                    String capId = command.substring(1).replace('_', '.');
                    CapabilityResult<Object> result = executionEngine.execute(capId, context, null);
                    if (result.isSuccess()) {
                        String replyHtml = formatter.formatResult(result);
                        client.sendMessage(botToken, chatId, replyHtml);
                    } else {
                        String unknownCmdHtml = "❓ <b>Unknown command:</b> <code>" + command + "</code>\n\n" +
                                "Use /help to see all available commands.";
                        client.sendMessage(botToken, chatId, unknownCmdHtml);
                    }
                }
                break;
        }
    }

    private void executeAndReply(String capabilityId, ExecutionContext context, Object input, long chatId, String botToken) {
        CapabilityResult<Object> result = executionEngine.execute(capabilityId, context, input);
        String replyHtml = formatter.formatResult(result);
        client.sendMessage(botToken, chatId, replyHtml);
    }

    private void handleRebootCommand(String text, String username, ExecutionContext context, long chatId, String botToken) {
        String[] parts = text.trim().split("\\s+");
        if (parts.length < 2 || !"confirm".equalsIgnoreCase(parts[1])) {
            String warning = "⚠️ <b>Host Reboot Confirmation Required</b>\n\n" +
                    "Rebooting will terminate all running services and temporarily disconnect this bot.\n\n" +
                    "To proceed, send:\n<code>/reboot confirm</code>";
            client.sendMessage(botToken, chatId, warning);
            return;
        }

        PowerOperationRequest request = new PowerOperationRequest(true, 0, "Telegram reboot requested by @" + username);
        executeAndReply("sys.reboot", context, request, chatId, botToken);
    }

    private void handlePoweroffCommand(String text, String username, ExecutionContext context, long chatId, String botToken) {
        String[] parts = text.trim().split("\\s+");
        if (parts.length < 2 || !"confirm".equalsIgnoreCase(parts[1])) {
            String warning = "⚠️ <b>Host Poweroff Confirmation Required</b>\n\n" +
                    "Powering off will shut down the host machine completely.\n\n" +
                    "To proceed, send:\n<code>/poweroff confirm</code>";
            client.sendMessage(botToken, chatId, warning);
            return;
        }

        PowerOperationRequest request = new PowerOperationRequest(true, 0, "Telegram poweroff requested by @" + username);
        executeAndReply("sys.poweroff", context, request, chatId, botToken);
    }

    private TailscaleOperationRequest parseTailscaleUpArgs(String text) {
        boolean acceptRoutes = text.contains("--accept-routes");
        boolean ssh = text.contains("--ssh");
        boolean reset = text.contains("--reset");
        String exitNode = null;
        for (String part : text.split("\\s+")) {
            if (part.startsWith("--exit-node=")) {
                exitNode = part.substring("--exit-node=".length()).trim();
            }
        }
        return new TailscaleOperationRequest(acceptRoutes, ssh, exitNode, null, null, reset);
    }

    private void handleZfsStatusCommand(String text, ExecutionContext context, long chatId, String botToken) {
        String[] parts = text.trim().split("\\s+");
        String pool = parts.length > 1 ? parts[1].trim() : null;
        executeAndReply("zfs.pool.status", context, pool, chatId, botToken);
    }

    private void handleZfsSnapshotsCommand(String text, ExecutionContext context, long chatId, String botToken) {
        String[] parts = text.trim().split("\\s+");
        String dataset = parts.length > 1 ? parts[1].trim() : null;
        executeAndReply("zfs.snapshot.list", context, dataset, chatId, botToken);
    }

    private void handleZfsGetCommand(String text, ExecutionContext context, long chatId, String botToken) {
        String[] parts = text.trim().split("\\s+");
        if (parts.length < 2) {
            client.sendMessage(botToken, chatId, "⚠️ <i>Please specify the dataset:</i> <code>/zfs_get &lt;dataset&gt;</code>");
            return;
        }
        executeAndReply("zfs.dataset.get", context, new ZfsDatasetDetailRequest(parts[1].trim(), java.util.List.of()), chatId, botToken);
    }

    private void handleConfigReloadCommand(long chatId, String botToken) {
        String resolvedPath = NexusConfigResolver.getResolvedConfigPath();
        java.io.File configFile = NexusConfigResolver.resolveTargetConfigFileForEditing(resolvedPath);
        if (configFile != null && configFile.exists()) {
            boolean success = NexusConfigResolver.reloadPropertiesFromYaml(configFile, properties);
            if (success) {
                reload();
                client.sendMessage(botToken, chatId, "⚙️ <b>Configuration Reloaded</b>\n\n" +
                        "✓ Successfully reloaded configuration from:\n<code>" + configFile.getAbsolutePath() + "</code>\n" +
                        "• Mode: <code>" + properties.getMode() + "</code>\n" +
                        "• Timeout: <code>" + properties.getDefaultTimeout() + "</code>\n" +
                        "• Audit Trail: <code>" + (properties.isAuditEnabled() ? "ENABLED" : "DISABLED") + "</code>");
            } else {
                client.sendMessage(botToken, chatId, "❌ <b>Configuration Reload Failed</b>\n\n" +
                        "Could not parse YAML at:\n<code>" + configFile.getAbsolutePath() + "</code>");
            }
        } else {
            client.sendMessage(botToken, chatId, "ℹ️ <b>Configuration Reload</b>\n\n" +
                    "No external configuration file found on disk (running on embedded defaults).");
        }
    }

    private void handleTimerCommand(String text, ExecutionContext context, long chatId, String botToken) {
        String[] parts = text.trim().split("\\s+");
        if (parts.length > 1) {
            String timerId = parts[1].trim();
            executeAndReply("timer.status", context, new TimerStatusRequest(timerId, TimerScope.ALL), chatId, botToken);
        } else {
            executeAndReply("timer.list", context, new TimerListRequest(TimerScope.ALL), chatId, botToken);
        }
    }

    private void sendHelpMessage(long chatId, String botToken) {
        String helpHtml = "🛡️ <b>Nexus Host Management Agent</b>\n\n" +
                "<b>System & Hardware:</b>\n" +
                "• /info - Host system & hardware specifications\n" +
                "• /uptime - System host and agent uptime\n" +
                "• /cpu - Processor cores & CPU utilization\n" +
                "• /memory - RAM and swap memory utilization\n\n" +
                "<b>Storage & Disks:</b>\n" +
                "• /disk - Mounted filesystems and drive usage\n" +
                "• /smart - Drive SMART health diagnostics\n" +
                "• /zfs - Configured ZFS storage pools\n" +
                "• /zfs_status [pool] - Pool health, scrub & vdev layout\n" +
                "• /zfs_datasets - List ZFS filesystems & volumes\n" +
                "• /zfs_snapshots [dataset] - List ZFS snapshots\n" +
                "• /zfs_get &lt;dataset&gt; - Inspect dataset properties\n\n" +
                "<b>Processes:</b>\n" +
                "• /proc - Top running system processes\n\n" +
                "<b>Network, VPN & Services:</b>\n" +
                "• /net - Network interfaces, IPs & MACs\n" +
                "• /services - Installed system services status\n" +
                "• /timers [id] - List scheduled timers or inspect live timer status\n" +
                "• /tailscale - Tailscale VPN mesh status & peers\n" +
                "• /tailscale_up - Connect and bring Tailscale VPN up\n" +
                "• /tailscale_down - Disconnect Tailscale VPN\n\n" +
                "<b>Privileged Power Management:</b>\n" +
                "• /reboot - Reboot host system (requires <code>/reboot confirm</code>)\n" +
                "• /poweroff - Shut down host system (requires <code>/poweroff confirm</code>)\n\n" +
                "<b>Agent & Configuration:</b>\n" +
                "• /reload - Reload agent configuration from disk\n" +
                "• /caps - List registered agent capabilities\n" +
                "• /help - Display this command overview\n";
        client.sendMessage(botToken, chatId, helpHtml);
    }
}
