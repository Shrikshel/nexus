package com.nexus.capabilities.network.tailscale;

import com.nexus.capabilities.network.tailscale.dto.TailscaleOperationResult;
import com.nexus.core.capability.*;
import com.nexus.core.security.Permission;
import com.nexus.core.security.privilege.PrivilegeRunner;
import com.nexus.platform.TailscaleLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Privileged capability to disconnect and bring the Tailscale VPN down.
 */
@Component
public class TailscaleDownCapability implements Capability<Void, TailscaleOperationResult> {

    public static final String ID = "net.tailscale.down";
    private static final Logger log = LoggerFactory.getLogger(TailscaleDownCapability.class);

    private final PrivilegeRunner privilegeRunner;

    public TailscaleDownCapability(PrivilegeRunner privilegeRunner) {
        this.privilegeRunner = privilegeRunner;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "Tailscale VPN Down";
    }

    @Override
    public String getDescription() {
        return "Disconnects and tears down the Tailscale VPN connection (requires elevated privilege)";
    }

    @Override
    public CapabilityCategory getCategory() {
        return CapabilityCategory.NETWORK;
    }

    @Override
    public Set<Permission> getRequiredPermissions() {
        return Set.of(Permission.WRITE_NETWORK);
    }

    @Override
    public boolean isPrivileged() {
        return true;
    }

    @Override
    public Class<Void> getInputType() {
        return Void.class;
    }

    @Override
    public Class<TailscaleOperationResult> getOutputType() {
        return TailscaleOperationResult.class;
    }

    @Override
    public CapabilityResult<TailscaleOperationResult> execute(ExecutionContext context, Void input) {
        Instant start = Instant.now();

        if (!TailscaleLocator.isTailscaleInstalled()) {
            return CapabilityResult.failure(ID, ErrorCode.PLATFORM_UNSUPPORTED, "Tailscale CLI is not installed on this host", start);
        }

        String binary = TailscaleLocator.findTailscaleBinary();
        log.info("Executing privileged tailscale down via PrivilegeRunner...");
        PrivilegeRunner.ExecutionOutput output = privilegeRunner.executeWhitelisted(binary, List.of("down"), 20);

        boolean success = output.exitCode() == 0;
        String msg = output.stdout().trim();
        if (msg.isBlank()) {
            msg = success ? "Tailscale disconnected successfully" : "Tailscale down returned exit code " + output.exitCode();
        }

        TailscaleOperationResult result = new TailscaleOperationResult(
                success,
                "DOWN",
                msg,
                output.exitCode(),
                Instant.now()
        );

        if (success) {
            return CapabilityResult.success(ID, result, start);
        } else {
            return CapabilityResult.failure(ID, ErrorCode.INTERNAL_ERROR, "Failed to disconnect Tailscale: " + msg, start);
        }
    }
}
