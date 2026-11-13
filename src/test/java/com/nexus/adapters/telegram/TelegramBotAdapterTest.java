package com.nexus.adapters.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.config.NexusProperties;
import com.nexus.core.capability.*;
import com.nexus.capabilities.system.dto.SystemInfoReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TelegramBotAdapterTest {

    private NexusProperties properties;
    private TelegramClient client;
    private TelegramSecurityFilter securityFilter;
    private TelegramFormatter formatter;
    private ExecutionEngine executionEngine;
    private TelegramBotAdapter adapter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        properties = new NexusProperties();
        NexusProperties.TelegramConfig config = properties.getTelegram();
        config.setEnabled(true);
        config.setBotToken("mock-token-123");
        config.setAllowedChatId(999L);

        client = mock(TelegramClient.class);
        securityFilter = new TelegramSecurityFilter();
        formatter = new TelegramFormatter();
        executionEngine = mock(ExecutionEngine.class);
        objectMapper = new ObjectMapper();

        adapter = new TelegramBotAdapter(properties, client, securityFilter, formatter, executionEngine);
    }

    private ObjectNode createUpdateJson(long updateId, long chatId, long senderId, String text) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("update_id", updateId);
        ObjectNode message = root.putObject("message");
        message.putObject("chat").put("id", chatId);
        ObjectNode from = message.putObject("from");
        from.put("id", senderId);
        from.put("username", "test_admin");
        message.put("text", text);
        return root;
    }

    @Test
    @DisplayName("Drops and ignores updates from unauthorized chat IDs")
    void testUnauthorizedChatDropped() {
        ObjectNode unauthorizedUpdate = createUpdateJson(1, 888L, 100L, "/info");

        adapter.processUpdate(unauthorizedUpdate, properties.getTelegram());

        verify(executionEngine, never()).execute(anyString(), any(), any());
        verify(client, never()).sendMessage(anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("Processes authorized /info command and sends HTML reply")
    void testAuthorizedInfoCommand() {
        SystemInfoReport report = new SystemInfoReport(
                "srv-nexus", "Linux", "6.1.0", "x86_64", "6.1.0-generic",
                4, 16000000000L, 8000000000L, "Ubuntu", "17.0.2", "JVM", "Asia/Kolkata (IST, UTC+05:30)", Instant.now()
        );
        CapabilityResult<Object> result = CapabilityResult.success("sys.info", report, Instant.now());
        when(executionEngine.execute(eq("sys.info"), any(ExecutionContext.class), any())).thenReturn(result);
        when(client.sendMessage(anyString(), anyLong(), anyString())).thenReturn(true);

        ObjectNode authorizedUpdate = createUpdateJson(2, 999L, 100L, "/info");
        adapter.processUpdate(authorizedUpdate, properties.getTelegram());

        ArgumentCaptor<ExecutionContext> contextCaptor = ArgumentCaptor.forClass(ExecutionContext.class);
        verify(executionEngine, times(1)).execute(eq("sys.info"), contextCaptor.capture(), isNull());

        ExecutionContext capturedContext = contextCaptor.getValue();
        assertThat(capturedContext.source()).isEqualTo(InvocationSource.TELEGRAM);
        assertThat(capturedContext.caller().id()).isEqualTo("100");

        ArgumentCaptor<String> replyCaptor = ArgumentCaptor.forClass(String.class);
        verify(client, times(1)).sendMessage(eq("mock-token-123"), eq(999L), replyCaptor.capture());

        assertThat(replyCaptor.getValue()).contains("🖥️ <b>Nexus Host Information</b>");
        assertThat(replyCaptor.getValue()).contains("<code>srv-nexus</code>");
    }

    @Test
    @DisplayName("Responds to /help with command overview")
    void testHelpCommand() {
        when(client.sendMessage(anyString(), anyLong(), anyString())).thenReturn(true);

        ObjectNode helpUpdate = createUpdateJson(3, 999L, 100L, "/help");
        adapter.processUpdate(helpUpdate, properties.getTelegram());

        ArgumentCaptor<String> replyCaptor = ArgumentCaptor.forClass(String.class);
        verify(client, times(1)).sendMessage(eq("mock-token-123"), eq(999L), replyCaptor.capture());

        assertThat(replyCaptor.getValue()).contains("🛡️ <b>Nexus Host Management Agent</b>");
        assertThat(replyCaptor.getValue()).contains("/info");
        assertThat(replyCaptor.getValue()).contains("/uptime");
    }

    @Test
    @DisplayName("Routes /cpu command to sys.cpu capability")
    void testCpuCommandRouting() {
        CapabilityResult<Object> result = CapabilityResult.success("sys.cpu", "Mock CPU", Instant.now());
        when(executionEngine.execute(eq("sys.cpu"), any(), any())).thenReturn(result);
        when(client.sendMessage(anyString(), anyLong(), anyString())).thenReturn(true);

        ObjectNode cpuUpdate = createUpdateJson(4, 999L, 100L, "/cpu");
        adapter.processUpdate(cpuUpdate, properties.getTelegram());

        verify(executionEngine, times(1)).execute(eq("sys.cpu"), any(), isNull());
    }

    @Test
    @DisplayName("Replies with unknown command error when capability is not found")
    void testUnknownCommand() {
        CapabilityResult<Object> failure = CapabilityResult.failure("unknown.cap", ErrorCode.CAPABILITY_NOT_FOUND, "Not found", Instant.now());
        when(executionEngine.execute(eq("unknown.cap"), any(), any())).thenReturn(failure);
        when(client.sendMessage(anyString(), anyLong(), anyString())).thenReturn(true);

        ObjectNode unknownUpdate = createUpdateJson(5, 999L, 100L, "/unknown_cap");
        adapter.processUpdate(unknownUpdate, properties.getTelegram());

        ArgumentCaptor<String> replyCaptor = ArgumentCaptor.forClass(String.class);
        verify(client, times(1)).sendMessage(eq("mock-token-123"), eq(999L), replyCaptor.capture());
        assertThat(replyCaptor.getValue()).contains("Unknown command");
    }

    @Test
    @DisplayName("Routes /tailscale command to net.tailscale.status capability")
    void testTailscaleStatusRouting() {
        CapabilityResult<Object> result = CapabilityResult.success("net.tailscale.status", "Mock Tailscale", Instant.now());
        when(executionEngine.execute(eq("net.tailscale.status"), any(), any())).thenReturn(result);
        when(client.sendMessage(anyString(), anyLong(), anyString())).thenReturn(true);

        ObjectNode update = createUpdateJson(6, 999L, 100L, "/tailscale");
        adapter.processUpdate(update, properties.getTelegram());

        verify(executionEngine, times(1)).execute(eq("net.tailscale.status"), any(), isNull());
    }

    @Test
    @DisplayName("Reboot command without confirm prompts user for confirmation")
    void testRebootUnconfirmedPrompts() {
        when(client.sendMessage(anyString(), anyLong(), anyString())).thenReturn(true);

        ObjectNode update = createUpdateJson(7, 999L, 100L, "/reboot");
        adapter.processUpdate(update, properties.getTelegram());

        verify(executionEngine, never()).execute(eq("sys.reboot"), any(), any());
        ArgumentCaptor<String> replyCaptor = ArgumentCaptor.forClass(String.class);
        verify(client).sendMessage(eq("mock-token-123"), eq(999L), replyCaptor.capture());
        assertThat(replyCaptor.getValue()).contains("Reboot Confirmation Required");
        assertThat(replyCaptor.getValue()).contains("/reboot confirm");
    }

    @Test
    @DisplayName("Reboot command with confirm dispatches sys.reboot")
    void testRebootConfirmedExecutes() {
        CapabilityResult<Object> result = CapabilityResult.success("sys.reboot", "Reboot initiated", Instant.now());
        when(executionEngine.execute(eq("sys.reboot"), any(), any())).thenReturn(result);
        when(client.sendMessage(anyString(), anyLong(), anyString())).thenReturn(true);

        ObjectNode update = createUpdateJson(8, 999L, 100L, "/reboot confirm");
        adapter.processUpdate(update, properties.getTelegram());

        verify(executionEngine, times(1)).execute(eq("sys.reboot"), any(), any());
    }

    @Test
    @DisplayName("Poweroff command without confirm prompts user for confirmation")
    void testPoweroffUnconfirmedPrompts() {
        when(client.sendMessage(anyString(), anyLong(), anyString())).thenReturn(true);

        ObjectNode update = createUpdateJson(9, 999L, 100L, "/poweroff");
        adapter.processUpdate(update, properties.getTelegram());

        verify(executionEngine, never()).execute(eq("sys.poweroff"), any(), any());
        ArgumentCaptor<String> replyCaptor = ArgumentCaptor.forClass(String.class);
        verify(client).sendMessage(eq("mock-token-123"), eq(999L), replyCaptor.capture());
        assertThat(replyCaptor.getValue()).contains("Poweroff Confirmation Required");
        assertThat(replyCaptor.getValue()).contains("/poweroff confirm");
    }

    @Test
    @DisplayName("Poweroff command with confirm dispatches sys.poweroff")
    void testPoweroffConfirmedExecutes() {
        CapabilityResult<Object> result = CapabilityResult.success("sys.poweroff", "Poweroff initiated", Instant.now());
        when(executionEngine.execute(eq("sys.poweroff"), any(), any())).thenReturn(result);
        when(client.sendMessage(anyString(), anyLong(), anyString())).thenReturn(true);

        ObjectNode update = createUpdateJson(10, 999L, 100L, "/poweroff confirm");
        adapter.processUpdate(update, properties.getTelegram());

        verify(executionEngine, times(1)).execute(eq("sys.poweroff"), any(), any());
    }

    @Test
    @DisplayName("Routes /zfs command to zfs.pool.list capability")
    void testZfsRouting() {
        CapabilityResult<Object> result = CapabilityResult.success("zfs.pool.list", "Mock ZFS", Instant.now());
        when(executionEngine.execute(eq("zfs.pool.list"), any(), any())).thenReturn(result);
        when(client.sendMessage(anyString(), anyLong(), anyString())).thenReturn(true);

        ObjectNode update = createUpdateJson(12, 999L, 100L, "/zfs");
        adapter.processUpdate(update, properties.getTelegram());

        verify(executionEngine, times(1)).execute(eq("zfs.pool.list"), any(), isNull());
    }
}
