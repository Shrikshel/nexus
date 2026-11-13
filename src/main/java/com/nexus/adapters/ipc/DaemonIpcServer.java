package com.nexus.adapters.ipc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.config.NexusConfigResolver;
import com.nexus.config.NexusProperties;
import com.nexus.core.capability.CapabilityRegistry;
import com.nexus.core.capability.CapabilityResult;
import com.nexus.core.capability.ExecutionContext;
import com.nexus.core.capability.ExecutionEngine;
import com.nexus.core.capability.InvocationSource;
import com.nexus.core.security.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Local authenticated IPC server allowing CLI tools, scripts, and automation
 * to query, monitor, and invoke capabilities on the background Nexus daemon.
 */
@Component
public class DaemonIpcServer {

    private static final Logger log = LoggerFactory.getLogger(DaemonIpcServer.class);
    public static final int DEFAULT_PORT = 29786;

    private final CapabilityRegistry registry;
    private final ApplicationContext applicationContext;
    private final ObjectProvider<ExecutionEngine> executionEngineProvider;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ServerSocket serverSocket;
    private ExecutorService serverExecutor;
    private String authToken;
    private final Instant startTime = Instant.now();

    @org.springframework.beans.factory.annotation.Autowired
    public DaemonIpcServer(CapabilityRegistry registry,
                           ApplicationContext applicationContext,
                           ObjectProvider<ExecutionEngine> executionEngineProvider) {
        this.registry = registry;
        this.applicationContext = applicationContext;
        this.executionEngineProvider = executionEngineProvider;
    }

    public DaemonIpcServer(CapabilityRegistry registry, ApplicationContext applicationContext) {
        this(registry, applicationContext, null);
    }

