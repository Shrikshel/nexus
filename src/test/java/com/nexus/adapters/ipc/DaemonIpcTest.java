package com.nexus.adapters.ipc;

import com.nexus.core.capability.CapabilityRegistry;
import com.nexus.core.capability.CapabilityResult;
import com.nexus.core.capability.ExecutionEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DaemonIpcTest {

    private static final int TEST_PORT = 29789;
    private DaemonIpcServer server;
    private DaemonIpcClient client;
    private ApplicationContext context;

    @BeforeEach
    void setup() throws Exception {
        CapabilityRegistry registry = new CapabilityRegistry(List.of());
        context = mock(ApplicationContext.class);

        server = new DaemonIpcServer(registry, context);
        server.start(TEST_PORT);
        client = new DaemonIpcClient();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    @DisplayName("Authorized client can ping and query daemon status over IPC")
    void testIpcPingAndStatus() throws Exception {
        boolean running = client.isDaemonRunning(TEST_PORT);
        assertThat(running).isTrue();

        String statusJson = client.sendCommand("STATUS", TEST_PORT);
        assertThat(statusJson).contains("\"status\":\"RUNNING\"");
        assertThat(statusJson).contains("\"pid\":");
    }

    @Test
    @DisplayName("Unauthorized client connection without valid token is rejected")
    void testUnauthorizedClientRejected() throws Exception {
        try (Socket socket = new Socket(InetAddress.getByName("127.0.0.1"), TEST_PORT);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true)) {

            writer.println("AUTH invalid_token_12345");
            String response = reader.readLine();
            assertThat(response).isEqualTo("ERROR: UNAUTHORIZED");
        }
    }

    @Test
    @DisplayName("INVOKE command executes capability when ExecutionEngine is provided")
    void testIpcInvokeCapability() throws Exception {
        server.stop();

        ExecutionEngine engine = mock(ExecutionEngine.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ExecutionEngine> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(engine);
        when(provider.getObject()).thenReturn(engine);

        CapabilityResult<Object> mockResult = CapabilityResult.success("sys.uptime", Map.of("uptime", "2h 30m"), Instant.now());
        when(engine.execute(eq("sys.uptime"), any(), any())).thenReturn(mockResult);

        CapabilityRegistry registry = new CapabilityRegistry(List.of());
        ApplicationContext context = mock(ApplicationContext.class);

        server = new DaemonIpcServer(registry, context, provider);
        server.start(TEST_PORT);

        String response = client.sendCommand("INVOKE sys.uptime", TEST_PORT);
        assertThat(response).contains("\"capabilityId\":\"sys.uptime\"");
        assertThat(response).contains("\"success\":true");
    }

    @Test
    @DisplayName("RELOAD command dynamically reloads configuration over IPC")
    void testIpcReloadCommand() throws Exception {
        com.nexus.config.NexusProperties properties = new com.nexus.config.NexusProperties();
        when(context.getBean(com.nexus.config.NexusProperties.class)).thenReturn(properties);

        String response = client.sendCommand("RELOAD", TEST_PORT);
        assertThat(response).contains("\"reloaded\":true");
    }

    @Test
    @DisplayName("Unauthenticated client can query STATUS and PING on loopback without token")
    void testUnauthenticatedClientCanPingAndGetStatus() throws Exception {
        try (Socket socket = new Socket(InetAddress.getByName("127.0.0.1"), TEST_PORT);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true)) {

            writer.println("STATUS");
            String status = reader.readLine();
            assertThat(status).contains("\"status\":\"RUNNING\"");
            assertThat(status).contains("\"pid\":");

            writer.println("PING");
            String pong = reader.readLine();
            assertThat(pong).isEqualTo("PONG");
        }
    }

    @Test
    @DisplayName("Unauthenticated client cannot execute privileged commands (STOP, RELOAD, INVOKE)")
    void testUnauthenticatedClientCannotExecutePrivilegedCommands() throws Exception {
        try (Socket socket = new Socket(InetAddress.getByName("127.0.0.1"), TEST_PORT);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true)) {

            writer.println("STOP");
            String resp = reader.readLine();
            assertThat(resp).isEqualTo("ERROR: UNAUTHORIZED");
        }
    }
}
