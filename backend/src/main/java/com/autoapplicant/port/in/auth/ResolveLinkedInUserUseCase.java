package com.autoapplicant.port.in.auth;

import com.autoapplicant.domain.user.User;

public interface ResolveLinkedInUserUseCase {
    /**
     * Resolves (or creates) an internal user record for a LinkedIn OAuth login.
     * Tries to match by linkedinId first, then by email, and finally creates a new user.
     */
    User resolve(String linkedinSub, String email, String fullName);
}