    public synchronized void start(int port) throws IOException {
        if (running.compareAndSet(false, true)) {
            this.authToken = generateSecureToken();
            writeRuntimeFiles(authToken);

            serverSocket = new ServerSocket(port, 10, InetAddress.getByName("127.0.0.1"));
            log.info("Local IPC server listening on 127.0.0.1:{}", port);

            serverExecutor = Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "nexus-ipc-worker");
                t.setDaemon(true);
                return t;
            });

            Thread acceptThread = new Thread(this::acceptLoop, "nexus-ipc-acceptor");
            acceptThread.setDaemon(true);
            acceptThread.start();
        }
    }

    public synchronized void stop() {
        if (running.compareAndSet(true, false)) {
            log.info("Shutting down local IPC server...");
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                }
            } catch (IOException ignored) {}

            if (serverExecutor != null) {
                serverExecutor.shutdownNow();
            }

            cleanupRuntimeFiles();
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public String getAuthToken() {
        return authToken;
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket client = serverSocket.accept();
                serverExecutor.submit(() -> handleClient(client));
            } catch (IOException e) {
                if (!running.get()) {
                    break;
                }
                log.warn("Error accepting IPC connection: {}", e.getMessage());
            }
        }
    }

    private void handleClient(Socket socket) {
        try (socket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true)) {

            // Expect first line: AUTH <token>
            String authLine = reader.readLine();
            if (authLine == null || !authLine.startsWith("AUTH ") || !authLine.substring(5).trim().equals(authToken)) {
                writer.println("ERROR: UNAUTHORIZED");
                return;
            }
            writer.println("OK: AUTHENTICATED");

            String line;
            while ((line = reader.readLine()) != null) {
                String cmd = line.trim();
                if ("STATUS".equalsIgnoreCase(cmd)) {
                    Map<String, Object> status = Map.of(
                            "status", "RUNNING",
                            "pid", ProcessHandle.current().pid(),
                            "uptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000,
                            "startTime", startTime.toString(),
                            "registeredCapabilities", registry.getAll().size()
                    );
                    writer.println(objectMapper.writeValueAsString(status));
                } else if ("STOP".equalsIgnoreCase(cmd)) {
                    writer.println("OK: STOPPING");
                    new Thread(() -> {
                        try {
                            Thread.sleep(500);
                            SpringApplication.exit(applicationContext, () -> 0);
                        } catch (InterruptedException ignored) {}
                    }).start();
                    break;
                } else if ("PING".equalsIgnoreCase(cmd)) {
                    writer.println("PONG");
                } else if ("RELOAD".equalsIgnoreCase(cmd) || cmd.startsWith("RELOAD ") || "CONFIG_RELOAD".equalsIgnoreCase(cmd)) {
                    String customPath = cmd.contains(" ") ? cmd.substring(cmd.indexOf(' ') + 1).trim() : null;
                    try {
                        String targetPath = (customPath != null && !customPath.isBlank()) ? customPath : NexusConfigResolver.getResolvedConfigPath();
                        File targetFile = NexusConfigResolver.resolveTargetConfigFileForEditing(targetPath);
                        if (targetFile != null && targetFile.exists()) {
                            NexusProperties props = applicationContext.getBean(NexusProperties.class);
                            boolean success = NexusConfigResolver.reloadPropertiesFromYaml(targetFile, props);
                            if (success) {
                                try {
                                    com.nexus.adapters.telegram.TelegramBotAdapter tgAdapter = applicationContext.getBean(com.nexus.adapters.telegram.TelegramBotAdapter.class);
                                    tgAdapter.reload();
                                } catch (Exception ignored) {}

                                Map<String, Object> resp = Map.of(
                                        "status", "OK",
                                        "reloaded", true,
                                        "configFile", targetFile.getAbsolutePath(),
                                        "mode", props.getMode(),
                                        "defaultTimeout", props.getDefaultTimeout().toString(),
                                        "auditEnabled", props.isAuditEnabled(),
                                        "telegramEnabled", props.getTelegram() != null && props.getTelegram().isEnabled()
                                );
                                writer.println(objectMapper.writeValueAsString(resp));
                            } else {
                                writer.println("ERROR: FAILED_TO_PARSE_YAML " + targetFile.getAbsolutePath());
                            }
                        } else {
                            writer.println("ERROR: CONFIG_FILE_NOT_FOUND");
                        }
                    } catch (Exception e) {
                        writer.println("ERROR: " + e.getMessage());
                    }
                } else if (cmd.startsWith("INVOKE ") || cmd.startsWith("EXEC ")) {
                    String rest = cmd.substring(cmd.indexOf(' ') + 1).trim();
                    String capId = rest.contains(" ") ? rest.substring(0, rest.indexOf(' ')) : rest;
                    String payloadStr = rest.contains(" ") ? rest.substring(rest.indexOf(' ') + 1).trim() : null;

                    if (executionEngineProvider != null && executionEngineProvider.getIfAvailable() != null) {
                        try {
                            ExecutionEngine engine = executionEngineProvider.getObject();
                            ExecutionContext context = ExecutionContext.create(
                                    InvocationSource.IPC,
                                    Subject.localCli(),
                                    Duration.ofSeconds(30)
                            );
                            Object inputObj = null;
                            if (payloadStr != null && !payloadStr.isBlank() && !payloadStr.equalsIgnoreCase("null")) {
                                inputObj = objectMapper.readTree(payloadStr);
                            }
                            CapabilityResult<Object> res = engine.execute(capId, context, inputObj);
                            writer.println(objectMapper.writeValueAsString(res));
                        } catch (Exception ex) {
                            writer.println("ERROR: " + ex.getMessage());
                        }
                    } else {
                        writer.println("ERROR: EXECUTION_ENGINE_UNAVAILABLE");
                    }
                } else {
                    writer.println("ERROR: UNKNOWN_COMMAND");
                }
            }
        } catch (Exception e) {
            log.debug("IPC client handling closed: {}", e.getMessage());
        }
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static Path getRuntimeDirectory() {
        String custom = System.getProperty("nexus.runtime.dir");
        if (custom != null && !custom.isBlank()) {
            return Path.of(custom);
        }
        String tmpDir = System.getProperty("java.io.tmpdir", "/tmp");
        String user = System.getProperty("user.name", "default");
        return Path.of(tmpDir, "nexus-" + user);
    }

    private void writeRuntimeFiles(String token) {
        try {
            Path dir = getRuntimeDirectory();
            Files.createDirectories(dir);

            Path tokenPath = dir.resolve("nexus.token");
            Files.writeString(tokenPath, token);
            try {
                Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
                Files.setPosixFilePermissions(tokenPath, perms);
            } catch (UnsupportedOperationException ignored) {}

            Path pidPath = dir.resolve("nexus.pid");
            Files.writeString(pidPath, String.valueOf(ProcessHandle.current().pid()));
        } catch (Exception e) {
            log.warn("Could not write IPC runtime files: {}", e.getMessage());
        }
    }

    private void cleanupRuntimeFiles() {
        try {
            Path dir = getRuntimeDirectory();
            Files.deleteIfExists(dir.resolve("nexus.token"));
            Files.deleteIfExists(dir.resolve("nexus.pid"));
        } catch (Exception e) {
            log.warn("Could not clean up IPC runtime files: {}", e.getMessage());
        }
    }
}
