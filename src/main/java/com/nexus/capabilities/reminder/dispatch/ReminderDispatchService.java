package com.nexus.capabilities.reminder.dispatch;

import com.nexus.adapters.telegram.TelegramClient;
import com.nexus.capabilities.reminder.dto.*;
import com.nexus.capabilities.reminder.scheduler.ReminderSchedulerProvider;
import com.nexus.capabilities.reminder.storage.ReminderStorageManager;
import com.nexus.config.NexusProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Core engine for dispatching reminder notifications, synthesizing tailored messages,
 * updating stage progression, and performing automated recurrence rollover.
 */
@Service
public class ReminderDispatchService {

    private static final Logger log = LoggerFactory.getLogger(ReminderDispatchService.class);

    private final ReminderStorageManager storageManager;
    private final ReminderSchedulerProvider schedulerProvider;
    private final TelegramClient telegramClient;
    private final NexusProperties properties;

    public ReminderDispatchService(
            ReminderStorageManager storageManager,
            ReminderSchedulerProvider schedulerProvider,
            TelegramClient telegramClient,
            NexusProperties properties) {
        this.storageManager = storageManager;
        this.schedulerProvider = schedulerProvider;
        this.telegramClient = telegramClient;
        this.properties = properties;
    }

    public boolean dispatch(String identifier) {
        Optional<ReminderEvent> eventOpt = storageManager.findByIdOrUuid(identifier);
        if (eventOpt.isEmpty()) {
            log.warn("Reminder dispatch failed: event '{}' not found", identifier);
            return false;
        }

        ReminderEvent event = eventOpt.get();
        if ("COMPLETED".equalsIgnoreCase(event.state().status()) || "PAUSED".equalsIgnoreCase(event.state().status())) {
            log.info("Reminder {} is {}. Disarming timer and skipping dispatch.", event.id(), event.state().status());
            schedulerProvider.disarmTimer(event.id());
            return true;
        }

        // 1. Find stage to deliver: prioritize latest pending stage whose trigger time has arrived
        Instant now = Instant.now();
        List<ReminderStage> updatedStages = new ArrayList<>(event.stages());
        ReminderStage stageToDeliver = null;
        int stageIdx = -1;

        for (int i = 0; i < updatedStages.size(); i++) {
            ReminderStage s = updatedStages.get(i);
            if (s.status() == ReminderStageStatus.PENDING && s.triggerAt() != null) {
                if (!s.triggerAt().isAfter(now.plusSeconds(60))) {
                    stageToDeliver = s;
                    stageIdx = i;
                }
            }
        }

        // If no pending stage has arrived yet, fallback to earliest pending stage (e.g. manual dispatch / test)
        if (stageToDeliver == null) {
            for (int i = 0; i < updatedStages.size(); i++) {
                ReminderStage s = updatedStages.get(i);
                if (s.status() == ReminderStageStatus.PENDING) {
                    stageToDeliver = s;
                    stageIdx = i;
                    break;
                }
            }
        }

        if (stageToDeliver == null) {
            log.info("All stages already delivered for reminder {}. Triggering cycle rollover check.", event.id());
            handleCycleRollover(event);
            return true;
        }

        // Auto-skip any overdue pending stages that were scheduled before stageToDeliver
        for (int i = 0; i < stageIdx; i++) {
            ReminderStage s = updatedStages.get(i);
            if (s.status() == ReminderStageStatus.PENDING) {
                log.info("Auto-skipping overdue stage '{}' (scheduled at {}) for reminder {}",
                        s.stage(), s.triggerAt(), event.id());
                updatedStages.set(i, s.withStatus(ReminderStageStatus.SKIPPED, null));
            }
        }

        // 2. Synthesize message & send notification
        String messageHtml = formatStageMessage(event, stageToDeliver, false);
        boolean sent = sendTelegramNotification(messageHtml);
        if (!sent) {
            log.warn("Failed to send Telegram notification for reminder {} (stage={})", event.id(), stageToDeliver.stage());
        }

        // 3. Mark stage as DELIVERED
        ReminderStage delivered = stageToDeliver.withStatus(ReminderStageStatus.DELIVERED, now);
        updatedStages.set(stageIdx, delivered);

        // 4. Determine next stage or rollover
        ReminderStage nextPending = null;
        for (int i = stageIdx + 1; i < updatedStages.size(); i++) {
            if (updatedStages.get(i).status() == ReminderStageStatus.PENDING) {
                nextPending = updatedStages.get(i);
                break;
            }
        }

        if (nextPending != null) {
            // More stages left in this cycle
            ReminderEventState newState = event.state().withProgress(
                    delivered.stage(), nextPending.stage(), nextPending.triggerAt()
            );
            ReminderEvent updatedEvent = event.withUpdatedState(newState, updatedStages);
            storageManager.saveEvent(updatedEvent);
            schedulerProvider.armTimer(updatedEvent);
            log.info("Delivered stage '{}' for {}. Next stage: '{}' at {}",
                    delivered.stage(), event.id(), nextPending.stage(), nextPending.triggerAt());
        } else {
            // Final stage of cycle delivered!
            ReminderEvent updatedEvent = event.withUpdatedState(
                    event.state().withProgress(delivered.stage(), null, null),
                    updatedStages
            );
            storageManager.saveEvent(updatedEvent);
            handleCycleRollover(updatedEvent);
        }

        return true;
    }

