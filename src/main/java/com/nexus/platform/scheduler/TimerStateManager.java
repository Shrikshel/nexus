package com.nexus.platform.scheduler;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nexus.capabilities.scheduler.timer.dto.TimerInfo;
import com.nexus.capabilities.scheduler.timer.dto.TimerScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages timer metadata, sequential ID assignment (NX001, NX002...),
 * and bi-directional resolution between IDs and user-friendly names.
 * Persists index to ~/.nexus/state/timers.json while remaining self-healing.
 */
@Component
public class TimerStateManager {

    private static final Logger log = LoggerFactory.getLogger(TimerStateManager.class);

    private static final Pattern NX_ID_PATTERN = Pattern.compile("^nx(\\d{3,})$", Pattern.CASE_INSENSITIVE);
    private static final Pattern FULL_ID_PATTERN = Pattern.compile("^nx(\\d{3,})-(.+)$", Pattern.CASE_INSENSITIVE);

    private final ObjectMapper objectMapper;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StateFile(
            int nextSequence,
            Map<String, TimerRecord> timers
    ) {
        public StateFile() {
            this(1, new LinkedHashMap<>());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TimerRecord(
            String id,
            String name,
            String fullIdentifier,
            String schedule,
            String scheduleDetails,
            String description,
            TimerScope scope,
            String scriptPath,
            String unitPath,
            String serviceUnitPath,
            Instant createdAt
    ) {}

    public TimerStateManager() {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    public Path getStateFilePath(TimerScope scope) {
        if (scope == TimerScope.SYSTEM) {
            return Path.of("/etc/nexus/state/timers.json");
        }
        return Path.of(System.getProperty("user.home"), ".nexus", "state", "timers.json");
    }

    public synchronized StateFile loadState(TimerScope scope) {
        Path path = getStateFilePath(scope);
        if (Files.exists(path)) {
            try {
                return objectMapper.readValue(path.toFile(), StateFile.class);
            } catch (Exception e) {
                log.warn("Could not read timer state file at {}: {}. Auto-healing...", path, e.getMessage());
            }
        }
        return new StateFile(1, new LinkedHashMap<>());
    }

    public synchronized void saveState(TimerScope scope, StateFile state) {
        Path path = getStateFilePath(scope);
        try {
            Files.createDirectories(path.getParent());
            objectMapper.writeValue(path.toFile(), state);
        } catch (Exception e) {
            log.error("Failed to save timer state to {}: {}", path, e.getMessage());
        }
    }

    /**
     * Allocates the next sequential ID (e.g. NX001, NX002) and computes the full identifier.
     */
    public synchronized String allocateNextId(TimerScope scope, String cleanName) {
        StateFile state = loadState(scope);
        int maxSeq = state.nextSequence();

        // Scan existing keys in state
        for (String key : state.timers().keySet()) {
            Matcher m = NX_ID_PATTERN.matcher(key);
            if (m.matches()) {
                try {
                    int seq = Integer.parseInt(m.group(1));
                    if (seq >= maxSeq) {
                        maxSeq = seq + 1;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        // Also scan unit files in systemd directory for any manual/existing NX ids
        int discoveredMax = scanFilesystemMaxSequence(scope);
        if (discoveredMax >= maxSeq) {
            maxSeq = discoveredMax + 1;
        }

        String assignedId = String.format("NX%03d", maxSeq);
        StateFile updated = new StateFile(maxSeq + 1, state.timers());
        saveState(scope, updated);
        return assignedId;
    }

    public synchronized void registerTimer(TimerScope scope, TimerRecord record) {
        StateFile state = loadState(scope);
        Map<String, TimerRecord> timers = new LinkedHashMap<>(state.timers());
        timers.put(record.id().toUpperCase(Locale.ROOT), record);
        saveState(scope, new StateFile(Math.max(state.nextSequence(), parseSeq(record.id()) + 1), timers));
    }

    public synchronized Optional<TimerRecord> unregisterTimer(TimerScope scope, String nameOrId) {
        StateFile state = loadState(scope);
        Optional<TimerRecord> found = resolveTimer(scope, nameOrId);
        if (found.isPresent()) {
            Map<String, TimerRecord> timers = new LinkedHashMap<>(state.timers());
            timers.remove(found.get().id().toUpperCase(Locale.ROOT));
            saveState(scope, new StateFile(state.nextSequence(), timers));
        }
        return found;
    }

    /**
     * Resolves an input string (e.g. "NX001", "nx001", "backup-db", "NX001-backup-db",
     * or "nexus-nx001-backup-db") to the corresponding timer record.
     */
    public Optional<TimerRecord> resolveTimer(TimerScope scope, String nameOrId) {
        if (nameOrId == null || nameOrId.isBlank()) {
            return Optional.empty();
        }
        String clean = nameOrId.trim();
        String upper = clean.toUpperCase(Locale.ROOT);

        StateFile state = loadState(scope);

        // 1. Direct match on ID
        if (state.timers().containsKey(upper)) {
            return Optional.of(state.timers().get(upper));
        }

        // 2. Normalize "nexus-nx001-name" or "nexus-nx001-name.timer"
        String stripped = clean.toLowerCase(Locale.ROOT);
        if (stripped.startsWith("nexus-")) {
            stripped = stripped.substring(6);
        }
        if (stripped.endsWith(".timer")) {
            stripped = stripped.substring(0, stripped.length() - 6);
        }
        if (stripped.endsWith(".service")) {
            stripped = stripped.substring(0, stripped.length() - 8);
        }

        // Check if stripped matches ID or FullIdentifier
        for (TimerRecord r : state.timers().values()) {
            if (r.id().equalsIgnoreCase(clean) || r.id().equalsIgnoreCase(stripped)) {
                return Optional.of(r);
            }
            if (r.fullIdentifier().equalsIgnoreCase(clean) || r.fullIdentifier().equalsIgnoreCase(stripped)) {
                return Optional.of(r);
            }
            if (r.name().equalsIgnoreCase(clean) || r.name().equalsIgnoreCase(stripped)) {
                return Optional.of(r);
            }
        }

        return Optional.empty();
    }

    private int scanFilesystemMaxSequence(TimerScope scope) {
        Path dir;
        if (scope == TimerScope.SYSTEM) {
            dir = Path.of("/etc/systemd/system");
        } else {
            dir = Path.of(System.getProperty("user.home"), ".config", "systemd", "user");
        }
        if (!Files.exists(dir)) {
            return 0;
        }

        int max = 0;
        File[] files = dir.toFile().listFiles((d, name) -> name.startsWith("nexus-nx") || name.startsWith("nexus-NX"));
        if (files != null) {
            for (File f : files) {
                Matcher m = Pattern.compile("nexus-nx(\\d{3,})-", Pattern.CASE_INSENSITIVE).matcher(f.getName());
                if (m.find()) {
                    try {
                        int s = Integer.parseInt(m.group(1));
                        if (s > max) max = s;
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return max;
    }

    private static int parseSeq(String id) {
        Matcher m = Pattern.compile("NX(\\d+)", Pattern.CASE_INSENSITIVE).matcher(id);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return 0;
    }
}
