package com.autoapplicant.adapter.web.dto.ai;

import com.autoapplicant.domain.ai.AiCredential;

import java.time.Instant;

/**
 * What the settings screen is allowed to know: whether a key is stored, which
 * provider it is for, and enough of it to recognise. Never the key.
 */
public record AiCredentialStatusResponse(
        boolean configured,
        String provider,
        String hint,
        String model,
        Instant updatedAt,
        /** False when the deployment has no encryption secret and so cannot store keys. */
        boolean storageAvailable
) {
    public static AiCredentialStatusResponse none(boolean storageAvailable) {
        return new AiCredentialStatusResponse(false, null, null, null, null, storageAvailable);
    }

    public static AiCredentialStatusResponse of(AiCredential credential) {
        return new AiCredentialStatusResponse(true,
                credential.provider() != null ? credential.provider().name() : null,
                credential.hint(), credential.model(), credential.updatedAt(), true);
    }
}
