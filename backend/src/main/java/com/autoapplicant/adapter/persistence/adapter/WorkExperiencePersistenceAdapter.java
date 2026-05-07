package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.mapper.ProfileSectionMapper;
import com.autoapplicant.adapter.persistence.repository.WorkExperienceJpaRepository;
import com.autoapplicant.domain.user.WorkExperience;
import com.autoapplicant.port.out.user.WorkExperienceRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class WorkExperiencePersistenceAdapter implements WorkExperienceRepositoryPort {

    private final WorkExperienceJpaRepository repo;

    public WorkExperiencePersistenceAdapter(WorkExperienceJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public WorkExperience save(WorkExperience exp) {
        return ProfileSectionMapper.toDomain(repo.save(ProfileSectionMapper.toEntity(exp)));
    }

    @Override
    public List<WorkExperience> findByUserId(UUID userId) {
        return repo.findByUserIdOrderByDisplayOrderAsc(userId)
                .stream().map(ProfileSectionMapper::toDomain).toList();
    }

    @Override
    public Optional<WorkExperience> findById(UUID id) {
        return repo.findById(id).map(ProfileSectionMapper::toDomain);
    }

    @Override
    @Transactional
    public void deleteByIdAndUserId(UUID id, UUID userId) {
        repo.deleteByIdAndUserId(id, userId);
    }
}
