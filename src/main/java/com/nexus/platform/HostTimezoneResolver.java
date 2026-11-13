package com.nexus.platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

/**
 * Detects and configures the host operating system's timezone to ensure agent
 * telemetry, timestamps, and logs accurately reflect the host's local time.
 */
public final class HostTimezoneResolver {

    private static final Logger log = LoggerFactory.getLogger(HostTimezoneResolver.class);
    private static volatile ZoneId hostZoneId;

    static {
        initHostTimezone();
    }

    private HostTimezoneResolver() {}

    public static synchronized void initHostTimezone() {
        ZoneId resolved = resolveFromSystem();
        hostZoneId = resolved;
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(resolved));
            System.setProperty("user.timezone", resolved.getId());
        } catch (Exception e) {
            log.debug("Could not set default TimeZone: {}", e.getMessage());
        }
    }

    public static ZoneId getHostZoneId() {
        if (hostZoneId == null) {
            initHostTimezone();
        }
        return hostZoneId;
    }

    public static String getFormattedTimezone() {
        ZoneId zone = getHostZoneId();
        ZonedDateTime now = ZonedDateTime.now(zone);
        String offset = now.format(DateTimeFormatter.ofPattern("xxx"));
        String shortName = now.format(DateTimeFormatter.ofPattern("z"));
        return String.format("%s (%s, UTC%s)", zone.getId(), shortName, offset);
    }

    private static ZoneId resolveFromSystem() {
        // 1. Check TZ environment variable
        String envTz = System.getenv("TZ");
        if (envTz != null && !envTz.isBlank()) {
            try {
                return ZoneId.of(envTz.trim());
            } catch (Exception ignored) {}
        }

        // 2. Check Linux /etc/localtime symlink
        try {
            Path localtime = Path.of("/etc/localtime");
            if (Files.isSymbolicLink(localtime)) {
                Path target = Files.readSymbolicLink(localtime);
                String pathStr = target.toString();
                int idx = pathStr.indexOf("zoneinfo/");
                if (idx >= 0) {
                    String zoneName = pathStr.substring(idx + "zoneinfo/".length());
                    return ZoneId.of(zoneName);
                }
            }
        } catch (Exception ignored) {}

        // 3. Check Linux /etc/timezone file
        try {
            File tzFile = new File("/etc/timezone");
            if (tzFile.exists()) {
                String zoneName = Files.readString(tzFile.toPath()).trim();
                if (!zoneName.isBlank()) {
                    return ZoneId.of(zoneName);
                }
            }
        } catch (Exception ignored) {}

        // 4. Default to Java system default
        return ZoneId.systemDefault();
    }
}
