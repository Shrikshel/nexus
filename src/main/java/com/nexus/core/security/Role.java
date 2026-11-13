package com.nexus.core.security;

import java.util.Set;

/**
 * Standard RBAC roles bundling permissions.
 */
public enum Role {
    VIEWER(Set.of(
        Permission.READ_SYSTEM,
        Permission.READ_RESOURCES,
        Permission.READ_STORAGE,
        Permission.READ_PROCESSES,
        Permission.READ_NETWORK,
        Permission.READ_SERVICES
    )),
    OPERATOR(Set.of(
        Permission.READ_SYSTEM,
        Permission.READ_RESOURCES,
        Permission.READ_STORAGE,
        Permission.READ_PROCESSES,
        Permission.READ_NETWORK,
        Permission.READ_SERVICES,
        Permission.WRITE_PROCESSES,
        Permission.WRITE_SERVICES,
        Permission.WRITE_NETWORK,
        Permission.POWER_MANAGEMENT,
        Permission.EXECUTE_DIAGNOSTICS
    )),
    ADMIN(Set.of(
        Permission.ADMIN_ALL,
        Permission.READ_SYSTEM,
        Permission.READ_RESOURCES,
        Permission.READ_STORAGE,
        Permission.READ_PROCESSES,
        Permission.READ_NETWORK,
        Permission.READ_SERVICES,
        Permission.WRITE_PROCESSES,
        Permission.WRITE_SERVICES,
        Permission.WRITE_NETWORK,
        Permission.POWER_MANAGEMENT,
        Permission.EXECUTE_DIAGNOSTICS
    ));

    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) {
        this.permissions = permissions;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }
}
