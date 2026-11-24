package com.nexus.adapters.ipc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Local IPC client for CLI commands to query or control the background daemon.
 */
@Component
public class DaemonIpcClient {

    private static final Logger log = LoggerFactory.getLogger(DaemonIpcClient.class);

    public static List<Path> getCandidateTokenPaths() {
        List<Path> list = new ArrayList<>();

        // 1. Explicit runtime directory property
        String custom = System.getProperty("nexus.runtime.dir");
        if (custom != null && !custom.isBlank()) {
            list.add(Path.of(custom, "nexus.token"));
        }

        // 2. User-specific runtime dir (/tmp/nexus-$USER/nexus.token)
        list.add(DaemonIpcServer.getRuntimeDirectory().resolve("nexus.token"));

        // 3. User config dir (~/.nexus/nexus.token)
        String userHome = System.getProperty("user.home");
        if (userHome != null && !userHome.isBlank()) {
            list.add(Path.of(userHome, ".nexus", "nexus.token"));
        }

        // 4. System root locations (when running as root or via sudo)
        list.add(Path.of("/root/.nexus/nexus.token"));
        list.add(Path.of("/tmp/nexus-root/nexus.token"));

        return list;
    }

    public static Path resolveTokenPath() {
        for (Path p : getCandidateTokenPaths()) {
            if (p != null && Files.isReadable(p)) {
                return p;
            }
        }
        return null;
    }

    public String sendCommand(String command, int port) throws IOException {
        boolean isReadOnly = "PING".equalsIgnoreCase(command) || "STATUS".equalsIgnoreCase(command);

        String token = null;
        Path tokenPath = resolveTokenPath();
        if (tokenPath != null && Files.isReadable(tokenPath)) {
            try {
                token = Files.readString(tokenPath).trim();
            } catch (Exception ignored) {}
        }

        if (!isReadOnly && (token == null || token.isBlank())) {
            throw new FileNotFoundException("Daemon auth token not found or not readable. Is the daemon running with matching privileges?");
        }

        try (Socket socket = new Socket(InetAddress.getByName("127.0.0.1"), port);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true)) {

            // If token is present, perform authentication handshake
            if (token != null && !token.isBlank()) {
                writer.println("AUTH " + token);
                String authResponse = reader.readLine();
                if (!"OK: AUTHENTICATED".equals(authResponse)) {
                    if (!isReadOnly) {
                        throw new SecurityException("IPC Authentication failed: " + authResponse);
                    }
                }
            }

            // Send command
            writer.println(command);
            return reader.readLine();
        }
    }

    public boolean isDaemonRunning(int port) {
        try {
            String resp = sendCommand("PING", port);
            return "PONG".equals(resp);
        } catch (Exception e) {
            return false;
        }
    }
}
