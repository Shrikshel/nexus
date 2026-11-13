package com.nexus.core.capability;

import com.nexus.core.security.Permission;

import java.util.Set;

/**
 * Fundamental contract for all pluggable capabilities in Nexus.
 *
 * @param <I> Input parameter type (use Void for parameterless capabilities)
 * @param <O> Strongly typed output payload type
 */
public interface Capability<I, O> {

    /**
     * Unique identifier for the capability (e.g., "sys.info", "sys.uptime", "disk.smart").
     */
    String getId();

    /**
     * Human-readable display title.
     */
    String getDisplayName();

    /**
     * Clear description of the functionality provided by this capability.
     */
    String getDescription();

    /**
     * Categorization of the capability.
     */
    CapabilityCategory getCategory();

    /**
     * Permissions required by the caller to invoke this capability.
     */
    Set<Permission> getRequiredPermissions();

    /**
     * Indicates whether execution requires elevated OS privilege (e.g. root or administrator).
     */
    default boolean isPrivileged() {
        return false;
    }

    /**
     * Class of the input type.
     */
    Class<I> getInputType();

    /**
     * Class of the output type.
     */
    Class<O> getOutputType();

    /**
     * Executes the capability within the provided execution context.
     * Implementations must handle interrupts and check cancellation tokens where applicable.
     */
    CapabilityResult<O> execute(ExecutionContext context, I input);
}
