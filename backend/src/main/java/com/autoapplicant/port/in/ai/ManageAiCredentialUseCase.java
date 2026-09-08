package com.autoapplicant.port.in.ai;

import com.autoapplicant.domain.ai.AiCredential;
import com.autoapplicant.domain.ai.AiCredentialProvider;

import java.util.Optional;
import java.util.UUID;

public interface ManageAiCredentialUseCase {

    /** Stores (or replaces) the user's own key. The key is encrypted before it lands. */
    AiCredential setCredential(UUID userId, AiCredentialProvider provider, String apiKey, String model);

    void clearCredential(UUID userId);

    /** The stored credential, decrypted — for the generation path, never for a response body. */
    Optional<AiCredential> findCredential(UUID userId);
}
