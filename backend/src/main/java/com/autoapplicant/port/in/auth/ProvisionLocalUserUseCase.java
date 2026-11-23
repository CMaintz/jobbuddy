package com.autoapplicant.port.in.auth;

import com.autoapplicant.domain.user.User;

/** The desktop profile's single user, who exists without any sign-in provider. */
public interface ProvisionLocalUserUseCase {

    /**
     * Returns the local user, creating it with a default profile the first time. Idempotent:
     * every call, on every start, yields the same user.
     */
    User provisionLocalUser();
}
