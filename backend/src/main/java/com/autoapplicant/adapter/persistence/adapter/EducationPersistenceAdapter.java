package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.mapper.ProfileSectionMapper;
import com.autoapplicant.adapter.persistence.repository.EducationJpaRepository;
import com.autoapplicant.domain.user.Education;
import com.autoapplicant.port.out.user.EducationRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class EducationPersistenceAdapter implements EducationRepositoryPort {

    private final EducationJpaRepository repo;

    public EducationPersistenceAdapter(EducationJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public Education save(Education education) {
        return ProfileSectionMapper.toDomain(repo.save(ProfileSectionMapper.toEntity(education)));
    }

    @Override
    public List<Education> findByUserId(UUID userId) {
        return repo.findByUserIdOrderByDisplayOrderAsc(userId)
                .stream().map(ProfileSectionMapper::toDomain).toList();
    }

    @Override
    public Optional<Education> findById(UUID id) {
        return repo.findById(id).map(ProfileSectionMapper::toDomain);
    }

    @Override
    @Transactional
    public void deleteByIdAndUserId(UUID id, UUID userId) {
        repo.deleteByIdAndUserId(id, userId);
    }
}
