package com.autoapplicant.adapter.security;

import com.autoapplicant.port.out.ai.CurrentUserPort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/** Reads the authenticated user off the Spring Security context, or nobody. */
@Component
public class SecurityContextCurrentUser implements CurrentUserPort {

    @Override
    public Optional<UUID> currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return Optional.empty();
        Object principal = auth.getPrincipal();
        try {
            if (principal instanceof UUID uuid) return Optional.of(uuid);
            if (principal instanceof String str) return Optional.of(UUID.fromString(str));
        } catch (IllegalArgumentException ignored) {
            // An anonymous principal ("anonymousUser") is not a user id.
        }
        return Optional.empty();
    }
}
