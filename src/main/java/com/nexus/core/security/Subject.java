package com.nexus.core.security;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Caller identity carrying roles and effective permissions.
 */
public record Subject(
    String id,
    String username,
    Role role,
    Set<Permission> explicitPermissions
) {
    public static Subject anonymous() {
        return new Subject("anonymous", "anonymous", Role.VIEWER, Collections.emptySet());
    }

    public static Subject localCli() {
        return new Subject("cli-local", System.getProperty("user.name", "local-user"), Role.ADMIN, Set.of(Permission.ADMIN_ALL));
    }

    public static Subject daemonSystem() {
        return new Subject("daemon-internal", "nexus-system", Role.ADMIN, Set.of(Permission.ADMIN_ALL));
    }

    public static Subject of(String id, String username, Role role) {
        return new Subject(id, username, role, Collections.emptySet());
    }

    /**
     * Checks if the subject holds a specific permission directly, via its role, or via ADMIN_ALL.
     */
    public boolean hasPermission(Permission permission) {
        if (role == Role.ADMIN || (explicitPermissions != null && explicitPermissions.contains(Permission.ADMIN_ALL))) {
            return true;
        }
        if (role != null && role.getPermissions().contains(permission)) {
            return true;
        }
        return explicitPermissions != null && explicitPermissions.contains(permission);
    }

    public Set<Permission> getEffectivePermissions() {
        Set<Permission> effective = new HashSet<>();
        if (role != null) {
            effective.addAll(role.getPermissions());
        }
        if (explicitPermissions != null) {
            effective.addAll(explicitPermissions);
        }
        return Collections.unmodifiableSet(effective);
    }
}
