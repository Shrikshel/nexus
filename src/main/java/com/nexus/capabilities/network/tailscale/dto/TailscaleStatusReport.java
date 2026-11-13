package com.nexus.capabilities.network.tailscale.dto;

import java.time.Instant;
import java.util.List;

/**
 * Structured report describing local Tailscale VPN state and network peers.
 */
public record TailscaleStatusReport(
    boolean installed,
    boolean running,
    String backendState,
    List<String> tailscaleIPs,
    String hostname,
    String dnsName,
    String magicDnsSuffix,
    int totalPeers,
    int onlinePeers,
    String exitNode,
    String message,
    Instant timestamp
) {
    public static TailscaleStatusReport notInstalled() {
        return new TailscaleStatusReport(
                false, false, "NotInstalled", List.of(),
                "N/A", "N/A", "N/A", 0, 0, "None",
                "Tailscale CLI binary not found on host system", Instant.now()
        );
    }

    public static TailscaleStatusReport stopped(String message) {
        return new TailscaleStatusReport(
                true, false, "Stopped", List.of(),
                "N/A", "N/A", "N/A", 0, 0, "None",
                message, Instant.now()
        );
    }

    public String primaryIp() {
        if (tailscaleIPs != null && !tailscaleIPs.isEmpty()) {
            return tailscaleIPs.get(0);
        }
        return "N/A";
    }
}
