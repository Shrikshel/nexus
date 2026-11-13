package com.nexus.capabilities.network.tailscale;

import com.nexus.capabilities.network.tailscale.dto.TailscaleOperationRequest;
import com.nexus.capabilities.network.tailscale.dto.TailscaleOperationResult;
import com.nexus.core.capability.*;
import com.nexus.core.security.Permission;
import com.nexus.core.security.privilege.PrivilegeRunner;
import com.nexus.platform.TailscaleLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Privileged capability to bring the Tailscale VPN up and connect to the tailnet.
 */
@Component
public class TailscaleUpCapability implements Capability<TailscaleOperationRequest, TailscaleOperationResult> {

    public static final String ID = "net.tailscale.up";
    private static final Logger log = LoggerFactory.getLogger(TailscaleUpCapability.class);

    private final PrivilegeRunner privilegeRunner;

    public TailscaleUpCapability(PrivilegeRunner privilegeRunner) {
        this.privilegeRunner = privilegeRunner;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "Tailscale VPN Up";
    }

    @Override
    public String getDescription() {
        return "Brings the Tailscale VPN connection up with optional routes and SSH flags (requires elevated privilege)";
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
    public Class<TailscaleOperationRequest> getInputType() {
        return TailscaleOperationRequest.class;
    }

    @Override
    public Class<TailscaleOperationResult> getOutputType() {
        return TailscaleOperationResult.class;
    }

    @Override
    public CapabilityResult<TailscaleOperationResult> execute(ExecutionContext context, TailscaleOperationRequest input) {
        Instant start = Instant.now();

        if (!TailscaleLocator.isTailscaleInstalled()) {
            return CapabilityResult.failure(ID, ErrorCode.PLATFORM_UNSUPPORTED, "Tailscale CLI is not installed on this host", start);
        }

        String binary = TailscaleLocator.findTailscaleBinary();
        List<String> args = new ArrayList<>();
        args.add("up");

        if (input != null) {
            if (input.acceptRoutes()) {
                args.add("--accept-routes");
            }
            if (input.ssh()) {
                args.add("--ssh");
            }
            if (input.exitNode() != null && !input.exitNode().isBlank()) {
                args.add("--exit-node=" + input.exitNode().trim());
            }
            if (input.authKey() != null && !input.authKey().isBlank()) {
                args.add("--auth-key=" + input.authKey().trim());
            }
            if (input.hostname() != null && !input.hostname().isBlank()) {
                args.add("--hostname=" + input.hostname().trim());
            }
            if (input.reset()) {
                args.add("--reset");
            }
        }

        log.info("Executing privileged tailscale up via PrivilegeRunner...");
        PrivilegeRunner.ExecutionOutput output = privilegeRunner.executeWhitelisted(binary, args, 30);

        boolean success = output.exitCode() == 0;
        String msg = output.stdout().trim();
        if (msg.isBlank()) {
            msg = success ? "Tailscale connected successfully" : "Tailscale up returned exit code " + output.exitCode();
        }

        TailscaleOperationResult result = new TailscaleOperationResult(
                success,
                "UP",
                msg,
                output.exitCode(),
                Instant.now()
        );

        if (success) {
            return CapabilityResult.success(ID, result, start);
        } else {
            return CapabilityResult.failure(ID, ErrorCode.INTERNAL_ERROR, "Failed to bring Tailscale up: " + msg, start);
        }
    }
}
