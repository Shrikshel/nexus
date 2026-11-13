package com.nexus.adapters.ipc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Local IPC client for CLI commands to query or control the background daemon.
 */
@Component
public class DaemonIpcClient {

    private static final Logger log = LoggerFactory.getLogger(DaemonIpcClient.class);

    public String sendCommand(String command, int port) throws IOException {
        Path tokenPath = DaemonIpcServer.getRuntimeDirectory().resolve("nexus.token");
        if (!Files.exists(tokenPath)) {
            throw new FileNotFoundException("Daemon auth token not found at " + tokenPath + ". Is the daemon running?");
        }

        String token = Files.readString(tokenPath).trim();

        try (Socket socket = new Socket(InetAddress.getByName("127.0.0.1"), port);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true)) {

            // 1. Authenticate
            writer.println("AUTH " + token);
            String authResponse = reader.readLine();
            if (!"OK: AUTHENTICATED".equals(authResponse)) {
                throw new SecurityException("IPC Authentication failed: " + authResponse);
            }

            // 2. Send command
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
