package com.autoapplicant.port.in.user;

import java.util.Map;
import java.util.UUID;

/**
 * GDPR Article 20 — Right to Data Portability.
 * Exports all personal data for a user in a machine-readable format.
 */
public interface ExportUserDataUseCase {
    Map<String, Object> exportUserData(UUID userId);
}
