package com.nexus.capabilities.network.dto;

import java.util.List;

/**
 * Details of a network interface card (NIC).
 */
public record NetworkInterfaceInfo(
    String name,
    String displayName,
    String macAddress,
    int mtu,
    boolean up,
    boolean loopback,
    List<String> ipAddresses
) {}
