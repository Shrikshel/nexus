package com.nexus.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class NexusConfigResolverTest {

    @AfterEach
    void cleanup() {
        System.clearProperty("nexus.config.file");
        System.clearProperty("spring.config.additional-location");
        System.clearProperty("spring.config.name");
    }

    @Test
    void testExtractExplicitConfigPath(@TempDir Path tempDir) throws IOException {
        Path cfg = tempDir.resolve("my-config.yaml");
        Files.writeString(cfg, "nexus:\n  mode: cli\n");

        File resolved = NexusConfigResolver.resolveConfigFile(new String[]{"-c", cfg.toString(), "info"});
        assertNotNull(resolved);
        assertEquals(cfg.toFile().getAbsolutePath(), resolved.getAbsolutePath());

        NexusConfigResolver.applySpringConfigProperties(resolved);
        assertEquals(cfg.toFile().getAbsolutePath(), NexusConfigResolver.getResolvedConfigPath());
        assertTrue(System.getProperty("spring.config.additional-location").contains(cfg.toFile().getAbsolutePath()));
    }

    @Test
    void testExtensionSwapYmlToYaml(@TempDir Path tempDir) throws IOException {
        Path yamlFile = tempDir.resolve("server.yaml");
        Files.writeString(yamlFile, "nexus:\n  mode: daemon\n");

        // Request server.yml when only server.yaml exists
        String requestedYml = tempDir.resolve("server.yml").toString();
        File resolved = NexusConfigResolver.resolveConfigFile(new String[]{"--config=" + requestedYml});

        assertNotNull(resolved);
        assertEquals(yamlFile.toFile().getAbsolutePath(), resolved.getAbsolutePath());
    }

    @Test
    void testEqualsSyntax(@TempDir Path tempDir) throws IOException {
        Path cfg = tempDir.resolve("test.yaml");
        Files.writeString(cfg, "nexus:\n  mode: cli\n");

        File resolved = NexusConfigResolver.resolveConfigFile(new String[]{"-c=" + cfg});
        assertNotNull(resolved);
        assertEquals(cfg.toFile().getAbsolutePath(), resolved.getAbsolutePath());
    }

    @Test
    void testReloadPropertiesFromYaml(@TempDir Path tempDir) throws IOException {
        Path cfg = tempDir.resolve("reload-test.yml");
        String content = """
                nexus:
                  mode: daemon
                  default-timeout: 45s
                  audit-enabled: false
                  telegram:
                    enabled: true
                    bot-token: "123:ABC"
                    allowed-chat-id: 777
                """;
        Files.writeString(cfg, content);

        NexusProperties props = new NexusProperties();
        boolean success = NexusConfigResolver.reloadPropertiesFromYaml(cfg.toFile(), props);

        assertTrue(success);
        assertEquals("daemon", props.getMode());
        assertEquals(java.time.Duration.ofSeconds(45), props.getDefaultTimeout());
        assertFalse(props.isAuditEnabled());
        assertNotNull(props.getTelegram());
        assertTrue(props.getTelegram().isEnabled());
        assertEquals("123:ABC", props.getTelegram().getBotToken());
        assertEquals(777L, props.getTelegram().getAllowedChatId());
    }

    @Test
    void testCreateDefaultConfigFileIfMissing(@TempDir Path tempDir) {
        File target = tempDir.resolve("subdir").resolve("config.yml").toFile();
        assertFalse(target.exists());

        boolean created = NexusConfigResolver.createDefaultConfigFileIfMissing(target);
        assertTrue(created);
        assertTrue(target.exists());
        assertTrue(target.length() > 0);
    }

    @Test
    void testNormalizePath() {
        File fileWithDot = new File("/home/toy/.nexus/./config.yml");
        File normalized = NexusConfigResolver.normalize(fileWithDot);
        assertNotNull(normalized);
        assertEquals("/home/toy/.nexus/config.yml", normalized.getAbsolutePath());
        assertFalse(normalized.getAbsolutePath().contains("/./"));

        assertNull(NexusConfigResolver.normalize(null));
    }

    @Test
    void testVerboseRequestedFromCliArgs() {
        assertTrue(NexusConfigResolver.isVerboseRequested(new String[]{"-v", "reminder", "list"}, null));
        assertTrue(NexusConfigResolver.isVerboseRequested(new String[]{"reminder", "list", "--verbose"}, null));
        assertTrue(NexusConfigResolver.isVerboseRequested(new String[]{"--debug"}, null));
        assertFalse(NexusConfigResolver.isVerboseRequested(new String[]{"reminder", "list"}, null));
    }

    @Test
    void testVerboseRequestedFromYamlConfig(@TempDir Path tempDir) throws IOException {
        Path cfg = tempDir.resolve("verbose.yml");
        Files.writeString(cfg, "nexus:\n  verbose: true\n");

        assertTrue(NexusConfigResolver.isVerboseRequested(new String[]{"reminder", "list"}, cfg.toFile()));

        Path cfgNormal = tempDir.resolve("normal.yml");
        Files.writeString(cfgNormal, "nexus:\n  verbose: false\n");

        assertFalse(NexusConfigResolver.isVerboseRequested(new String[]{"reminder", "list"}, cfgNormal.toFile()));
    }

    @Test
    void testResolveConfiguredLogLevel(@TempDir Path tempDir) throws IOException {
        Path cfg = tempDir.resolve("loglevel.yml");
        Files.writeString(cfg, "nexus:\n  log-level: DEBUG\n");

        assertEquals("DEBUG", NexusConfigResolver.resolveConfiguredLogLevel(cfg.toFile()));
        assertTrue(NexusConfigResolver.isVerboseRequested(new String[]{"status"}, cfg.toFile()));

        Path cfgWarn = tempDir.resolve("warn.yml");
        Files.writeString(cfgWarn, "nexus:\n  log-level: WARN\n");
        assertEquals("WARN", NexusConfigResolver.resolveConfiguredLogLevel(cfgWarn.toFile()));
    }

    @Test
    void testReloadPropertiesWithVerboseAndLogLevel(@TempDir Path tempDir) throws IOException {
        Path cfg = tempDir.resolve("verbose-props.yml");
        Files.writeString(cfg, "nexus:\n  verbose: true\n  log-level: DEBUG\n");

        NexusProperties props = new NexusProperties();
        boolean success = NexusConfigResolver.reloadPropertiesFromYaml(cfg.toFile(), props);

        assertTrue(success);
        assertTrue(props.isVerbose());
        assertEquals("DEBUG", props.getLogLevel());
    }
}
