package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.mapper.JobMapper;
import com.autoapplicant.adapter.persistence.repository.JobJpaRepository;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class JobPersistenceAdapter implements JobRepositoryPort {

    private final JobJpaRepository repo;

    public JobPersistenceAdapter(JobJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public Job save(Job job) {
        return JobMapper.toDomain(repo.save(JobMapper.toEntity(job)));
    }

    @Override
    public List<Job> saveAll(List<Job> jobs) {
        return repo.saveAll(jobs.stream().map(JobMapper::toEntity).collect(Collectors.toList()))
                .stream().map(JobMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public Optional<Job> findById(UUID id) {
        return repo.findById(id).map(JobMapper::toDomain);
    }

    @Override
    public List<Job> findAll(int page, int size) {
        return repo.findAll(PageRequest.of(page, size)).stream()
                .map(JobMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Job> findAllExcluding(Set<UUID> excludedIds, int page, int size) {
        if (excludedIds == null || excludedIds.isEmpty()) {
            return findAll(page, size);
        }
        return repo.findAllExcluding(excludedIds, PageRequest.of(page, size))
                .stream().map(JobMapper::toDomain).toList();
    }

    @Override
    public Optional<Job> findBySourceAndSourceJobId(JobSource source, String sourceJobId) {
        return repo.findBySourceAndSourceJobId(source.name(), sourceJobId).map(JobMapper::toDomain);
    }

    @Override
    public boolean existsBySourceAndSourceJobId(JobSource source, String sourceJobId) {
        return repo.existsBySourceAndSourceJobId(source.name(), sourceJobId);
    }

    @Override
    public Optional<Job> findByUrl(String url) {
        return repo.findByUrl(url).map(JobMapper::toDomain);
    }

    @Override
    public List<Job> findUnenriched(int limit) {
        return repo.findUnenriched(org.springframework.data.domain.PageRequest.of(0, limit))
                .stream().map(JobMapper::toDomain).collect(java.util.stream.Collectors.toList());
    }

    @Override
    public long count() {
        return repo.count();
    }
}
