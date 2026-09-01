package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.mapper.ApplicationMapper;
import com.autoapplicant.adapter.persistence.repository.ApplicationJpaRepository;
import com.autoapplicant.domain.application.Application;
import com.autoapplicant.port.out.application.ApplicationRepositoryPort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class ApplicationPersistenceAdapter implements ApplicationRepositoryPort {

    private final ApplicationJpaRepository repo;

    public ApplicationPersistenceAdapter(ApplicationJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public Application save(Application application) {
        return ApplicationMapper.toDomain(repo.save(ApplicationMapper.toEntity(application)));
    }

    @Override
    public Optional<Application> findById(UUID id) {
        return repo.findById(id).map(ApplicationMapper::toDomain);
    }

    @Override
    public List<Application> findByUserId(UUID userId) {
        return repo.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(ApplicationMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public Page<Application> findByUserId(UUID userId, Pageable pageable) {
        return repo.findByUserId(userId, pageable).map(ApplicationMapper::toDomain);
    }

    @Override
    public Optional<Application> findByIdAndUserId(UUID id, UUID userId) {
        return repo.findByIdAndUserId(id, userId).map(ApplicationMapper::toDomain);
    }

    @Override
    public boolean existsByUserIdAndJobId(UUID userId, UUID jobId) {
        return repo.existsByUserIdAndJobId(userId, jobId);
    }

    @Override
    public java.util.Set<UUID> findAppliedJobIds(UUID userId) {
        return new java.util.HashSet<>(repo.findAppliedJobIds(userId));
    }

    @Override
    public List<String> findRecentOutcomeLessons(UUID userId, int limit) {
        return repo.findRecentOutcomeLessons(userId, org.springframework.data.domain.PageRequest.of(0, limit));
    }
}
