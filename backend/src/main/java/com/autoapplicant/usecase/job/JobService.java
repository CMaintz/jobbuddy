package com.autoapplicant.usecase.job;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.search.JobSearchQuery;
import com.autoapplicant.port.in.job.*;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class JobService implements GetJobsUseCase, GetJobByIdUseCase, SaveJobUseCase, IgnoreJobUseCase {

    private final JobRepositoryPort jobRepo;

    public JobService(JobRepositoryPort jobRepo) {
        this.jobRepo = jobRepo;
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
        // Save interaction tracked via SavedJob table (simplified: just log for now)
        // Full implementation would persist to saved_jobs table
    }

    @Override
    public void ignoreJob(UUID userId, UUID jobId, String reason) {
        // Full implementation would persist to ignored_jobs table
    }
}
