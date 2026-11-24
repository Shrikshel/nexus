package com.nexus.adapters.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.capabilities.network.tailscale.dto.TailscaleOperationRequest;
import com.nexus.capabilities.scheduler.timer.dto.TimerListRequest;
import com.nexus.capabilities.scheduler.timer.dto.TimerScope;
import com.nexus.capabilities.scheduler.timer.dto.TimerStatusRequest;
import com.nexus.capabilities.storage.zfs.dto.ZfsDatasetDetailRequest;
import com.nexus.capabilities.system.dto.PowerOperationRequest;
import com.nexus.capabilities.reminder.dispatch.ReminderDispatchService;
import com.nexus.capabilities.reminder.dto.ReminderEvent;
import com.nexus.capabilities.reminder.scheduler.ReminderSchedulerProvider;
import com.nexus.capabilities.reminder.storage.ReminderStorageManager;
import com.nexus.config.NexusConfigResolver;
import com.nexus.config.NexusProperties;
import com.nexus.core.capability.CapabilityResult;
import com.nexus.core.capability.ExecutionContext;
import com.nexus.core.capability.ExecutionEngine;
import com.nexus.core.capability.InvocationSource;
import com.nexus.core.security.Role;
import com.nexus.core.security.Subject;
import com.nexus.platform.backup.BackupCryptoService;
import com.nexus.platform.backup.BackupManager;
import com.nexus.platform.backup.dto.BackupRequest;
import com.nexus.platform.backup.dto.BackupResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
    private final ReminderStorageManager reminderStorageManager;
    private final ReminderSchedulerProvider reminderSchedulerProvider;
    private final ReminderDispatchService reminderDispatchService;
    private final BackupManager backupManager;
    private final BackupCryptoService backupCryptoService;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService pollerExecutor;
    private long lastUpdateId = 0;

    @Autowired
    public TelegramBotAdapter(
            NexusProperties properties,
            TelegramClient client,
            TelegramSecurityFilter securityFilter,
            TelegramFormatter formatter,
            ExecutionEngine executionEngine,
            @Autowired(required = false) ReminderStorageManager reminderStorageManager,
            @Autowired(required = false) ReminderSchedulerProvider reminderSchedulerProvider,
            @Autowired(required = false) ReminderDispatchService reminderDispatchService,
            @Autowired(required = false) BackupManager backupManager,
            @Autowired(required = false) BackupCryptoService backupCryptoService) {
        this.properties = properties;
        this.client = client;
        this.securityFilter = securityFilter;
        this.formatter = formatter;
        this.executionEngine = executionEngine;
        this.reminderStorageManager = reminderStorageManager;
        this.reminderSchedulerProvider = reminderSchedulerProvider;
        this.reminderDispatchService = reminderDispatchService;
        this.backupManager = backupManager;
        this.backupCryptoService = backupCryptoService;
    }

    public TelegramBotAdapter(
            NexusProperties properties,
            TelegramClient client,
            TelegramSecurityFilter securityFilter,
            TelegramFormatter formatter,
            ExecutionEngine executionEngine,
            ReminderStorageManager reminderStorageManager,
            ReminderSchedulerProvider reminderSchedulerProvider,
            ReminderDispatchService reminderDispatchService) {
        this(properties, client, securityFilter, formatter, executionEngine, reminderStorageManager, reminderSchedulerProvider, reminderDispatchService, null, null);
    }

    public TelegramBotAdapter(
            NexusProperties properties,
            TelegramClient client,
            TelegramSecurityFilter securityFilter,
            TelegramFormatter formatter,
            ExecutionEngine executionEngine,
            ReminderStorageManager reminderStorageManager) {
        this(properties, client, securityFilter, formatter, executionEngine, reminderStorageManager, null, null, null, null);
    }

    public TelegramBotAdapter(
            NexusProperties properties,
            TelegramClient client,
            TelegramSecurityFilter securityFilter,
            TelegramFormatter formatter,
            ExecutionEngine executionEngine) {
        this(properties, client, securityFilter, formatter, executionEngine, null, null, null, null, null);
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

            case "/reminders":
            case "/reminder":
                handleReminderCommand(text, chatId, botToken);
                break;

            case "/reminders_next":
            case "/reminder_next":
                sendNextReminders(chatId, botToken);
                break;

            case "/reminders_list":
            case "/reminder_list":
                sendReminderList(chatId, botToken, false);
                break;

            case "/backup":
                handleBackupCommand(text, chatId, botToken);
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

    private void handleReminderCommand(String text, long chatId, String botToken) {
        if (reminderStorageManager == null) {
            client.sendMessage(botToken, chatId, "⚠️ <i>Reminder subsystem is not initialized or unavailable.</i>");
            return;
        }

        String[] parts = text.trim().split("\\s+");
        if (parts.length > 1) {
            String sub = parts[1].trim();

            if ("help".equalsIgnoreCase(sub) || "--help".equalsIgnoreCase(sub) || "-h".equalsIgnoreCase(sub)) {
                sendReminderHelp(chatId, botToken);
                return;
            }

            if ("list".equalsIgnoreCase(sub) || "ls".equalsIgnoreCase(sub)) {
                sendReminderList(chatId, botToken, false);
                return;
            }

            if ("all".equalsIgnoreCase(sub)) {
                sendReminderList(chatId, botToken, true);
                return;
            }

            if ("next".equalsIgnoreCase(sub)) {
                sendNextReminders(chatId, botToken);
                return;
            }

            if ("show".equalsIgnoreCase(sub)) {
                if (parts.length > 2) {
                    sendReminderDetail(parts[2].trim(), chatId, botToken);
                } else {
                    client.sendMessage(botToken, chatId, "⚠️ <i>Please specify reminder ID:</i> <code>/reminders show EVT-001</code>");
                }
                return;
            }

            if ("test".equalsIgnoreCase(sub)) {
                if (parts.length > 2) {
                    sendTestReminder(parts[2].trim(), chatId, botToken);
                } else {
                    client.sendMessage(botToken, chatId, "⚠️ <i>Please specify reminder ID to test:</i> <code>/reminders test EVT-001</code>");
                }
                return;
            }

            if ("delete".equalsIgnoreCase(sub) || "rm".equalsIgnoreCase(sub)) {
                if (parts.length > 2) {
                    deleteReminder(parts[2].trim(), chatId, botToken);
                } else {
                    client.sendMessage(botToken, chatId, "⚠️ <i>Please specify reminder ID to delete:</i> <code>/reminders delete EVT-001</code>");
                }
                return;
            }

            // Direct ID lookup: /reminders EVT-001
            sendReminderDetail(sub, chatId, botToken);
            return;
        }

        // Default: list active reminders
        sendReminderList(chatId, botToken, false);
    }

    private void sendReminderList(long chatId, String botToken, boolean showAll) {
        if (reminderStorageManager == null) {
            client.sendMessage(botToken, chatId, "⚠️ <i>Reminder subsystem is unavailable.</i>");
            return;
        }
        List<ReminderEvent> all = reminderStorageManager.loadAll();
        if (showAll) {
            String reply = formatter.formatReminderList(all);
            client.sendMessage(botToken, chatId, reply);
            return;
        }
        List<ReminderEvent> active = all.stream()
                .filter(e -> "ACTIVE".equalsIgnoreCase(e.state().status()))
                .toList();
        String reply = formatter.formatReminderList(active.isEmpty() && !all.isEmpty() ? all : active);
        client.sendMessage(botToken, chatId, reply);
    }

    private void sendNextReminders(long chatId, String botToken) {
        if (reminderStorageManager == null) {
            client.sendMessage(botToken, chatId, "⚠️ <i>Reminder subsystem is unavailable.</i>");
            return;
        }
        List<ReminderEvent> all = reminderStorageManager.loadAll();
        Instant earliest = null;
        for (ReminderEvent e : all) {
            if ("ACTIVE".equalsIgnoreCase(e.state().status()) && e.state().nextTriggerAt() != null) {
                if (earliest == null || e.state().nextTriggerAt().isBefore(earliest)) {
                    earliest = e.state().nextTriggerAt();
                }
            }
        }

        if (earliest == null) {
            client.sendMessage(botToken, chatId, formatter.formatNextReminders(List.of()));
            return;
        }

        Instant finalEarliest = earliest;
        List<ReminderEvent> matching = all.stream()
                .filter(e -> "ACTIVE".equalsIgnoreCase(e.state().status()) && finalEarliest.equals(e.state().nextTriggerAt()))
                .toList();

        String reply = formatter.formatNextReminders(matching);
        client.sendMessage(botToken, chatId, reply);
    }

    private void sendReminderDetail(String identifier, long chatId, String botToken) {
        if (reminderStorageManager == null) {
            client.sendMessage(botToken, chatId, "⚠️ <i>Reminder subsystem is unavailable.</i>");
            return;
        }
        Optional<ReminderEvent> eventOpt = reminderStorageManager.findByIdOrUuid(identifier);
        if (eventOpt.isPresent()) {
            String reply = formatter.formatReminderDetail(eventOpt.get());
            client.sendMessage(botToken, chatId, reply);
        } else {
            client.sendMessage(botToken, chatId, "⚠️ <i>Reminder not found:</i> <code>" + identifier + "</code>\n\nUse /reminders to view active reminders.");
        }
    }

    private void sendTestReminder(String identifier, long chatId, String botToken) {
        if (reminderDispatchService == null) {
            client.sendMessage(botToken, chatId, "⚠️ <i>Reminder dispatch service is unavailable.</i>");
            return;
        }
        boolean ok = reminderDispatchService.testDispatch(identifier);
        if (ok) {
            client.sendMessage(botToken, chatId, "✅ <b>Test Alert Dispatched:</b> Successfully sent test notification for <code>" + identifier + "</code>.");
        } else {
            client.sendMessage(botToken, chatId, "❌ <b>Test Alert Failed:</b> Reminder <code>" + identifier + "</code> not found or notification dispatch failed.");
        }
    }

    private void deleteReminder(String identifier, long chatId, String botToken) {
        if (reminderStorageManager == null) {
            client.sendMessage(botToken, chatId, "⚠️ <i>Reminder subsystem is unavailable.</i>");
            return;
        }
        Optional<ReminderEvent> eventOpt = reminderStorageManager.findByIdOrUuid(identifier);
        if (eventOpt.isEmpty()) {
            client.sendMessage(botToken, chatId, "⚠️ <i>Reminder not found to delete:</i> <code>" + identifier + "</code>");
            return;
        }
        ReminderEvent event = eventOpt.get();
        boolean deleted = reminderStorageManager.deleteByIdOrUuid(event.id());
        if (deleted) {
            if (reminderSchedulerProvider != null) {
                reminderSchedulerProvider.disarmTimer(event.id());
            }
            client.sendMessage(botToken, chatId, "🗑️ <b>Deleted Reminder:</b> <code>" + event.id() + "</code> (" + event.title() + ") deleted and associated timers disarmed.");
        } else {
            client.sendMessage(botToken, chatId, "❌ <i>Could not delete reminder:</i> <code>" + identifier + "</code>");
        }
    }

    private void sendReminderHelp(long chatId, String botToken) {
        String helpHtml = "📅 <b>Nexus Scheduled Reminders Management</b>\n\n" +
                "• <code>/reminders</code> - List all active scheduled reminders\n" +
                "• <code>/reminders all</code> - List all reminders (including completed/paused)\n" +
                "• <code>/reminders next</code> - View immediate upcoming reminder countdown\n" +
                "• <code>/reminders &lt;id&gt;</code> - View stages and configuration for reminder\n" +
                "• <code>/reminders show &lt;id&gt;</code> - Detailed breakdown of stages and triggers\n" +
                "• <code>/reminders test &lt;id&gt;</code> - Dispatch a test notification alert right now\n" +
                "• <code>/reminders delete &lt;id&gt;</code> - Delete reminder and disarm systemd timers\n\n" +
                "<i>Tip: You can also use /reminders_next or /reminders_list as quick shortcuts.</i>";
        client.sendMessage(botToken, chatId, helpHtml);
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
                "<b>Automation & Reminders:</b>\n" +
                "• /timers [id] - List scheduled timers or inspect live timer status\n" +
                "• /reminders - List scheduled reminders and upcoming stages\n" +
                "• /reminders all - List all reminders (active & completed)\n" +
                "• /reminders next - Immediate next reminder & countdown\n" +
                "• /reminders &lt;id&gt; - Inspect detailed reminder configuration & stages\n" +
                "• /reminders test &lt;id&gt; - Dispatch test notification alert\n" +
                "• /reminders delete &lt;id&gt; - Delete reminder and disarm timers\n" +
                "• /tailscale - Tailscale VPN mesh status & peers\n" +
                "• /tailscale_up - Connect and bring Tailscale VPN up\n" +
                "• /tailscale_down - Disconnect Tailscale VPN\n\n" +
                "<b>Privileged Power Management:</b>\n" +
                "• /reboot - Reboot host system (requires <code>/reboot confirm</code>)\n" +
                "• /poweroff - Shut down host system (requires <code>/poweroff confirm</code>)\n\n" +
                "<b>Agent & Configuration:</b>\n" +
                "• /backup [password] - Export encrypted full host backup & download via Telegram\n" +
                "• /reload - Reload agent configuration from disk\n" +
                "• /caps - List registered agent capabilities\n" +
                "• /help - Display this command overview\n";
        client.sendMessage(botToken, chatId, helpHtml);
    }

    private void handleBackupCommand(String text, long chatId, String botToken) {
        if (backupManager == null) {
            client.sendMessage(botToken, chatId, "⚠️ <i>Backup subsystem is unavailable.</i>");
            return;
        }

        String[] parts = text.trim().split("\\s+");
        String password = null;
        if (parts.length > 1 && !parts[1].isBlank()) {
            password = parts[1].trim();
        } else if (properties.getBackup() != null && properties.getBackup().getPassword() != null && !properties.getBackup().getPassword().isBlank()) {
            password = properties.getBackup().getPassword();
        } else if (backupCryptoService != null) {
            password = backupCryptoService.generateSecurePassword(16);
        } else {
            password = "Nexus" + System.currentTimeMillis();
        }

        client.sendMessage(botToken, chatId, "⏳ <i>Generating encrypted full host backup archive...</i>");

        Path tempBackup = null;
        try {
            tempBackup = Files.createTempFile("nexus-backup-telegram-", ".zip.enc");
            BackupRequest request = BackupRequest.builder()
                    .outputPath(tempBackup)
                    .encrypt(true)
                    .password(password.toCharArray())
                    .includeSystem(true)
                    .includeUser(true)
                    .overwrite(true)
                    .build();

            BackupResult result = backupManager.createBackup(request);
            if (!result.success()) {
                client.sendMessage(botToken, chatId, "❌ <b>Backup Failed:</b> " + result.message());
                return;
            }

            int remindersCount = 0;
            int timersCount = 0;
            if (result.manifest() != null && result.manifest().stats() != null) {
                var stats = result.manifest().stats();
                remindersCount = ((Number) stats.getOrDefault("systemReminders", 0)).intValue() +
                        ((Number) stats.getOrDefault("userReminders", 0)).intValue();
                timersCount = ((Number) stats.getOrDefault("systemTimers", 0)).intValue() +
                        ((Number) stats.getOrDefault("userTimers", 0)).intValue();
            }

            String caption = "📦 <b>Nexus Full Host Backup</b>\n\n" +
                    "• <b>Archive:</b> <code>" + result.archivePath().getFileName() + "</code>\n" +
                    "• <b>Size:</b> " + BackupResult.formatSize(result.archiveSizeBytes()) + "\n" +
                    "• <b>Encryption:</b> AES-256-GCM\n" +
                    "• <b>Files Backed Up:</b> " + result.totalFiles() + " (" +
                    remindersCount + " reminders, " +
                    timersCount + " timers)\n" +
                    "• <b>Decryption Key:</b> <code>" + password + "</code>\n\n" +
                    "<i>Restore Instructions:</i>\n" +
                    "<code>nexus backup inspect " + result.archivePath().getFileName() + " -p " + password + "</code>\n" +
                    "<code>sudo nexus backup restore " + result.archivePath().getFileName() + " -p " + password + " --dry-run</code>";

            TelegramClient.TelegramSendResponse sendResp = client.sendDocument(
                    botToken,
                    String.valueOf(chatId),
                    result.archivePath(),
                    caption,
                    "HTML",
                    false
            );

            if (!sendResp.success()) {
                client.sendMessage(botToken, chatId, "❌ <b>Backup Upload Failed:</b> " + sendResp.error());
            }
        } catch (Exception e) {
            log.error("Failed to generate or upload Telegram backup", e);
            client.sendMessage(botToken, chatId, "❌ <b>Backup Generation Failed:</b> " + e.getMessage());
        } finally {
            if (tempBackup != null) {
                try {
                    Files.deleteIfExists(tempBackup);
                } catch (IOException ignored) {}
            }
        }
    }
}
