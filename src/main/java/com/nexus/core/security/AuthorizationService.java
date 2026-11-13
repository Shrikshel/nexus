package com.nexus.core.security;

import com.nexus.core.capability.Capability;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Evaluates authorization for capability execution against subject permissions.
 */
@Service
public class AuthorizationService {

    public boolean isAuthorized(Subject subject, Capability<?, ?> capability) {
        if (subject == null) {
            return false;
        }

        Set<Permission> required = capability.getRequiredPermissions();
        if (required == null || required.isEmpty()) {
            return true;
        }

        for (Permission permission : required) {
            if (!subject.hasPermission(permission)) {
                return false;
            }
        }
        return true;
    }
}
