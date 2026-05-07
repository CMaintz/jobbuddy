package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.mapper.DocumentMapper;
import com.autoapplicant.adapter.persistence.repository.CvVersionJpaRepository;
import com.autoapplicant.domain.document.CvVersion;
import com.autoapplicant.port.out.document.CvVersionRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class CvVersionPersistenceAdapter implements CvVersionRepositoryPort {

    private final CvVersionJpaRepository repo;

    public CvVersionPersistenceAdapter(CvVersionJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public CvVersion save(CvVersion cvVersion) {
        return DocumentMapper.toDomain(repo.save(DocumentMapper.toEntity(cvVersion)));
    }

    @Override
    public Optional<CvVersion> findById(UUID id) {
        return repo.findById(id).map(DocumentMapper::toDomain);
    }

    @Override
    public List<CvVersion> findByUserId(UUID userId) {
        return repo.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(DocumentMapper::toDomain).collect(Collectors.toList());
    }
}
