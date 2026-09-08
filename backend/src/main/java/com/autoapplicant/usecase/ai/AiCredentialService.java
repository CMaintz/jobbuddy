package com.autoapplicant.usecase.ai;

import com.autoapplicant.domain.ai.AiCredential;
import com.autoapplicant.domain.ai.AiCredentialProvider;
import com.autoapplicant.port.in.ai.ManageAiCredentialUseCase;
import com.autoapplicant.port.out.ai.AiCredentialRepositoryPort;
import com.autoapplicant.port.out.security.SecretCipherPort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class AiCredentialService implements ManageAiCredentialUseCase {

    private final AiCredentialRepositoryPort repo;
    private final SecretCipherPort cipher;

    public AiCredentialService(AiCredentialRepositoryPort repo, SecretCipherPort cipher) {
        this.repo = repo;
        this.cipher = cipher;
    }

    @Override
    public AiCredential setCredential(UUID userId, AiCredentialProvider provider, String apiKey, String model) {
        if (provider == null) throw new IllegalArgumentException("Unknown AI provider");
        if (apiKey == null || apiKey.isBlank()) throw new IllegalArgumentException("API key is required");
        if (!cipher.isConfigured()) {
            // Refusing beats storing someone's API key in the clear because a
            // deployment forgot to set the encryption secret.
            throw new IllegalStateException(
                    "This deployment cannot store API keys: no secret-encryption key is configured.");
        }
        return repo.save(new AiCredential(userId, provider, apiKey.trim(), blankToNull(model), Instant.now()));
    }

    @Override
    public void clearCredential(UUID userId) {
        repo.deleteByUserId(userId);
    }

    @Override
    public Optional<AiCredential> findCredential(UUID userId) {
        if (!cipher.isConfigured()) return Optional.empty();
        return repo.findByUserId(userId);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
