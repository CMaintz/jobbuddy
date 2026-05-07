package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.mapper.ProfileSectionMapper;
import com.autoapplicant.adapter.persistence.repository.ProjectJpaRepository;
import com.autoapplicant.domain.user.Project;
import com.autoapplicant.port.out.user.ProjectRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ProjectPersistenceAdapter implements ProjectRepositoryPort {

    private final ProjectJpaRepository repo;

    public ProjectPersistenceAdapter(ProjectJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public Project save(Project project) {
        return ProfileSectionMapper.toDomain(repo.save(ProfileSectionMapper.toEntity(project)));
    }

    @Override
    public List<Project> findByUserId(UUID userId) {
        return repo.findByUserIdOrderByDisplayOrderAsc(userId)
                .stream().map(ProfileSectionMapper::toDomain).toList();
    }

    @Override
    public Optional<Project> findById(UUID id) {
        return repo.findById(id).map(ProfileSectionMapper::toDomain);
    }

    @Override
    @Transactional
    public void deleteByIdAndUserId(UUID id, UUID userId) {
        repo.deleteByIdAndUserId(id, userId);
    }
}
