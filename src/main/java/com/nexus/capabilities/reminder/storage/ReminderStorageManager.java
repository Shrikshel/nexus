package com.nexus.capabilities.reminder.storage;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nexus.capabilities.reminder.dto.*;
import com.nexus.capabilities.scheduler.timer.dto.TimerScope;
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

    public static final Path SYSTEM_ROOT_PATH = Path.of("/root/.nexus/data/reminders.json");

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
            return SYSTEM_ROOT_PATH;
        }

        return Path.of(System.getProperty("user.home"), ".nexus", "data", "reminders.json");
    }

    private List<ReminderEvent> loadFromPathOrFallback(Path path, TimerScope scope) {
        if (path != null && Files.exists(path) && Files.isReadable(path)) {
            try {
                byte[] bytes = Files.readAllBytes(path);
                if (bytes.length > 0) {
                    return parseEvents(bytes, scope);
                }
            } catch (Exception e) {
                log.warn("Could not read reminders from {}: {}", path, e.getMessage());
            }
        }

        // If SYSTEM scope and unreadable directly, fallback to sudo -n cat if running as non-root on Linux
        if (scope == TimerScope.SYSTEM) {
            if (!"root".equalsIgnoreCase(System.getProperty("user.name"))) {
                try {
                    Process p = new ProcessBuilder("sudo", "-n", "cat", path.toString()).start();
                    byte[] bytes = p.getInputStream().readAllBytes();
                    int exit = p.waitFor();
                    if (exit == 0 && bytes.length > 0) {
                        return parseEvents(bytes, TimerScope.SYSTEM);
                    }
                } catch (Exception ignored) {}
            }
        }

        return new ArrayList<>();
    }

    private List<ReminderEvent> parseEvents(byte[] bytes, TimerScope scope) {
        try {
            JsonNode root = objectMapper.readTree(bytes);
            JsonNode eventsNode = root.path("events");
            if (!eventsNode.isArray()) return new ArrayList<>();
            List<ReminderEvent> list = new ArrayList<>();
            for (JsonNode item : eventsNode) {
                try {
                    ReminderEvent event = objectMapper.treeToValue(item, ReminderEvent.class);
                    if (event != null) {
                        if (!event.metadata().containsKey("scope")) {
                            Map<String, Object> meta = new LinkedHashMap<>(event.metadata());
                            meta.put("scope", scope == TimerScope.SYSTEM ? "SYSTEM" : "USER");
                            event = new ReminderEvent(
                                    event.uuid(), event.id(), event.title(), event.type(), event.category(),
                                    event.targetDate(), event.targetTime(), event.timezone(), event.recurrence(),
                                    meta, event.stages(), event.state(), event.history()
                            );
                        }
                        list.add(event);
                    }
                } catch (Exception e) {
                    log.warn("Skipping unparseable reminder event: {}", e.getMessage());
                }
            }
            return list;
        } catch (Exception e) {
            log.error("Failed to parse reminders JSON: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<ReminderEvent> loadAll(TimerScope scope) {
        lock.readLock().lock();
        try {
            if (customStoragePath != null) {
                return new ArrayList<>(loadFromPathOrFallback(customStoragePath, TimerScope.USER));
            }

            TimerScope effective = scope != null ? scope : TimerScope.ALL;
            if (effective == TimerScope.SYSTEM) {
                return new ArrayList<>(loadFromPathOrFallback(SYSTEM_ROOT_PATH, TimerScope.SYSTEM));
            } else if (effective == TimerScope.USER) {
                Path userPath = Path.of(System.getProperty("user.home"), ".nexus", "data", "reminders.json");
                return new ArrayList<>(loadFromPathOrFallback(userPath, TimerScope.USER));
            } else {
                Path userPath = Path.of(System.getProperty("user.home"), ".nexus", "data", "reminders.json");
                if (Files.exists(userPath)) {
                    return new ArrayList<>(loadFromPathOrFallback(userPath, TimerScope.USER));
                }
                // If user-specific file does not exist yet, load system records
                return new ArrayList<>(loadFromPathOrFallback(SYSTEM_ROOT_PATH, TimerScope.SYSTEM));
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<ReminderEvent> loadAll() {
        return loadAll(TimerScope.ALL);
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

            List<ReminderStage> rawStages = StageCalculator.buildStages(
                    stageTokens, req.targetDate(), req.targetTime(), zone, req.customStageMessages()
            );
            List<ReminderStage> stages = StageCalculator.filterPastStages(rawStages, Instant.now());

            // 6. Initial State & Cycle
            String cycle;
            if (recurrence.isSubDay()) {
                cycle = req.targetDate() + "T" + (req.targetTime() != null ? req.targetTime().toString().substring(0, 5) : "09:00");
            } else if (recurrence.unit() == ReminderRecurrence.Unit.DAYS || recurrence.unit() == ReminderRecurrence.Unit.WEEKS) {
                cycle = req.targetDate().toString();
            } else {
                cycle = String.valueOf(req.targetDate().getYear());
            }

            ReminderStage firstUpcoming = stages.stream()
                    .filter(s -> s.status() == ReminderStageStatus.PENDING)
                    .findFirst()
                    .orElse(null);

            String initialNextStage = firstUpcoming != null ? firstUpcoming.stage() : null;
            Instant initialNextTrigger = firstUpcoming != null ? firstUpcoming.triggerAt() : null;
            String status = (firstUpcoming == null && recurrence.isNone()) ? "COMPLETED" : "ACTIVE";
            ReminderEventState state = new ReminderEventState(cycle, status, null, initialNextStage, initialNextTrigger);

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
                List<ReminderStage> raw = StageCalculator.buildStages(tokens, date, time, zone, customMsgs);
                updatedStages = StageCalculator.filterPastStages(raw, Instant.now());
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

            String status = (nextPending == null && recurrence.isNone()) ? "COMPLETED" : "ACTIVE";
            ReminderEventState newState = new ReminderEventState(
                    String.valueOf(date.getYear()),
                    status,
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

    public ReminderEvent reconcileEvent(ReminderEvent event, Instant now) {
        if (event == null || !"ACTIVE".equalsIgnoreCase(event.state().status())) {
            return event;
        }
        List<ReminderStage> filtered = StageCalculator.filterPastStages(event.stages(), now);
        boolean changed = false;
        for (int i = 0; i < filtered.size(); i++) {
            if (filtered.get(i).status() != event.stages().get(i).status()) {
                changed = true;
                break;
            }
        }
        if (!changed) {
            return event;
        }
        ReminderStage nextPending = filtered.stream()
                .filter(s -> s.status() == ReminderStageStatus.PENDING)
                .findFirst()
                .orElse(null);

        String status = (nextPending == null && event.recurrence().isNone()) ? "COMPLETED" : "ACTIVE";
        ReminderEventState newState = new ReminderEventState(
                event.state().activeCycle(),
                status,
                event.state().lastDispatchedStage(),
                nextPending != null ? nextPending.stage() : null,
                nextPending != null ? nextPending.triggerAt() : null
        );
        ReminderEvent reconciled = event.withUpdatedState(newState, filtered);
        saveEvent(reconciled);
        return reconciled;
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

    private void writeAtomicFile(Path targetFile, byte[] bytes) throws IOException {
        Path parentDir = targetFile.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        Path tmpFile = parentDir.resolve(targetFile.getFileName() + ".tmp." + System.nanoTime());
        try (FileOutputStream fos = new FileOutputStream(tmpFile.toFile())) {
            fos.write(bytes);
            fos.flush();
            fos.getFD().sync(); // Hard sync to disk
        }

        Files.move(tmpFile, targetFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        targetFile.toFile().setReadable(true, false);
    }

    private void saveAllAtomic(List<ReminderEvent> events) {
        Path targetFile = resolveStoragePath();
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("version", 1);
            root.put("updatedAt", Instant.now().toString());
            root.put("totalEvents", events.size());
            root.put("events", events);

            byte[] bytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(root);
            writeAtomicFile(targetFile, bytes);
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
