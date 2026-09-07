package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.mapper.DocumentMapper;
import com.autoapplicant.adapter.persistence.repository.WritingProfileJpaRepository;
import com.autoapplicant.domain.document.WritingProfile;
import com.autoapplicant.port.out.document.WritingProfileRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class WritingProfilePersistenceAdapter implements WritingProfileRepositoryPort {

    private final WritingProfileJpaRepository repo;

    public WritingProfilePersistenceAdapter(WritingProfileJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public WritingProfile save(WritingProfile profile) {
        return DocumentMapper.toDomain(repo.save(DocumentMapper.toEntity(profile)));
    }

    @Override
    public Optional<WritingProfile> findByUserId(UUID userId) {
        return repo.findByUserId(userId).map(DocumentMapper::toDomain);
    }
}
