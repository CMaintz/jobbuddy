package com.autoapplicant.port.in.user;

import java.util.UUID;

/**
 * GDPR Article 17 — Right to Erasure.
 * Deletes all data for the authenticated user from the database and from Firebase.
 */
public interface DeleteUserAccountUseCase {
    void deleteAccount(UUID userId);
}
