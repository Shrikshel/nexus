package com.nexus.capabilities.network;

import com.nexus.capabilities.network.dto.NetworkInterfaceInfo;
import com.nexus.capabilities.network.dto.NetworkReport;
import com.nexus.core.capability.*;
import com.nexus.core.security.Permission;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.time.Instant;
import java.util.*;

/**
 * Capability providing network interface telemetry and IP address assignments.
 */
@Component
public class NetworkInfoCapability implements Capability<Void, NetworkReport> {

    public static final String ID = "net.info";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "Network Interfaces";
    }

    @Override
    public String getDescription() {
        return "Returns network interfaces, MAC addresses, MTU, IPv4/IPv6 addresses, and status";
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
    public Class<NetworkReport> getOutputType() {
        return NetworkReport.class;
    }

    @Override
    public CapabilityResult<NetworkReport> execute(ExecutionContext context, Void input) {
        Instant start = Instant.now();
        try {
            List<NetworkInterfaceInfo> list = new ArrayList<>();
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    NetworkInterface nif = interfaces.nextElement();
                    String name = nif.getName();
                    String displayName = nif.getDisplayName();
                    int mtu = nif.getMTU();
                    boolean up = nif.isUp();
                    boolean loopback = nif.isLoopback();

                    String mac = formatMac(nif.getHardwareAddress());

                    List<String> ips = new ArrayList<>();
                    Enumeration<InetAddress> addrs = nif.getInetAddresses();
                    while (addrs.hasMoreElements()) {
                        ips.add(addrs.nextElement().getHostAddress());
                    }

                    list.add(new NetworkInterfaceInfo(name, displayName, mac, mtu, up, loopback, ips));
                }
            }

            NetworkReport report = new NetworkReport(list, list.size(), Instant.now());
            return CapabilityResult.success(ID, report, start);
        } catch (Exception e) {
            return CapabilityResult.failure(ID, ErrorCode.INTERNAL_ERROR, "Failed to read network interfaces: " + e.getMessage(), start);
        }
    }

    private String formatMac(byte[] mac) {
        if (mac == null || mac.length == 0) {
            return "N/A";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mac.length; i++) {
            sb.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? ":" : ""));
        }
        return sb.toString();
    }
}