    public boolean testDispatch(String identifier) {
        Optional<ReminderEvent> eventOpt = storageManager.findByIdOrUuid(identifier);
        if (eventOpt.isEmpty()) {
            return false;
        }
        ReminderEvent event = eventOpt.get();
        ReminderStage previewStage = event.stages().isEmpty()
                ? new ReminderStage("0m", "PT0S", Instant.now(), null, null, ReminderStageStatus.PENDING)
                : event.stages().get(event.stages().size() - 1);

        String messageHtml = formatStageMessage(event, previewStage, true);
        return sendTelegramNotification(messageHtml);
    }

    private void handleCycleRollover(ReminderEvent event) {
        List<String> deliveredStages = event.stages().stream()
                .filter(s -> s.status() == ReminderStageStatus.DELIVERED)
                .map(ReminderStage::stage)
                .toList();

        ReminderHistoryEntry historyEntry = new ReminderHistoryEntry(
                event.state().activeCycle(),
                event.targetDate(),
                Instant.now(),
                deliveredStages
        );

        if (event.recurrence().isNone()) {
            // One-off event completes
            ReminderEventState completedState = event.state().withCompleted();
            List<ReminderHistoryEntry> updatedHistory = new ArrayList<>(event.history());
            updatedHistory.add(historyEntry);

            ReminderEvent completedEvent = new ReminderEvent(
                    event.uuid(), event.id(), event.title(), event.type(), event.category(),
                    event.targetDate(), event.targetTime(), event.timezone(), event.recurrence(),
                    event.metadata(), event.stages(), completedState, updatedHistory
            );

            storageManager.saveEvent(completedEvent);
            schedulerProvider.disarmTimer(event.id());
            log.info("One-off reminder {} marked COMPLETED. Timers disarmed.", event.id());
        } else {
            // Recurrence rollover!
            LocalDateTime nextDateTime = event.recurrence().nextOccurrence(event.targetDate(), event.targetTime(), event.metadata());
            LocalDate nextDate = nextDateTime.toLocalDate();
            LocalTime nextTime = nextDateTime.toLocalTime();

            List<String> stageTokens = event.stages().stream().map(ReminderStage::stage).toList();

            Map<String, String> customMsgs = new HashMap<>();
            for (ReminderStage s : event.stages()) {
                if (s.customMessage() != null) customMsgs.put(s.stage(), s.customMessage());
            }

            List<ReminderStage> nextStages = StageCalculator.buildStages(
                    stageTokens, nextDate, nextTime, event.timezone(), customMsgs
            );

            String nextCycle = event.recurrence().formatCycle(nextDate, nextTime);

            ReminderStage firstStage = nextStages.isEmpty() ? null : nextStages.get(0);
            ReminderEventState nextState = event.state().withNewCycle(
                    nextCycle,
                    firstStage != null ? firstStage.stage() : null,
                    firstStage != null ? firstStage.triggerAt() : null
            );

            ReminderEvent rolledOver = event.withArchivedCycle(historyEntry, nextDate, nextTime, nextStages, nextState);
            storageManager.saveEvent(rolledOver);
            schedulerProvider.armTimer(rolledOver);
            log.info("Rolled over recurring reminder {} to new date/time: {} {} (cycle: {})",
                    event.id(), nextDate, nextTime, nextCycle);
        }
    }

