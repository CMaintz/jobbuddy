package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.mapper.ProfileSectionMapper;
import com.autoapplicant.adapter.persistence.repository.CertificationJpaRepository;
import com.autoapplicant.domain.user.Certification;
import com.autoapplicant.port.out.user.CertificationRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class CertificationPersistenceAdapter implements CertificationRepositoryPort {

    private final CertificationJpaRepository repo;

    public CertificationPersistenceAdapter(CertificationJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public Certification save(Certification cert) {
        return ProfileSectionMapper.toDomain(repo.save(ProfileSectionMapper.toEntity(cert)));
    }

    @Override
    public List<Certification> findByUserId(UUID userId) {
        return repo.findByUserIdOrderByIssuedAtDesc(userId)
                .stream().map(ProfileSectionMapper::toDomain).toList();
    }

    @Override
    public Optional<Certification> findById(UUID id) {
        return repo.findById(id).map(ProfileSectionMapper::toDomain);
    }

    @Override
    @Transactional
    public void deleteByIdAndUserId(UUID id, UUID userId) {
        repo.deleteByIdAndUserId(id, userId);
    }
}
