package com.nexus.capabilities.reminder.storage;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nexus.capabilities.reminder.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-reliability, single-file atomic storage manager for reminders (~/.nexus/data/reminders.json).
 * Guarantees zero corruption during sudden powercuts via .tmp write -> fsync -> ATOMIC_MOVE.
 */
@Component
public class ReminderStorageManager {

    private static final Logger log = LoggerFactory.getLogger(ReminderStorageManager.class);
    private static final Pattern EVT_NUMBER_PATTERN = Pattern.compile("^EVT-(\\d+)$", Pattern.CASE_INSENSITIVE);

    private final ObjectMapper objectMapper;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private Path customStoragePath;

    public ReminderStorageManager() {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    public void setCustomStoragePath(Path path) {
        this.customStoragePath = path;
    }

    public Path resolveStoragePath() {
        if (customStoragePath != null) {
            return customStoragePath;
        }

        String user = System.getProperty("user.name", "");
        if ("root".equalsIgnoreCase(user)) {
            return Path.of("/root/.nexus/data/reminders.json");
        }

        Path rootPath = Path.of("/root/.nexus/data/reminders.json");
        if (Files.isReadable(rootPath)) {
            return rootPath;
        }

        return Path.of(System.getProperty("user.home"), ".nexus", "data", "reminders.json");
    }

    public List<ReminderEvent> loadAll() {
        lock.readLock().lock();
        try {
            Path file = resolveStoragePath();
            if (!Files.exists(file) || Files.size(file) == 0) {
                return new ArrayList<>();
            }
            JsonNode root = objectMapper.readTree(file.toFile());
            JsonNode eventsNode = root.path("events");
            if (!eventsNode.isArray()) {
                return new ArrayList<>();
            }
            List<ReminderEvent> list = new ArrayList<>();
            for (JsonNode item : eventsNode) {
                try {
                    ReminderEvent event = objectMapper.treeToValue(item, ReminderEvent.class);
                    if (event != null) {
                        list.add(event);
                    }
                } catch (Exception e) {
                    log.warn("Skipping unparseable reminder event: {}", e.getMessage());
                }
            }
            return list;
        } catch (Exception e) {
            log.error("Failed to load reminders from {}: {}", resolveStoragePath(), e.getMessage());
            return new ArrayList<>();
        } finally {
            lock.readLock().unlock();
        }
    }

    public Optional<ReminderEvent> findByIdOrUuid(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        String clean = identifier.trim();
        List<ReminderEvent> all = loadAll();

        // 1. Exact match on friendly ID (EVT-001)
        for (ReminderEvent e : all) {
            if (clean.equalsIgnoreCase(e.id())) {
                return Optional.of(e);
            }
        }

        // 2. Exact match on UUID
        for (ReminderEvent e : all) {
            if (clean.equalsIgnoreCase(e.uuid())) {
                return Optional.of(e);
            }
        }

        // 3. Prefix match on UUID (minimum 4 characters)
        if (clean.length() >= 4) {
            for (ReminderEvent e : all) {
                if (e.uuid() != null && e.uuid().toLowerCase(Locale.ROOT).startsWith(clean.toLowerCase(Locale.ROOT))) {
                    return Optional.of(e);
                }
            }
        }

        // 4. Exact title match (case-insensitive)
        for (ReminderEvent e : all) {
            if (clean.equalsIgnoreCase(e.title())) {
                return Optional.of(e);
            }
        }

        return Optional.empty();
    }

    public ReminderEvent createReminder(ReminderCreateRequest req) {
        lock.writeLock().lock();
        try {
            List<ReminderEvent> all = loadAll();

            // 1. Determine friendly ID
            String friendlyId = req.id();
            if (friendlyId == null || friendlyId.isBlank()) {
                friendlyId = generateNextFriendlyId(all);
            } else {
                friendlyId = friendlyId.trim().toUpperCase(Locale.ROOT);
            }

            // 2. Generate immutable System UUID
            String uuid = UUID.randomUUID().toString();

            // 3. Determine Timezone
            ZoneId zone = req.timezone() != null ? req.timezone() : ZoneId.systemDefault();
            ReminderRecurrence recurrence = req.recurrence() != null ? req.recurrence() : ReminderRecurrence.NONE;

            // 4. Anchor metadata (guarantees leap-year and month-end consistency)
            Map<String, Object> meta = new LinkedHashMap<>(req.metadata() != null ? req.metadata() : Collections.emptyMap());
            meta.putIfAbsent("anchorDate", req.targetDate().toString());
            meta.putIfAbsent("anchorTime", (req.targetTime() != null ? req.targetTime() : LocalTime.of(9, 0)).toString());
            meta.putIfAbsent("anchorDayOfMonth", req.targetDate().getDayOfMonth());
            meta.putIfAbsent("anchorMonth", req.targetDate().getMonthValue());

            // 5. Build stages
            List<String> stageTokens = req.stages();
            if (stageTokens == null || stageTokens.isEmpty()) {
                if (recurrence.isSubDay()) {
                    stageTokens = List.of("0m");
                } else {
                    stageTokens = StageCalculator.getDefaultStages(req.type());
                }
            }

            List<ReminderStage> stages = StageCalculator.buildStages(
                    stageTokens, req.targetDate(), req.targetTime(), zone, req.customStageMessages()
            );

            // 6. Initial State & Cycle
            String cycle;
            if (recurrence.isSubDay()) {
                cycle = req.targetDate() + "T" + (req.targetTime() != null ? req.targetTime().toString().substring(0, 5) : "09:00");
            } else if (recurrence.unit() == ReminderRecurrence.Unit.DAYS || recurrence.unit() == ReminderRecurrence.Unit.WEEKS) {
                cycle = req.targetDate().toString();
            } else {
                cycle = String.valueOf(req.targetDate().getYear());
            }
            String initialNextStage = stages.isEmpty() ? null : stages.get(0).stage();
            Instant initialNextTrigger = stages.isEmpty() ? null : stages.get(0).triggerAt();
            ReminderEventState state = ReminderEventState.initial(cycle, initialNextStage, initialNextTrigger);

            ReminderEvent newEvent = new ReminderEvent(
                    uuid,
                    friendlyId,
                    req.title(),
                    req.type() != null ? req.type() : ReminderType.EVENT,
                    req.category() != null ? req.category() : "GENERAL",
                    req.targetDate(),
                    req.targetTime(),
                    zone,
                    recurrence,
                    meta,
                    stages,
                    state,
                    Collections.emptyList()
            );

            all.add(newEvent);
            saveAllAtomic(all);
            return newEvent;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Optional<ReminderEvent> updateReminder(ReminderUpdateRequest req) {
        lock.writeLock().lock();
        try {
            List<ReminderEvent> all = loadAll();
            Optional<ReminderEvent> existingOpt = findByIdOrUuid(req.targetIdentifier());
            if (existingOpt.isEmpty()) {
                return Optional.empty();
            }

            ReminderEvent existing = existingOpt.get();
            int index = -1;
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).uuid().equals(existing.uuid())) {
                    index = i;
                    break;
                }
            }

            String title = req.newTitle() != null ? req.newTitle() : existing.title();
            ReminderType type = req.newType() != null ? req.newType() : existing.type();
            String category = req.newCategory() != null ? req.newCategory() : existing.category();
            java.time.LocalDate date = req.newTargetDate() != null ? req.newTargetDate() : existing.targetDate();
            java.time.LocalTime time = req.newTargetTime() != null ? req.newTargetTime() : existing.targetTime();
            ZoneId zone = req.newTimezone() != null ? req.newTimezone() : existing.timezone();
            ReminderRecurrence recurrence = req.newRecurrence() != null ? req.newRecurrence() : existing.recurrence();

            Map<String, Object> meta = new LinkedHashMap<>(existing.metadata());
            if (req.updateMetadata() != null) {
                meta.putAll(req.updateMetadata());
            }

            // Stages
            List<ReminderStage> updatedStages;
            boolean dateOrTimeChanged = (req.newTargetDate() != null && !req.newTargetDate().equals(existing.targetDate()))
                    || (req.newTargetTime() != null && !req.newTargetTime().equals(existing.targetTime()))
                    || (req.newTimezone() != null && !req.newTimezone().equals(existing.timezone()));

            if (req.newStages() != null || dateOrTimeChanged) {
                List<String> tokens = req.newStages() != null ? req.newStages() : existing.stages().stream().map(ReminderStage::stage).toList();
                Map<String, String> customMsgs = new HashMap<>();
                for (ReminderStage s : existing.stages()) {
                    if (s.customMessage() != null) customMsgs.put(s.stage(), s.customMessage());
                }
                if (req.updateStageMessages() != null) {
                    customMsgs.putAll(req.updateStageMessages());
                }
                updatedStages = StageCalculator.buildStages(tokens, date, time, zone, customMsgs);
            } else if (req.updateStageMessages() != null) {
                updatedStages = new ArrayList<>();
                for (ReminderStage s : existing.stages()) {
                    String updatedMsg = req.updateStageMessages().getOrDefault(s.stage(), s.customMessage());
                    updatedStages.add(new ReminderStage(s.stage(), s.offset(), s.triggerAt(), updatedMsg, s.deliveredAt(), s.status()));
                }
            } else {
                updatedStages = existing.stages();
            }

            // Update state
            ReminderStage nextPending = updatedStages.stream()
                    .filter(s -> s.status() == ReminderStageStatus.PENDING)
                    .findFirst()
                    .orElse(null);

            ReminderEventState newState = new ReminderEventState(
                    String.valueOf(date.getYear()),
                    "ACTIVE",
                    existing.state().lastDispatchedStage(),
                    nextPending != null ? nextPending.stage() : null,
                    nextPending != null ? nextPending.triggerAt() : null
            );

            ReminderEvent updated = new ReminderEvent(
                    existing.uuid(),
                    existing.id(),
                    title,
                    type,
                    category,
                    date,
                    time,
                    zone,
                    recurrence,
                    meta,
                    updatedStages,
                    newState,
                    existing.history()
            );

            all.set(index, updated);
            saveAllAtomic(all);
            return Optional.of(updated);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean saveEvent(ReminderEvent updatedEvent) {
        lock.writeLock().lock();
        try {
            List<ReminderEvent> all = loadAll();
            boolean found = false;
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).uuid().equals(updatedEvent.uuid())) {
                    all.set(i, updatedEvent);
                    found = true;
                    break;
                }
            }
            if (!found) {
                all.add(updatedEvent);
            }
            saveAllAtomic(all);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean deleteByIdOrUuid(String identifier) {
        lock.writeLock().lock();
        try {
            List<ReminderEvent> all = loadAll();
            Optional<ReminderEvent> match = findByIdOrUuid(identifier);
            if (match.isEmpty()) {
                return false;
            }
            String targetUuid = match.get().uuid();
            boolean removed = all.removeIf(e -> e.uuid().equals(targetUuid));
            if (removed) {
                saveAllAtomic(all);
            }
            return removed;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public ReminderImportResult importEvents(List<ReminderEvent> incoming, boolean replaceMode) {
        lock.writeLock().lock();
        try {
            if (incoming == null || incoming.isEmpty()) {
                return new ReminderImportResult(true, 0, 0, 0, loadAll().size(), "No events to import.");
            }

            List<ReminderEvent> existing = replaceMode ? new ArrayList<>() : loadAll();
            int imported = 0;
            int updated = 0;
            int skipped = 0;

            for (ReminderEvent in : incoming) {
                if (in == null) continue;

                // Match by UUID first, then friendly ID
                int existingIdx = -1;
                for (int i = 0; i < existing.size(); i++) {
                    ReminderEvent ex = existing.get(i);
                    if (ex.uuid().equals(in.uuid()) || ex.id().equalsIgnoreCase(in.id())) {
                        existingIdx = i;
                        break;
                    }
                }

                if (existingIdx >= 0) {
                    if (replaceMode) {
                        existing.set(existingIdx, in);
                        updated++;
                    } else {
                        // Merge mode updates the existing record
                        existing.set(existingIdx, in);
                        updated++;
                    }
                } else {
                    existing.add(in);
                    imported++;
                }
            }

            saveAllAtomic(existing);
            return new ReminderImportResult(true, imported, updated, skipped, existing.size(),
                    String.format("Successfully imported %d new events, updated %d existing events.", imported, updated));
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void saveAllAtomic(List<ReminderEvent> events) {
        Path targetFile = resolveStoragePath();
        Path parentDir = targetFile.getParent();
        try {
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            Path tmpFile = parentDir.resolve(targetFile.getFileName() + ".tmp." + System.nanoTime());

            Map<String, Object> root = new LinkedHashMap<>();
            root.put("version", 1);
            root.put("updatedAt", Instant.now().toString());
            root.put("totalEvents", events.size());
            root.put("events", events);

            byte[] bytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(root);

            try (FileOutputStream fos = new FileOutputStream(tmpFile.toFile())) {
                fos.write(bytes);
                fos.flush();
                fos.getFD().sync(); // Hard sync to disk
            }

            Files.move(tmpFile, targetFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            targetFile.toFile().setReadable(true, false);
        } catch (IOException e) {
            log.error("Atomic write failed for {}: {}", targetFile, e.getMessage(), e);
            throw new RuntimeException("Failed to persist reminders atomically: " + e.getMessage(), e);
        }
    }

    public static String generateNextFriendlyId(List<ReminderEvent> events) {
        int highest = 0;
        for (ReminderEvent e : events) {
            if (e.id() != null) {
                Matcher m = EVT_NUMBER_PATTERN.matcher(e.id().trim());
                if (m.matches()) {
                    try {
                        int num = Integer.parseInt(m.group(1));
                        if (num > highest) highest = num;
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return String.format("EVT-%03d", highest + 1);
    }
}