    public String formatStageMessage(ReminderEvent event, ReminderStage stage, boolean isTest) {
        StringBuilder sb = new StringBuilder();

        if (isTest) {
            sb.append("🧪 <b>[TEST / PREVIEW NOTIFICATION]</b>\n\n");
        }

        // Custom Message Override
        if (stage.customMessage() != null && !stage.customMessage().isBlank()) {
            sb.append(stage.customMessage());
            return sb.toString();
        }

        boolean isDayOf = "0m".equalsIgnoreCase(stage.stage()) || "0".equals(stage.stage());
        String eventDateStr = event.targetDate().format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy"));

        switch (event.type()) {
            case BIRTHDAY -> {
                if (isDayOf) {
                    Integer birthYear = extractBirthYear(event.metadata());
                    if (birthYear != null && birthYear > 1900) {
                        int age = event.targetDate().getYear() - birthYear;
                        sb.append(String.format("🎉 <b>Happy Birthday!</b>\n\nToday is <b>%s</b>! Celebrating <b>%d years</b> today! 🎂", event.title(), age));
                    } else {
                        sb.append(String.format("🎉 <b>Happy Birthday!</b>\n\nToday is <b>%s</b>! Wish them a wonderful day! 🎂", event.title()));
                    }
                } else {
                    sb.append(String.format("🗓 <b>Birthday Reminder</b>\n\n<b>%s</b> is coming up in <b>%s</b> (%s)!",
                            event.title(), formatFriendlyStage(stage.stage()), eventDateStr));
                }
            }
            case ANNIVERSARY -> {
                if (isDayOf) {
                    sb.append(String.format("🥂 <b>Happy Anniversary!</b>\n\nToday is <b>%s</b>! Celebrating milestone memories! 💐", event.title()));
                } else {
                    sb.append(String.format("🗓 <b>Anniversary Reminder</b>\n\n<b>%s</b> is in <b>%s</b> (%s).",
                            event.title(), formatFriendlyStage(stage.stage()), eventDateStr));
                }
            }
            case BILL -> {
                if (isDayOf) {
                    sb.append(String.format("⚠️ <b>Payment Due Today!</b>\n\n<b>%s</b> is due for payment today (%s).", event.title(), eventDateStr));
                } else {
                    sb.append(String.format("💳 <b>Bill Payment Reminder</b>\n\n<b>%s</b> is due in <b>%s</b> (%s).",
                            event.title(), formatFriendlyStage(stage.stage()), eventDateStr));
                }
            }
            case MEETING -> {
                if (isDayOf) {
                    sb.append(String.format("📅 <b>Meeting Reminder</b>\n\n<b>%s</b> is starting now (%s)!", event.title(), event.targetTime()));
                } else {
                    sb.append(String.format("📅 <b>Upcoming Meeting</b>\n\n<b>%s</b> starts in <b>%s</b> (%s at %s).",
                            event.title(), formatFriendlyStage(stage.stage()), eventDateStr, event.targetTime()));
                }
            }
            default -> {
                if (isDayOf) {
                    sb.append(String.format("🔔 <b>Event Today!</b>\n\nToday is <b>%s</b> (%s).", event.title(), eventDateStr));
                } else {
                    sb.append(String.format("🔔 <b>Upcoming Event Reminder</b>\n\n<b>%s</b> is in <b>%s</b> (%s).",
                            event.title(), formatFriendlyStage(stage.stage()), eventDateStr));
                }
            }
        }

        if (event.metadata().containsKey("notes") && event.metadata().get("notes") != null) {
            sb.append("\n\n<i>Note: ").append(event.metadata().get("notes")).append("</i>");
        }

        return sb.toString();
    }

    private static String formatFriendlyStage(String stage) {
        if (stage == null) return "";
        if (stage.endsWith("d")) return stage.replace("d", " days");
        if (stage.endsWith("h")) return stage.replace("h", " hours");
        if (stage.endsWith("m")) return stage.replace("m", " minutes");
        return stage;
    }

    private static Integer extractBirthYear(Map<String, Object> metadata) {
        if (metadata == null) return null;
        Object val = metadata.get("birthYear");
        if (val instanceof Number n) {
            return n.intValue();
        } else if (val instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private boolean sendTelegramNotification(String html) {
        NexusProperties.TelegramConfig tg = properties.getTelegram();
        if (tg == null || !tg.isEnabled() || tg.getBotToken() == null || tg.getBotToken().isBlank()) {
            log.warn("Telegram notifications disabled in configuration. Skipping send.");
            return false;
        }
        long chatId = tg.getAllowedChatId();
        if (chatId == 0) {
            log.warn("Telegram allowedChatId is 0. Skipping reminder notification.");
            return false;
        }
        return telegramClient.sendMessage(tg.getBotToken(), chatId, html);
    }
}
