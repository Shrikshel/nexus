package com.nexus.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Resolves configuration file location from CLI arguments or standard system locations,
 * and sets up Spring Boot environment properties before application bootstrap.
 */
public final class NexusConfigResolver {

    private static final Logger log = LoggerFactory.getLogger(NexusConfigResolver.class);
    public static final String PROPERTY_CONFIG_FILE = "nexus.config.file";

    public static final String DEFAULT_CONFIG_TEMPLATE = """
            # ==============================================================================
            # Nexus Host Management Agent Configuration
            # ==============================================================================
            
            nexus:
              # Operational mode: cli or daemon
              mode: daemon
            
              # Default execution timeout for capabilities (e.g. 30s, 60s, 2m)
              default-timeout: 30s
            
              # Enable audit logging to nexus-audit.log
              audit-enabled: true

              # Centralized log directory (defaults to ~/.nexus/logs or /var/log/nexus)
              # log-dir: ~/.nexus/logs
            
              # Remote bi-directional Telegram management adapter
              telegram:
                # Set to true to enable Telegram bot polling & alerting
                enabled: false
            
                # Bot API token from @BotFather (e.g. "123456789:ABCDefGhIJKlmNoPQRsTUVwxyZ")
                bot-token: ""
            
                # Authorized Telegram Chat ID (only messages from this ID will be accepted)
                allowed-chat-id: 0

                # Default recipients for outgoing alerts & messages (user IDs, channel IDs, or @usernames)
                recipients: []
            """;

    private static final List<String> DEFAULT_CONFIG_LOCATIONS = List.of(
            "./config.yaml",
            "./config.yml",
            "./nexus.yaml",
            "./nexus.yml",
            System.getProperty("user.home") + "/.nexus/config.yaml",
            System.getProperty("user.home") + "/.nexus/config.yml",
            "/etc/nexus/config.yaml",
            "/etc/nexus/config.yml"
    );

    private NexusConfigResolver() {}

    /**
     * Resolves the configuration file from CLI arguments or default paths.
     * Exits early with an error message if an explicitly passed configuration file is missing.
     *
     * @param args Command-line arguments
     * @return Resolved existing File, or null if using embedded defaults
     */
    public static File resolveConfigFile(String[] args) {
        String explicitPath = extractConfigFileArgument(args);

        if (explicitPath != null) {
            File explicitFile = new File(explicitPath);
            if (explicitFile.exists() && explicitFile.isFile()) {
                return explicitFile;
            }

            // Check common extension swap (.yml <-> .yaml)
            File altFile = getAlternativeExtensionFile(explicitPath);
            if (altFile != null && altFile.exists() && altFile.isFile()) {
                System.out.printf("\u001B[33m[NOTICE] Using '%s' instead of requested '%s'\u001B[0m%n",
                        altFile.getPath(), explicitPath);
                return altFile;
            }

            System.err.printf("\u001B[31m[ERROR] Configuration file not found: %s\u001B[0m%n", explicitPath);
            System.err.println("Please provide a valid YAML configuration file path via -c or --config.");
            System.exit(1);
        }

        // Check standard default locations
        for (String location : DEFAULT_CONFIG_LOCATIONS) {
            File candidate = new File(location);
            if (candidate.exists() && candidate.isFile()) {
                return candidate;
            }
        }

        return null;
    }

    /**
     * Resolves target configuration file for editing or viewing.
     * If an explicit path is provided, it is returned.
     * If an active configuration file is already resolved, it is returned.
     * Otherwise, checks default locations, falling back to ~/.nexus/config.yml.
     */
    public static File resolveTargetConfigFileForEditing(String explicitPath) {
        if (explicitPath != null && !explicitPath.isBlank()) {
            return new File(explicitPath);
        }

        String resolved = getResolvedConfigPath();
        if (resolved != null && !resolved.isBlank()) {
            return new File(resolved);
        }

        for (String loc : DEFAULT_CONFIG_LOCATIONS) {
            File f = new File(loc);
            if (f.exists() && f.isFile()) {
                return f;
            }
        }

        // If running as root or /etc/nexus exists/writable, prefer /etc/nexus/config.yml
        File etcDir = new File("/etc/nexus");
        if (etcDir.exists() && etcDir.canWrite()) {
            return new File(etcDir, "config.yml");
        }

        // Standard user fallback
        return new File(System.getProperty("user.home") + "/.nexus/config.yml");
    }

    /**
     * Creates a default configuration file with starter template if it does not already exist.
     */
    public static boolean createDefaultConfigFileIfMissing(File targetFile) {
        if (targetFile == null || targetFile.exists()) {
            return false;
        }
        try {
            File parent = targetFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            Files.writeString(targetFile.toPath(), DEFAULT_CONFIG_TEMPLATE);
            return true;
        } catch (Exception e) {
            log.warn("Could not create default config file at {}: {}", targetFile, e.getMessage());
            return false;
        }
    }

