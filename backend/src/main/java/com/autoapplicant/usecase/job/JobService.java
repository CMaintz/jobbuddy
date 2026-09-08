package com.autoapplicant.usecase.job;

import com.autoapplicant.domain.job.IgnoredJob;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.search.JobSearchQuery;
import com.autoapplicant.port.in.job.*;
import com.autoapplicant.port.in.job.CreateManualJobUseCase;
import com.autoapplicant.port.out.job.IgnoredJobRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.port.out.job.SavedJobRepositoryPort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class JobService implements GetJobsUseCase, GetJobByIdUseCase, SaveJobUseCase,
        IgnoreJobUseCase, GetSavedJobsUseCase, GetIgnoredJobsUseCase, CreateManualJobUseCase {

    private final JobRepositoryPort jobRepo;
    private final SavedJobRepositoryPort savedJobRepo;
    private final IgnoredJobRepositoryPort ignoredJobRepo;

    public JobService(JobRepositoryPort jobRepo, SavedJobRepositoryPort savedJobRepo,
                      IgnoredJobRepositoryPort ignoredJobRepo) {
        this.jobRepo = jobRepo;
        this.savedJobRepo = savedJobRepo;
        this.ignoredJobRepo = ignoredJobRepo;
    }

    @Override
    public Page<Job> getJobs(JobSearchQuery query) {
        if (query.userId() != null) {
            Set<UUID> ignored = ignoredJobRepo.findJobIdsByUserId(query.userId());
            List<Job> jobs = jobRepo.findAllExcluding(ignored, query.page(), query.size());
            long total = Math.max(jobRepo.countActive() - ignored.size(), 0);
            return new PageImpl<>(jobs, PageRequest.of(query.page(), query.size()), total);
        }
        List<Job> jobs = jobRepo.findActive(query.page(), query.size());
        long total = jobRepo.countActive();
        return new PageImpl<>(jobs, PageRequest.of(query.page(), query.size()), total);
    }

    @Override
    public Optional<Job> getJobById(UUID id) {
        return jobRepo.findById(id);
    }

    @Override
    public Optional<Job> lookupByUrl(String url) {
        return jobRepo.findByUrl(url);
    }

    @Override
    public Job createManualJob(Job job) {
        return jobRepo.save(job);
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
        if (!ignoredJobRepo.isIgnored(userId, jobId)) {
            ignoredJobRepo.save(new IgnoredJob(null, userId, jobId, reason, Instant.now()));
        }
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

    @Override
    public List<IgnoredJob> getIgnoredJobs(UUID userId) {
        return ignoredJobRepo.findByUserIdOrderByIgnoredAtDesc(userId);
    }

    @Override
    public Set<UUID> getIgnoredJobIds(UUID userId) {
        return ignoredJobRepo.findJobIdsByUserId(userId);
    }

    @Override
    public void unignoreJob(UUID userId, UUID jobId) {
        ignoredJobRepo.deleteByUserIdAndJobId(userId, jobId);
    }
}
