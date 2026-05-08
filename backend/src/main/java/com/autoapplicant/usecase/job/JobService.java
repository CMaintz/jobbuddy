package com.autoapplicant.usecase.job;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.search.JobSearchQuery;
import com.autoapplicant.port.in.job.*;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.port.out.job.SavedJobRepositoryPort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class JobService implements GetJobsUseCase, GetJobByIdUseCase, SaveJobUseCase, IgnoreJobUseCase, GetSavedJobsUseCase {

    private final JobRepositoryPort jobRepo;
    private final SavedJobRepositoryPort savedJobRepo;

    public JobService(JobRepositoryPort jobRepo, SavedJobRepositoryPort savedJobRepo) {
        this.jobRepo = jobRepo;
        this.savedJobRepo = savedJobRepo;
    }

    @Override
    public Page<Job> getJobs(JobSearchQuery query) {
        List<Job> jobs = jobRepo.findAll(query.page(), query.size());
        long total = jobRepo.count();
        return new PageImpl<>(jobs, PageRequest.of(query.page(), query.size()), total);
    }

    @Override
    public Optional<Job> getJobById(UUID id) {
        return jobRepo.findById(id);
    }

    @Override
    public void saveJob(UUID userId, UUID jobId) {
        savedJobRepo.save(userId, jobId);
    }

    @Override
    public void unsaveJob(UUID userId, UUID jobId) {
        savedJobRepo.unsave(userId, jobId);
    }

    @Override
    public void ignoreJob(UUID userId, UUID jobId, String reason) {
        savedJobRepo.unsave(userId, jobId);
    }

    @Override
    public List<Job> getSavedJobs(UUID userId) {
        List<UUID> jobIds = savedJobRepo.findJobIdsByUserId(userId);
        return jobIds.stream()
                .map(jobRepo::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }
}
