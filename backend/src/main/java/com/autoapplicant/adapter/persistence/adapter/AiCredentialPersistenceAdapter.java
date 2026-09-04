package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.AiCredentialEntity;
import com.autoapplicant.adapter.persistence.repository.AiCredentialJpaRepository;
import com.autoapplicant.domain.ai.AiCredential;
import com.autoapplicant.domain.ai.AiCredentialProvider;
import com.autoapplicant.port.out.ai.AiCredentialRepositoryPort;
import com.autoapplicant.port.out.security.SecretCipherPort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * The key is encrypted on the way in and decrypted on the way out, so nothing above
 * this adapter — or in the database — ever holds it in the clear.
 */
@Component
public class AiCredentialPersistenceAdapter implements AiCredentialRepositoryPort {

    private final AiCredentialJpaRepository repo;
    private final SecretCipherPort cipher;

    public AiCredentialPersistenceAdapter(AiCredentialJpaRepository repo, SecretCipherPort cipher) {
        this.repo = repo;
        this.cipher = cipher;
    }

    @Override
    public AiCredential save(AiCredential credential) {
        AiCredentialEntity e = new AiCredentialEntity();
        e.setUserId(credential.userId());
        e.setProvider(credential.provider().name());
        e.setApiKeyEncrypted(cipher.encrypt(credential.apiKey()));
        e.setModel(credential.model());
        return toDomain(repo.save(e), credential.apiKey());
    }

    @Override
    public Optional<AiCredential> findByUserId(UUID userId) {
        return repo.findById(userId).map(e -> toDomain(e, cipher.decrypt(e.getApiKeyEncrypted())));
    }

    @Override
    public void deleteByUserId(UUID userId) {
        repo.deleteById(userId);
    }

    private static AiCredential toDomain(AiCredentialEntity e, String apiKey) {
        return new AiCredential(e.getUserId(), AiCredentialProvider.parse(e.getProvider()),
                apiKey, e.getModel(), e.getUpdatedAt());
    }
}
