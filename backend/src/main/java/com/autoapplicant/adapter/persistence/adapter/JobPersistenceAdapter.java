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
    public Optional<Job> findBySourceAndSourceJobId(JobSource source, String sourceJobId) {
        return repo.findBySourceAndSourceJobId(source.name(), sourceJobId).map(JobMapper::toDomain);
    }

    @Override
    public List<Job> findByUserId(UUID userId) {
        return List.of();
    }

    @Override
    public long count() {
        return repo.count();
    }
}
