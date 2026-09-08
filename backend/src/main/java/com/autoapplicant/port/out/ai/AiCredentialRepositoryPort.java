package com.autoapplicant.port.out.ai;

import com.autoapplicant.domain.ai.AiCredential;

import java.util.Optional;
import java.util.UUID;

public interface AiCredentialRepositoryPort {
    AiCredential save(AiCredential credential);
    Optional<AiCredential> findByUserId(UUID userId);
    void deleteByUserId(UUID userId);
}
