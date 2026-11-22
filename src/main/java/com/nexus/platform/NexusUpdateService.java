package com.nexus.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Service responsible for checking, downloading, verifying, and applying
 * in-place binary updates for the Nexus host management agent.
 */
@Service
public class NexusUpdateService {

    private static final Logger log = LoggerFactory.getLogger(NexusUpdateService.class);

    public static final String CURRENT_VERSION = "0.0.3-SNAPSHOT";
    public static final String DEFAULT_REPO = "Shrikshel/nexus";

    private final PlatformDetector platformDetector;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public NexusUpdateService(PlatformDetector platformDetector) {
        this.platformDetector = platformDetector;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public record ReleaseInfo(
            String tagName,
            String releaseUrl,
            String binaryAssetUrl,
            String checksumAssetUrl,
            String binaryAssetName
    ) {}

    public record UpdateResult(
            boolean success,
            boolean updateAvailable,
            String currentVersion,
            String targetVersion,
            String message,
            String binaryPath
    ) {}

    /**
     * Resolves the target binary asset name for the current host OS and CPU architecture.
     */
    public String resolveBinaryAssetName() {
        String os = platformDetector.isLinux() ? "linux" :
                (platformDetector.isMac() ? "darwin" : "windows");

        String archRaw = platformDetector.getOsArch().toLowerCase();
        String arch = (archRaw.contains("aarch64") || archRaw.contains("arm64")) ? "arm64" : "amd64";

        String ext = platformDetector.isWindows() ? ".exe" : "";
        return "nexus-" + os + "-" + arch + ext;
    }

    /**
     * Queries GitHub Releases API to fetch release metadata.
     */
    public ReleaseInfo fetchReleaseInfo(String repo, Optional<String> targetTag) throws IOException, InterruptedException {
        String effectiveRepo = (repo != null && !repo.isBlank()) ? repo : DEFAULT_REPO;
        String apiUrl = targetTag.map(t -> "https://api.github.com/repos/" + effectiveRepo + "/releases/tags/" + (t.startsWith("v") ? t : "v" + t))
                .orElse("https://api.github.com/repos/" + effectiveRepo + "/releases/latest");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("User-Agent", "Nexus-Agent/" + CURRENT_VERSION)
                .header("Accept", "application/vnd.github.v3+json")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        JsonNode root;
        if (response.statusCode() == 404 && targetTag.isEmpty()) {
            // Fallback: If /releases/latest returned 404 (e.g. GitHub excludes pre-releases/snapshots),
            // query the releases list API to fetch the most recent release
            String listApiUrl = "https://api.github.com/repos/" + effectiveRepo + "/releases";
            HttpRequest listRequest = HttpRequest.newBuilder()
                    .uri(URI.create(listApiUrl))
                    .header("User-Agent", "Nexus-Agent/" + CURRENT_VERSION)
                    .header("Accept", "application/vnd.github.v3+json")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> listResponse = httpClient.send(listRequest, HttpResponse.BodyHandlers.ofString());
            if (listResponse.statusCode() == 200) {
                JsonNode array = objectMapper.readTree(listResponse.body());
                if (array.isArray() && !array.isEmpty()) {
                    root = array.get(0);
                } else {
                    throw new FileNotFoundException("Release not found on repository '" + effectiveRepo + "'");
                }
            } else {
                throw new FileNotFoundException("Release not found on repository '" + effectiveRepo + "'");
            }
        } else if (response.statusCode() == 404) {
            throw new FileNotFoundException("Release not found on repository '" + effectiveRepo + "'");
        } else if (response.statusCode() >= 400) {
            throw new IOException("GitHub API error (HTTP " + response.statusCode() + "): " + response.body());
        } else {
            root = objectMapper.readTree(response.body());
        }

        String tagName = root.path("tag_name").asText("");
        String htmlUrl = root.path("html_url").asText("");

        String expectedBinary = resolveBinaryAssetName();
        String binaryDownloadUrl = null;
        String checksumDownloadUrl = null;

        JsonNode assets = root.path("assets");
        if (assets.isArray()) {
            for (JsonNode asset : assets) {
                String name = asset.path("name").asText("");
                String downloadUrl = asset.path("browser_download_url").asText("");
                if (name.equalsIgnoreCase(expectedBinary)) {
                    binaryDownloadUrl = downloadUrl;
                } else if (name.equalsIgnoreCase("SHA256SUMS.txt")) {
                    checksumDownloadUrl = downloadUrl;
                }
            }
        }

        // Fallback to direct release download URL if asset wasn't enumerated
        if (binaryDownloadUrl == null) {
            binaryDownloadUrl = "https://github.com/" + effectiveRepo + "/releases/download/" + tagName + "/" + expectedBinary;
        }

        return new ReleaseInfo(tagName, htmlUrl, binaryDownloadUrl, checksumDownloadUrl, expectedBinary);
    }

    /**
     * Checks if a newer version is available.
     */
    public boolean isNewerVersion(String currentVersion, String latestVersion) {
        if (currentVersion == null || latestVersion == null) {
            return false;
        }
        String curr = currentVersion.startsWith("v") ? currentVersion.substring(1) : currentVersion;
        String latest = latestVersion.startsWith("v") ? latestVersion.substring(1) : latestVersion;

        if (curr.equalsIgnoreCase(latest)) {
            return false;
        }

        // Dev/snapshot builds consider release builds newer
        if (curr.contains("SNAPSHOT")) {
            return true;
        }
        return !curr.equalsIgnoreCase(latest);
    }

    /**
     * Downloads file to a temporary location.
     */
    public Path downloadToTemp(String downloadUrl, String prefix) throws IOException, InterruptedException {
        Path tempFile = Files.createTempFile(prefix, ".tmp");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(downloadUrl))
                .header("User-Agent", "Nexus-Agent/" + CURRENT_VERSION)
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();

        HttpResponse<Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(tempFile));
        if (response.statusCode() >= 400) {
            Files.deleteIfExists(tempFile);
            throw new IOException("Download failed with HTTP " + response.statusCode());
        }
        return tempFile;
    }

    /**
     * Computes the SHA256 hex string of a file.
     */
    public String computeSha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream is = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Locates the active Nexus binary on the host system.
     */
    public Path locateCurrentBinary() {
        // 1. Check ProcessHandle command line (present in GraalVM native binary)
        try {
            Optional<String> cmd = ProcessHandle.current().info().command();
            if (cmd.isPresent()) {
                Path p = Paths.get(cmd.get());
                if (Files.isRegularFile(p)) {
                    return p.toAbsolutePath();
                }
            }
        } catch (Throwable ignored) {}

        // 2. Check standard system installation locations
        Path[] commonLocations = new Path[] {
                Paths.get("/usr/local/bin/nexus"),
                Paths.get(System.getProperty("user.home"), ".local", "bin", "nexus"),
                Paths.get(System.getProperty("user.home"), "bin", "nexus")
        };
        for (Path p : commonLocations) {
            if (Files.isRegularFile(p)) {
                return p;
            }
        }

        // 3. Fallback to /usr/local/bin/nexus
        return Paths.get("/usr/local/bin/nexus");
    }

    /**
     * Replaces the target executable atomically with the newly downloaded binary.
     */
    public void applyUpdate(Path newBinary, Path targetBinary) throws IOException {
        // Ensure executable permissions before moving
        File f = newBinary.toFile();
        f.setExecutable(true, false);
        f.setReadable(true, false);

        // Atomic replacement
        try {
            Files.move(newBinary, targetBinary, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(newBinary, targetBinary, StandardCopyOption.REPLACE_EXISTING);
        }

        // Clean up duplicate binaries in alternate standard locations to ensure a single active installation
        Path[] commonLocations = new Path[] {
                Paths.get("/usr/local/bin/nexus"),
                Paths.get(System.getProperty("user.home"), ".local", "bin", "nexus"),
                Paths.get(System.getProperty("user.home"), "bin", "nexus")
        };
        for (Path loc : commonLocations) {
            try {
                if (Files.exists(loc) && !Files.isSameFile(loc, targetBinary)) {
                    log.info("Removing duplicate binary at {} to prevent multiple versions side-by-side", loc);
                    Files.deleteIfExists(loc);
                }
            } catch (Exception ignored) {}
        }
    }
}
