package com.autoapplicant.port.in.auth;

import com.autoapplicant.domain.user.User;
import com.autoapplicant.domain.user.UserRole;

import java.util.UUID;

public interface ProvisionFirebaseUserUseCase {
    /** Looks up or creates the local user record for a verified Firebase token. */
    User findOrCreate(String firebaseUid, String email, String name);

    /** Updates a user's role in the DB and syncs the Firebase custom claim. */
    User setRole(UUID userId, UserRole newRole);
}