    /**
     * Parses a YAML configuration file and dynamically updates NexusProperties bean in-memory.
     *
     * @param configFile the YAML configuration file to load
     * @param properties the target NexusProperties bean to update
     * @return true if loaded and applied successfully, false otherwise
     */
    public static boolean reloadPropertiesFromYaml(File configFile, NexusProperties properties) {
        if (configFile == null || !configFile.exists() || !configFile.isFile()) {
            return false;
        }
        try (InputStream is = new FileInputStream(configFile)) {
            Yaml yaml = new Yaml();
            Object loaded = yaml.load(is);
            if (!(loaded instanceof Map<?, ?> rootMap)) {
                return false;
            }
            Object nexusObj = rootMap.get("nexus");
            if (!(nexusObj instanceof Map<?, ?> nexusMap)) {
                return false;
            }

            if (nexusMap.containsKey("mode")) {
                properties.setMode(String.valueOf(nexusMap.get("mode")));
            }
            if (nexusMap.containsKey("default-timeout")) {
                String timeoutStr = String.valueOf(nexusMap.get("default-timeout")).trim();
                properties.setDefaultTimeout(parseDuration(timeoutStr));
            }
            if (nexusMap.containsKey("audit-enabled")) {
                properties.setAuditEnabled(Boolean.parseBoolean(String.valueOf(nexusMap.get("audit-enabled"))));
            }

            if (nexusMap.containsKey("log-dir") || nexusMap.containsKey("logDir")) {
                Object logDirVal = nexusMap.containsKey("log-dir") ? nexusMap.get("log-dir") : nexusMap.get("logDir");
                if (logDirVal != null && !String.valueOf(logDirVal).isBlank()) {
                    String custom = String.valueOf(logDirVal).trim();
                    properties.setLogDir(custom);
                    com.nexus.platform.logging.NexusLogManager.setLogDirectory(java.nio.file.Path.of(custom));
                }
            }

            Object tgObj = nexusMap.get("telegram");
            if (tgObj instanceof Map<?, ?> tgMap) {
                NexusProperties.TelegramConfig tg = properties.getTelegram();
                if (tg == null) {
                    tg = new NexusProperties.TelegramConfig();
                    properties.setTelegram(tg);
                }
                if (tgMap.containsKey("enabled")) {
                    tg.setEnabled(Boolean.parseBoolean(String.valueOf(tgMap.get("enabled"))));
                }
                if (tgMap.containsKey("bot-token")) {
                    Object val = tgMap.get("bot-token");
                    tg.setBotToken(val != null ? String.valueOf(val) : "");
                }
                if (tgMap.containsKey("allowed-chat-id")) {
                    try {
                        Object val = tgMap.get("allowed-chat-id");
                        if (val instanceof Number n) {
                            tg.setAllowedChatId(n.longValue());
                        } else if (val != null) {
                            tg.setAllowedChatId(Long.parseLong(String.valueOf(val).trim()));
                        }
                    } catch (Exception ignored) {}
                }
                if (tgMap.containsKey("recipients")) {
                    Object recVal = tgMap.get("recipients");
                    java.util.List<String> list = new java.util.ArrayList<>();
                    if (recVal instanceof java.util.List<?> recList) {
                        for (Object item : recList) {
                            if (item != null && !String.valueOf(item).isBlank()) {
                                list.add(String.valueOf(item).trim());
                            }
                        }
                    } else if (recVal != null && !String.valueOf(recVal).isBlank()) {
                        list.add(String.valueOf(recVal).trim());
                    }
                    tg.setRecipients(list);
                }
            }

            System.setProperty(PROPERTY_CONFIG_FILE, configFile.getAbsolutePath());
            return true;
        } catch (Exception e) {
            log.warn("Failed to reload configuration from {}: {}", configFile, e.getMessage());
            return false;
        }
    }

    public static Duration parseDuration(String str) {
        if (str == null || str.isBlank()) {
            return Duration.ofSeconds(30);
        }
        String s = str.trim().toLowerCase();
        try {
            if (s.endsWith("ms")) {
                return Duration.ofMillis(Long.parseLong(s.substring(0, s.length() - 2).trim()));
            } else if (s.endsWith("s")) {
                return Duration.ofSeconds(Long.parseLong(s.substring(0, s.length() - 1).trim()));
            } else if (s.endsWith("m")) {
                return Duration.ofMinutes(Long.parseLong(s.substring(0, s.length() - 1).trim()));
            } else if (s.endsWith("h")) {
                return Duration.ofHours(Long.parseLong(s.substring(0, s.length() - 1).trim()));
            } else if (s.startsWith("pt") || s.startsWith("p")) {
                return Duration.parse(str.toUpperCase());
            } else {
                return Duration.ofSeconds(Long.parseLong(s));
            }
        } catch (Exception e) {
            return Duration.ofSeconds(30);
        }
    }

    /**
     * Applies the resolved configuration file path to Spring Boot system properties
     * before SpringApplication boots.
     *
     * @param configFile The resolved configuration file (may be null)
     */
    public static void applySpringConfigProperties(File configFile) {
        System.setProperty("spring.config.name", "application,config,nexus");

        if (configFile != null && configFile.exists()) {
            String absPath = configFile.getAbsolutePath();
            System.setProperty(PROPERTY_CONFIG_FILE, absPath);
            // Instruct Spring Boot ConfigDataEnvironmentPostProcessor to load this file
            System.setProperty("spring.config.additional-location", "file:" + absPath);
        }
    }

    /**
     * Returns the absolute path of the currently resolved configuration file, or null.
     */
    public static String getResolvedConfigPath() {
        return System.getProperty(PROPERTY_CONFIG_FILE);
    }

    private static String extractConfigFileArgument(String[] args) {
        if (args == null || args.length == 0) {
            return null;
        }

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("-c".equals(arg) || "--config".equals(arg)) {
                if (i + 1 < args.length) {
                    return args[i + 1];
                }
            } else if (arg.startsWith("-c=")) {
                return arg.substring(3);
            } else if (arg.startsWith("--config=")) {
                return arg.substring(9);
            }
        }
        return null;
    }

    private static File getAlternativeExtensionFile(String path) {
        if (path.endsWith(".yml")) {
            return new File(path.substring(0, path.length() - 4) + ".yaml");
        } else if (path.endsWith(".yaml")) {
            return new File(path.substring(0, path.length() - 5) + ".yml");
        }
        return null;
    }
}
