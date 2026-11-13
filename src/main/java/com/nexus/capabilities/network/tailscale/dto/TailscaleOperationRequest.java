package com.nexus.capabilities.network.tailscale.dto;

/**
 * Options for configuring and bringing Tailscale VPN up or down.
 */
public record TailscaleOperationRequest(
    boolean acceptRoutes,
    boolean ssh,
    String exitNode,
    String authKey,
    String hostname,
    boolean reset
) {
    public static TailscaleOperationRequest defaults() {
        return new TailscaleOperationRequest(false, false, null, null, null, false);
    }
}
