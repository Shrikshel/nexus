package com.nexus.capabilities.network.tailscale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.capabilities.network.tailscale.dto.TailscaleStatusReport;
import com.nexus.core.capability.*;
import com.nexus.core.security.Permission;
import com.nexus.platform.TailscaleLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Capability providing Tailscale VPN state, IP addresses, node name, and peer counts.
 */
@Component
public class TailscaleStatusCapability implements Capability<Void, TailscaleStatusReport> {

    public static final String ID = "net.tailscale.status";
    private static final Logger log = LoggerFactory.getLogger(TailscaleStatusCapability.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "Tailscale VPN Status";
    }

    @Override
    public String getDescription() {
        return "Inspects Tailscale VPN connectivity, assigned 100.x IPs, MagicDNS name, and active peers";
    }

    @Override
    public CapabilityCategory getCategory() {
        return CapabilityCategory.NETWORK;
    }

    @Override
    public Set<Permission> getRequiredPermissions() {
        return Set.of(Permission.READ_NETWORK);
    }

    @Override
    public Class<Void> getInputType() {
        return Void.class;
    }

    @Override
    public Class<TailscaleStatusReport> getOutputType() {
        return TailscaleStatusReport.class;
    }

    @Override
    public CapabilityResult<TailscaleStatusReport> execute(ExecutionContext context, Void input) {
        Instant start = Instant.now();

        if (!TailscaleLocator.isTailscaleInstalled()) {
            return CapabilityResult.success(ID, TailscaleStatusReport.notInstalled(), start);
        }

        String binary = TailscaleLocator.findTailscaleBinary();
        try {
            Process process = new ProcessBuilder(binary, "status", "--json").start();
            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            try (BufferedReader outReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = outReader.readLine()) != null) {
                    stdout.append(line).append("\n");
                }
                while ((line = errReader.readLine()) != null) {
                    stderr.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return CapabilityResult.failure(ID, ErrorCode.TIMEOUT, "Tailscale status query timed out", start);
            }

            String output = stdout.toString().trim();
            if (process.exitValue() != 0 || output.isBlank() || !output.startsWith("{")) {
                String errorMsg = stderr.toString().trim();
                if (errorMsg.isBlank()) {
                    errorMsg = output;
                }
                return CapabilityResult.success(ID, TailscaleStatusReport.stopped(
                        errorMsg.isBlank() ? "Tailscale daemon is stopped or unreachable" : errorMsg
                ), start);
            }

            TailscaleStatusReport report = parseStatusJson(output);
            return CapabilityResult.success(ID, report, start);
        } catch (Exception e) {
            log.error("Failed to query tailscale status: {}", e.getMessage());
            return CapabilityResult.failure(ID, ErrorCode.INTERNAL_ERROR, "Failed to query Tailscale: " + e.getMessage(), start);
        }
    }

    public TailscaleStatusReport parseStatusJson(String output) throws Exception {
        if (output == null || output.isBlank() || !output.trim().startsWith("{")) {
            return TailscaleStatusReport.stopped("Tailscale daemon is stopped or unreachable");
        }

        JsonNode root = objectMapper.readTree(output);
        String backendState = root.path("BackendState").asText("Unknown");
        boolean running = "Running".equalsIgnoreCase(backendState);

        List<String> ips = new ArrayList<>();
        JsonNode ipsNode = root.path("TailscaleIPs");
        if (ipsNode.isArray()) {
            for (JsonNode ip : ipsNode) {
                ips.add(ip.asText());
            }
        }

        JsonNode self = root.path("Self");
        String hostname = self.path("HostName").asText("N/A");
        String dnsName = self.path("DNSName").asText("N/A");
        if (dnsName.endsWith(".")) {
            dnsName = dnsName.substring(0, dnsName.length() - 1);
        }
        String magicDns = root.path("MagicDNSSuffix").asText("N/A");

        int totalPeers = 0;
        int onlinePeers = 0;
        JsonNode peersNode = root.path("Peer");
        if (peersNode.isObject()) {
            totalPeers = peersNode.size();
            var fields = peersNode.fields();
            while (fields.hasNext()) {
                JsonNode peerNode = fields.next().getValue();
                if (peerNode.path("Online").asBoolean(false)) {
                    onlinePeers++;
                }
            }
        }

        String exitNode = "None";
        if (self.path("ExitNode").asBoolean(false)) {
            exitNode = "Active (This node)";
        }

        return new TailscaleStatusReport(
                true,
                running,
                backendState,
                ips,
                hostname,
                dnsName,
                magicDns,
                totalPeers,
                onlinePeers,
                exitNode,
                running ? "Tailscale connected and operational" : "Tailscale backend state: " + backendState,
                Instant.now()
        );
    }
}
