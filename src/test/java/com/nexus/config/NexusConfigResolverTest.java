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
}
